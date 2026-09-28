import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { WatchlistApiService } from '../../core/watchlist-api.service';
import { WatchlistEntry } from '../../core/watchlist.models';

@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section aria-labelledby="watchlist-title">
      <a routerLink="/movies">← Browse movies</a>
      <h1 id="watchlist-title">Watchlist</h1>
      @if (loading()) {
        <p role="status">Loading watchlist…</p>
      } @else {
        @if (error()) {
          <p role="alert" class="error">{{ error() }}</p>
          <button type="button" (click)="load()">Retry</button>
        }
        @if (!entries().length && !error()) {
          <p role="status" class="empty">Your watchlist is empty.</p>
          <a routerLink="/movies">Browse movies</a>
        } @else if (entries().length) {
          <ul data-testid="watchlist">
            @for (entry of entries(); track entry.movieId) {
              <li>
                <a [routerLink]="['/movies', entry.movieId]">{{ entry.title }}</a>
                @if (entry.releaseYear) { <span>{{ entry.releaseYear }}</span> }
                <button type="button" [disabled]="busyMovieId() === entry.movieId" (click)="remove(entry)">
                  {{ busyMovieId() === entry.movieId ? 'Removing…' : 'Remove' }}
                </button>
              </li>
            }
          </ul>
        }
      }
    </section>
  `,
})
export class WatchlistComponent implements OnInit {
  private readonly api = inject(WatchlistApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly entries = signal<WatchlistEntry[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busyMovieId = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (page) => this.entries.set(page.content),
      error: () => this.error.set('Unable to load your watchlist. Please try again.'),
    });
  }

  protected remove(entry: WatchlistEntry): void {
    if (this.busyMovieId()) return;
    this.busyMovieId.set(entry.movieId);
    this.error.set(null);
    this.api.remove(entry.movieId).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.busyMovieId.set(null)),
    ).subscribe({
      next: () => this.entries.update((current) => current.filter((item) => item.movieId !== entry.movieId)),
      error: () => this.error.set('Unable to update your watchlist. Please try again.'),
    });
  }
}
