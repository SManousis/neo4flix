import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CatalogFilters, GenreSummary, MovieDetail, MovieSummary, MovieWrite, PageResult } from './catalog.models';

@Injectable({ providedIn: 'root' })
export class CatalogApiService {
  private readonly http = inject(HttpClient);
  movies(filters: CatalogFilters = {}): Observable<PageResult<MovieSummary>> {
    let params = new HttpParams();
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== '') params = params.set(key, String(value)); });
    return this.http.get<PageResult<MovieSummary>>('/api/v1/movies', { params });
  }
  movie(id: string): Observable<MovieDetail> { return this.http.get<MovieDetail>(`/api/v1/movies/${encodeURIComponent(id)}`); }
  genres(): Observable<GenreSummary[]> { return this.http.get<GenreSummary[]>('/api/v1/genres'); }
  related(id: string): Observable<MovieSummary[]> { return this.http.get<MovieSummary[]>(`/api/v1/movies/${encodeURIComponent(id)}/related`); }
  createMovie(movie: MovieWrite): Observable<MovieDetail> { return this.http.post<MovieDetail>('/api/v1/movies', movie); }
  updateMovie(id: string, movie: MovieWrite): Observable<MovieDetail> { return this.http.patch<MovieDetail>(`/api/v1/movies/${encodeURIComponent(id)}`, movie); }
  deleteMovie(id: string): Observable<void> { return this.http.delete<void>(`/api/v1/movies/${encodeURIComponent(id)}`); }
  createGenre(name: string): Observable<GenreSummary> { return this.http.post<GenreSummary>('/api/v1/genres', null, { params: { name } }); }
  renameGenre(id: string, name: string): Observable<GenreSummary> { return this.http.patch<GenreSummary>(`/api/v1/genres/${encodeURIComponent(id)}`, null, { params: { name } }); }
  deleteGenre(id: string): Observable<void> { return this.http.delete<void>(`/api/v1/genres/${encodeURIComponent(id)}`); }
}
