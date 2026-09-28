import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreatedRecommendationShare,
  PublicRecommendationShare,
  RecommendationShareOwner,
  RecommendationShareUpdate,
} from './recommendation-share.models';

@Injectable({ providedIn: 'root' })
export class RecommendationShareApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/recommendation-shares';

  create(movieId: string, expiresInDays = 30): Observable<CreatedRecommendationShare> {
    return this.http.post<CreatedRecommendationShare>(this.baseUrl, { movieId, expiresInDays });
  }

  list(): Observable<RecommendationShareOwner[]> {
    return this.http.get<RecommendationShareOwner[]>(this.baseUrl);
  }

  get(id: string): Observable<RecommendationShareOwner> {
    return this.http.get<RecommendationShareOwner>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  update(id: string, request: RecommendationShareUpdate): Observable<RecommendationShareOwner> {
    return this.http.patch<RecommendationShareOwner>(`${this.baseUrl}/${encodeURIComponent(id)}`, request);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  publicLookup(publicToken: string): Observable<PublicRecommendationShare> {
    return this.http.get<PublicRecommendationShare>(`/api/v1/shares/${encodeURIComponent(publicToken)}`);
  }
}
