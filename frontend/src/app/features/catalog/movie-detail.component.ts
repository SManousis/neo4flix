import { AsyncPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, map, of, shareReplay, startWith, Subject, switchMap } from 'rxjs';
import { AuthStore } from '../../core/auth.store';
import { CatalogApiService } from '../../core/catalog-api.service';
import { RatingApiService } from '../../core/rating-api.service';
import { WatchlistApiService } from '../../core/watchlist-api.service';

@Component({
  selector: 'app-movie-detail', standalone: true, imports: [AsyncPipe, RouterLink],
  template: `
    <a routerLink="/movies">← Movies</a>
    @if (movie$ | async; as movie) {
      <article>
        @if (movie.posterUrl) { <img [src]="movie.posterUrl" [alt]="movie.title + ' poster'" /> }
        <h1>{{ movie.title }}</h1>
        <p>{{ movie.overview }}</p>
        <p>{{ movie.releaseYear }} · {{ movie.runtimeMinutes ?? '—' }} minutes</p>
        @if (movie.genres.length) { <p>Genres: {{ movie.genres.map(genreName).join(', ') }}</p> }
        <section aria-labelledby="rating-title">
          <h2 id="rating-title">Rating</h2>
          @if (summary$ | async; as summary) { <p>{{ summary.averageRating === null ? 'No ratings yet' : summary.averageRating }} ({{ summary.ratingCount }} ratings)</p> }
          @if (authenticated()) { <a [routerLink]="['/movies', movie.id, 'rate']">Rate this movie</a> } @else { <a [routerLink]="['/auth/login']">Sign in to rate this movie</a> }
        </section>
        <section aria-labelledby="watchlist-title">
          <h2 id="watchlist-title">Watchlist</h2>
          @if (authenticated()) { <button type="button" data-testid="watchlist-action" [disabled]="watchlistBusy()" (click)="toggleWatchlist(movie.id)">{{ watchlisted() ? 'Remove from watchlist' : 'Add to watchlist' }}</button> } @else { <a [routerLink]="['/auth/login']">Sign in to save this movie</a> }
          @if (watchlistStatus()) { <p role="status">{{ watchlistStatus() }}</p> }
          @if (watchlistError()) { <p role="alert">{{ watchlistError() }}</p> }
        </section>
      </article>
    } @else if (movieLoading()) {
      <p role="status">Loading movie details…</p>
    } @else if (movieError()) {
      <p role="alert">{{ movieError() }}</p>
      <button type="button" data-testid="movie-retry" (click)="retryMovie()">Retry</button>
    } @else { <p role="status">Movie not found.</p> }
  `,
  styles: [
    ':host { display: block; }',
    'article { min-width: 0; }',
    'article img { display: block; max-width: 100%; height: auto; max-height: 32rem; object-fit: cover; }',
    '@media (max-width: 37.5rem) { article img { width: 100%; max-height: none; } }',
  ],
})
export class MovieDetailComponent {
  private readonly route = inject(ActivatedRoute); private readonly api = inject(CatalogApiService);
  private readonly ratingApi = inject(RatingApiService); private readonly auth = inject(AuthStore);
  private readonly watchlistApi = inject(WatchlistApiService); private readonly destroyRef = inject(DestroyRef);
  readonly watchlisted = signal(false);
  readonly watchlistBusy = signal(false);
  readonly watchlistStatus = signal<string | null>(null);
  readonly watchlistError = signal<string | null>(null);
  readonly movieLoading = signal(true);
  readonly movieError = signal<string | null>(null);
  readonly authenticated = () => this.auth.accessToken() !== null;
  readonly genreName = (genre: { name: string }) => genre.name;
  private readonly reloadMovie$ = new Subject<void>();
  readonly movie$ = this.reloadMovie$.pipe(
    startWith(void 0),
    switchMap(() => {
      this.movieLoading.set(true);
      this.movieError.set(null);
      return this.route.paramMap.pipe(
        map(params => params.get('id')),
        switchMap(id => id ? this.api.movie(id) : of(null)),
        catchError(() => {
          this.movieError.set('Unable to load movie details. Please try again.');
          return of(null);
        }),
        finalize(() => this.movieLoading.set(false)),
      );
    }),
    takeUntilDestroyed(this.destroyRef),
    shareReplay({ bufferSize: 1, refCount: false }),
  );
  readonly summary$ = this.route.paramMap.pipe(map(params => params.get('id')), switchMap(id => id ? this.ratingApi.summary(id) : of(null)), catchError(() => of(null)));

  retryMovie(): void { this.reloadMovie$.next(); }

  toggleWatchlist(movieId: string): void {
    if (this.watchlistBusy()) return;
    this.watchlistBusy.set(true);
    this.watchlistStatus.set(null);
    this.watchlistError.set(null);
    const request = this.watchlisted() ? this.watchlistApi.remove(movieId) : this.watchlistApi.add(movieId);
    request.pipe(finalize(() => this.watchlistBusy.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        const added = !this.watchlisted();
        this.watchlisted.set(added);
        this.watchlistStatus.set(added ? 'Added to watchlist.' : 'Removed from watchlist.');
      },
      error: () => this.watchlistError.set('Unable to update your watchlist. Please try again.'),
    });
  }
}
