import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RecommendationShareApiService } from './recommendation-share-api.service';

describe('RecommendationShareApiService', () => {
  let api: RecommendationShareApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RecommendationShareApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(RecommendationShareApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('maps owner creation and public lookup with encoded path values', () => {
    api.create('movie/1', 30).subscribe();
    expect(http.expectOne('/api/v1/recommendation-shares').request.method).toBe('POST');
    api.publicLookup('raw/token').subscribe();
    expect(http.expectOne((request) => request.url === '/api/v1/shares/raw%2Ftoken').request.method).toBe('GET');
  });
});
