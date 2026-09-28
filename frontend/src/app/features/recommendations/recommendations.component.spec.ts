import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { RecommendationApiService } from '../../core/recommendation-api.service';
import { RecommendationResponse } from '../../core/recommendation.models';
import { WatchlistApiService } from '../../core/watchlist-api.service';
import { RecommendationShareApiService } from '../../core/recommendation-share-api.service';
import { RecommendationsComponent } from './recommendations.component';

const response = (items: RecommendationResponse['items']): RecommendationResponse => ({
  items,
  strategy: items[0]?.strategy ?? 'POPULARITY',
  page: 0,
  size: 20,
  totalItems: items.length,
  totalPages: items.length ? 1 : 0,
});

const item = {
  movie: {
    id: 'movie-1', title: 'Arrival', overview: 'First contact', releaseYear: 2016,
    releaseDate: '2016-11-11', posterUrl: null, genres: [{ id: 'genre-1', name: 'Science Fiction' }],
    averageRating: 4.4, ratingCount: 100,
  },
  recommendationScore: 0.91,
  signals: { collaborative: 0.8, content: 0.9, popularity: 0.7 },
  strategy: 'HYBRID' as const,
  reason: { type: 'GENRE_MATCH' as const, text: 'Because you enjoy Science Fiction' },
};

describe('RecommendationsComponent', () => {
  let fixture: ComponentFixture<RecommendationsComponent>;
  const api = { list: vi.fn() };
  const watchlist = { add: vi.fn() };
  const shareApi = { create: vi.fn() };
  const router = { navigate: vi.fn() };
  const queryParamMap = new BehaviorSubject(convertToParamMap({ genre: 'Science Fiction', sort: 'rating', page: '0', size: '20' }));

  beforeEach(async () => {
    api.list.mockReset();
    watchlist.add.mockReset();
    shareApi.create.mockReset();
    router.navigate.mockReset();
    queryParamMap.next(convertToParamMap({ genre: 'Science Fiction', sort: 'rating', page: '0', size: '20' }));
    api.list.mockReturnValue(of(response([])));
    await TestBed.configureTestingModule({
      imports: [RecommendationsComponent],
      providers: [
        { provide: RecommendationApiService, useValue: api },
        { provide: WatchlistApiService, useValue: watchlist },
        { provide: RecommendationShareApiService, useValue: shareApi },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParamMap.asObservable() } },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();
  });

  function create(): void {
    fixture = TestBed.createComponent(RecommendationsComponent);
    fixture.detectChanges();
  }

  it('loads filters from the URL and renders server strategy and reason text', () => {
    api.list.mockReturnValue(of(response([item])));
    create();

    expect(api.list).toHaveBeenCalledWith(expect.objectContaining({ genre: 'Science Fiction', sort: 'rating', page: 0, size: 20 }));
    expect(fixture.nativeElement.textContent).toContain('Personalized');
    expect(fixture.nativeElement.textContent).toContain('Because you enjoy Science Fiction');
    expect(fixture.nativeElement.textContent).toContain('0.91');
    expect(fixture.nativeElement.textContent).toContain('First contact');
    expect(fixture.nativeElement.textContent).toContain('4.4');
    expect(fixture.nativeElement.querySelector('[data-testid="movie-details"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('collaborative');
  });

  it('associates every recommendation filter with a real label target', () => {
    create();

    expect(fixture.nativeElement.querySelector('label[for="recommendation-genre"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('label[for="recommendation-from-year"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#recommendation-genre')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#recommendation-from-year')).not.toBeNull();
  });

  it('writes normalized filter values to the URL when applied', () => {
    create();
    const genre = fixture.nativeElement.querySelector('[formControlName="genre"]') as HTMLInputElement;
    genre.value = 'Drama';
    genre.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('button[data-testid="apply-filters"]').click();

    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: expect.objectContaining({ genre: 'Drama', sort: 'rating', page: 0, size: 20 }) }));
  });

  it('does not issue a second request when navigation echoes applied filters', () => {
    create();
    api.list.mockClear();
    const genre = fixture.nativeElement.querySelector('[formControlName="genre"]') as HTMLInputElement;
    genre.value = 'Drama';
    genre.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('button[data-testid="apply-filters"]').click();
    queryParamMap.next(convertToParamMap({ genre: 'Drama', sort: 'rating', page: '0', size: '20' }));

    expect(api.list).toHaveBeenCalledTimes(1);
  });

  it('renders explicit empty cold-start guidance', () => {
    api.list.mockReturnValue(of(response([])));
    create();

    expect(fixture.nativeElement.querySelector('.empty[role="status"]')?.textContent).toContain('Rate a few movies');
  });

  it('renders a retry and browse fallback for recommendation outages', () => {
    api.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Unavailable' })));
    create();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('temporarily unavailable');
    expect(fixture.nativeElement.textContent).toContain('Browse movies');
  });

  it('maps rate limiting to a controlled user-facing message', () => {
    api.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' })));
    create();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('temporarily rate limited');
    expect(fixture.nativeElement.textContent).not.toContain('Too Many Requests');
  });

  it('delegates watchlist actions to the existing service', () => {
    api.list.mockReturnValue(of(response([item])));
    watchlist.add.mockReturnValue(of(void 0));
    create();

    fixture.nativeElement.querySelector('button[data-testid="watchlist-add"]').click();

    expect(watchlist.add).toHaveBeenCalledWith('movie-1');
  });

  it('creates a share and renders the public URL for a recommendation', () => {
    api.list.mockReturnValue(of(response([item])));
    shareApi.create.mockReturnValue(of({
      id: 'share-1', movieId: 'movie-1', publicToken: 'raw-token', publicPath: '/share/raw-token',
      createdAt: '2026-09-15T12:00:00Z', expiresAt: '2026-10-15T12:00:00Z', revoked: false,
    }));
    create();

    fixture.nativeElement.querySelector('button[data-testid="share-action"]').click();
    fixture.detectChanges();

    expect(shareApi.create).toHaveBeenCalledWith('movie-1', 30);
    expect(fixture.nativeElement.textContent).toContain(`${globalThis.location.origin}/share/raw-token`);
  });

  it('copies a paste-ready public URL through the Clipboard API when available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    api.list.mockReturnValue(of(response([item])));
    shareApi.create.mockReturnValue(of({
      id: 'share-1', movieId: 'movie-1', publicToken: 'raw-token', publicPath: '/share/raw-token',
      createdAt: '2026-09-15T12:00:00Z', expiresAt: '2026-10-15T12:00:00Z', revoked: false,
    }));
    create();

    fixture.nativeElement.querySelector('button[data-testid="share-action"]').click();
    await Promise.resolve();

    expect(writeText).toHaveBeenCalledWith(`${globalThis.location.origin}/share/raw-token`);
  });
});
