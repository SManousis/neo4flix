import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RecommendationFilters, RecommendationResponse } from './recommendation.models';

@Injectable({ providedIn: 'root' })
export class RecommendationApiService {
  private readonly http = inject(HttpClient);

  list(filters: RecommendationFilters = {}): Observable<RecommendationResponse> {
    let params = new HttpParams();
    const entries: Array<[string, unknown]> = [
      ['genre', filters.genre],
      ['fromYear', filters.fromYear],
      ['toYear', filters.toYear],
      ['minimumAverageRating', filters.minimumAverageRating],
      ['sort', filters.sort],
      ['page', filters.page],
      ['size', filters.size],
    ];
    for (const [key, value] of entries) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<RecommendationResponse>('/api/v1/recommendations/me', { params });
  }
}
