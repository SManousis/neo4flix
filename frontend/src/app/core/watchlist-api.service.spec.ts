import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { WatchlistApiService } from './watchlist-api.service';

describe('WatchlistApiService', () => {
  let api: WatchlistApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [WatchlistApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(WatchlistApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('maps list, add, and remove to the watchlist endpoints', () => {
    api.list(1, 10).subscribe();
    expect(http.expectOne('/api/v1/users/me/watchlist?page=1&size=10').request.method).toBe('GET');

    api.add('movie/1').subscribe();
    expect(http.expectOne('/api/v1/users/me/watchlist/movie%2F1').request.method).toBe('POST');

    api.remove('movie/1').subscribe();
    expect(http.expectOne('/api/v1/users/me/watchlist/movie%2F1').request.method).toBe('DELETE');
  });
});
