import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { RecommendationShareApiService } from '../../core/recommendation-share-api.service';
import { ShareComponent } from './share.component';

describe('ShareComponent', () => {
  let fixture: ComponentFixture<ShareComponent>;
  const api = { publicLookup: vi.fn() };
  const paramMap = new BehaviorSubject(convertToParamMap({ publicToken: 'raw-token' }));

  beforeEach(async () => {
    api.publicLookup.mockReset();
    paramMap.next(convertToParamMap({ publicToken: 'raw-token' }));
    await TestBed.configureTestingModule({
      imports: [ShareComponent],
      providers: [
        { provide: RecommendationShareApiService, useValue: api },
        { provide: ActivatedRoute, useValue: { paramMap: paramMap.asObservable() } },
      ],
    }).compileComponents();
  });

  it('renders a public-safe movie summary and browse links', () => {
    api.publicLookup.mockReturnValue(of({
      id: 'share-1', movieId: 'movie-1', expiresAt: null,
      movie: { id: 'movie-1', title: 'Arrival', overview: 'First contact', releaseYear: 2016,
        releaseDate: null, posterUrl: null, genres: [], averageRating: 4.4, ratingCount: 10 },
    }));
    fixture = TestBed.createComponent(ShareComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Arrival');
    expect(fixture.nativeElement.textContent).toContain('Shared with you through Neo4flix');
    expect(fixture.nativeElement.querySelector('a[href="/movies"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('creator');
  });

  it('maps all unavailable responses to one generic not-found state', () => {
    api.publicLookup.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    fixture = TestBed.createComponent(ShareComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('This shared link is no longer available.');
  });
});
