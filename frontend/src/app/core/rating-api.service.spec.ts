import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RatingApiService } from './rating-api.service';

describe('RatingApiService', () => {
  let api: RatingApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RatingApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(RatingApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('maps create, update, remove, summary, and history endpoints', () => {
    api.create('movie/1', 5).subscribe();
    const create = http.expectOne('/api/v1/ratings');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ movieId: 'movie/1', score: 5 });
    create.flush({});

    api.update('movie/1', 4).subscribe();
    const update = http.expectOne('/api/v1/ratings/movie%2F1');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ score: 4 });
    update.flush({});

    api.remove('movie/1').subscribe();
    expect(http.expectOne('/api/v1/ratings/movie%2F1').request.method).toBe('DELETE');

    api.summary('movie/1').subscribe();
    expect(http.expectOne('/api/v1/ratings/movies/movie%2F1/summary').request.method).toBe('GET');

    api.history(2, 10).subscribe();
    expect(http.expectOne('/api/v1/ratings/me?page=2&size=10').request.method).toBe('GET');
  });

  it('normalizes a missing current rating to null', () => {
    api.get('movie-1').subscribe((rating) => expect(rating).toBeNull());
    http.expectOne('/api/v1/ratings/movie-1').flush(
      { code: 'RATING_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
  });

  it('preserves non-404 current-rating errors', () => {
    api.get('movie-1').subscribe({
      next: () => { throw new Error('expected an error'); },
      error: (error: HttpErrorResponse) => expect(error.status).toBe(503),
    });
    http.expectOne('/api/v1/ratings/movie-1').flush({}, { status: 503, statusText: 'Unavailable' });
  });
});
