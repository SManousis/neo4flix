import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RecommendationApiService } from './recommendation-api.service';

describe('RecommendationApiService', () => {
  let api: RecommendationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RecommendationApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(RecommendationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('serializes non-empty recommendation filters without client-side ranking', () => {
    api.list({ genre: 'Science Fiction', fromYear: 1990, toYear: null, minimumAverageRating: 3.5, sort: 'rating', page: 0, size: 20 }).subscribe();

    const request = http.expectOne(
      '/api/v1/recommendations/me?genre=Science%20Fiction&fromYear=1990&minimumAverageRating=3.5&sort=rating&page=0&size=20',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [], strategy: 'POPULARITY', page: 0, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('omits empty filters while preserving server errors', () => {
    api.list({ genre: '', fromYear: null, toYear: null, minimumAverageRating: null, sort: 'recommendation', page: 0, size: 20 }).subscribe({
      error: (error) => expect(error.status).toBe(503),
    });

    const request = http.expectOne('/api/v1/recommendations/me?sort=recommendation&page=0&size=20');
    request.flush({ code: 'RECOMMENDATION_SERVICE_UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });
  });
});
