import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogApiService } from './catalog-api.service';

describe('CatalogApiService admin mutations', () => {
  let api: CatalogApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CatalogApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CatalogApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('serializes movie creation as an authenticated JSON mutation', () => {
    const movie = {
      title: 'Arrival', overview: 'First contact', releaseYear: 2016,
      releaseDate: '2016-11-11', runtimeMinutes: 116, posterUrl: null,
      externalSource: null, externalId: null, genreIds: ['genre-1'],
    };

    api.createMovie(movie).subscribe();

    const request = http.expectOne('/api/v1/movies');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(movie);
    request.flush({ id: 'movie-1', ...movie, genres: [], averageRating: 0, ratingCount: 0, createdAt: '', updatedAt: '' });
  });

  it('serializes genre creation as a query-parameter mutation', () => {
    api.createGenre('Science Fiction').subscribe();

    const request = http.expectOne('/api/v1/genres?name=Science%20Fiction');
    expect(request.request.method).toBe('POST');
    request.flush({ id: 'genre-1', name: 'Science Fiction' });
  });
});
