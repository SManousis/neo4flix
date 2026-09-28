import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, of, throwError } from 'rxjs';
import { RatingPage, RatingResponse, RatingSummary } from './rating.models';

@Injectable({ providedIn: 'root' })
export class RatingApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/ratings';

  create(movieId: string, score: number): Observable<RatingResponse> {
    return this.http.post<RatingResponse>(this.baseUrl, { movieId, score });
  }

  get(movieId: string): Observable<RatingResponse | null> {
    return this.http.get<RatingResponse>(`${this.baseUrl}/${encodeURIComponent(movieId)}`).pipe(
      catchError((error: unknown) =>
        error instanceof HttpErrorResponse && error.status === 404
          ? of(null)
          : throwError(() => error),
      ),
    );
  }

  update(movieId: string, score: number): Observable<RatingResponse> {
    return this.http.put<RatingResponse>(`${this.baseUrl}/${encodeURIComponent(movieId)}`, { score });
  }

  remove(movieId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(movieId)}`);
  }

  summary(movieId: string): Observable<RatingSummary> {
    return this.http.get<RatingSummary>(`${this.baseUrl}/movies/${encodeURIComponent(movieId)}/summary`);
  }

  history(page = 0, size = 24): Observable<RatingPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<RatingPage>(`${this.baseUrl}/me`, { params });
  }
}
