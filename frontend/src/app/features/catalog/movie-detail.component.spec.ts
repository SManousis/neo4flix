import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { AuthStore } from '../../core/auth.store';
import { CatalogApiService } from '../../core/catalog-api.service';
import { RatingApiService } from '../../core/rating-api.service';
import { WatchlistApiService } from '../../core/watchlist-api.service';
import { MovieDetailComponent } from './movie-detail.component';

const movie = {
  id: 'movie-1', title: 'Arrival', overview: 'First contact', releaseYear: 2016,
  releaseDate: null, posterUrl: null, genres: [], averageRating: 0, ratingCount: 0,
  runtimeMinutes: 116, externalSource: null, externalId: null, createdAt: '', updatedAt: '',
};

describe('MovieDetailComponent watchlist actions', () => {
  let fixture: ComponentFixture<MovieDetailComponent>;
  const catalog = { movie: vi.fn().mockReturnValue(of(movie)) };
  const ratings = { summary: vi.fn().mockReturnValue(of({ movieId: 'movie-1', averageRating: null, ratingCount: 0 })) };
  const watchlist = { add: vi.fn().mockReturnValue(of(void 0)), remove: vi.fn().mockReturnValue(of(void 0)) };
  const auth = { accessToken: signal<string | null>(null) };

  beforeEach(async () => {
    catalog.movie.mockReturnValue(of(movie));
    ratings.summary.mockReturnValue(of({ movieId: 'movie-1', averageRating: null, ratingCount: 0 }));
    watchlist.add.mockReturnValue(of(void 0));
    watchlist.remove.mockReturnValue(of(void 0));
    auth.accessToken.set(null);
    await TestBed.configureTestingModule({
      imports: [MovieDetailComponent],
      providers: [
        { provide: CatalogApiService, useValue: catalog },
        { provide: RatingApiService, useValue: ratings },
        { provide: WatchlistApiService, useValue: watchlist },
        { provide: AuthStore, useValue: auth },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'movie-1' })) } },
      ],
    }).compileComponents();
  });

  it('prompts anonymous viewers to sign in for watchlist actions', () => {
    fixture = TestBed.createComponent(MovieDetailComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sign in to save this movie');
    expect(watchlist.add).not.toHaveBeenCalled();
  });

  it('adds a movie for authenticated viewers and announces success', () => {
    auth.accessToken.set('token');
    fixture = TestBed.createComponent(MovieDetailComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('[data-testid="watchlist-action"]') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(watchlist.add).toHaveBeenCalledWith('movie-1');
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('Added to watchlist.');
  });

  it('renders the public poster and genre summary fields', () => {
    catalog.movie.mockReturnValue(of({ ...movie, posterUrl: 'https://example.test/arrival.jpg', genres: [{ id: 'g1', name: 'Science Fiction' }] }));
    fixture = TestBed.createComponent(MovieDetailComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('img[alt="Arrival poster"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Science Fiction');
  });

  it('renders a retryable load error and reloads the movie', () => {
    let unavailable = true;
    catalog.movie.mockImplementation(() => unavailable
      ? throwError(() => new Error('offline'))
      : of(movie));
    fixture = TestBed.createComponent(MovieDetailComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('Unable to load movie details. Please try again.');

    const initialCalls = catalog.movie.mock.calls.length;
    unavailable = false;
    (fixture.nativeElement.querySelector('[data-testid="movie-retry"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(catalog.movie.mock.calls.length).toBeGreaterThan(initialCalls);
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Arrival');
  });
});
