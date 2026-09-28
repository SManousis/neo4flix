import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { WatchlistPage } from './watchlist.models';

@Injectable({ providedIn: 'root' })
export class WatchlistApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/users/me/watchlist';

  list(page = 0, size = 24): Observable<WatchlistPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<WatchlistPage>(this.baseUrl, { params });
  }

  add(movieId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${encodeURIComponent(movieId)}`, null);
  }

  remove(movieId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(movieId)}`);
  }
}
