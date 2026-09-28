import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { RecommendationApiService } from '../../core/recommendation-api.service';
import { RecommendationItem } from '../../core/recommendation.models';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="home" aria-labelledby="home-title">
      <h1 id="home-title">Welcome to Neo4flix</h1>
      <p>Discover movies, rate what you love, and build a watchlist.</p>
      <nav aria-label="Discovery shortcuts">
        <a routerLink="/movies">Browse movies</a>
        <a routerLink="/recommendations">Recommendations</a>
        <a routerLink="/watchlist">Watchlist</a>
      </nav>
      <section aria-labelledby="home-recommendations-title">
        <h2 id="home-recommendations-title">Recommended for you</h2>
        @if (loading()) {
          <p role="status">Loading recommendations…</p>
        } @else if (error()) {
          <p role="alert">{{ error() }}</p>
          <a routerLink="/recommendations">Open recommendations</a>
        } @else if (!items().length) {
          <p role="status">Rate a few movies to improve your recommendations.</p>
          <a routerLink="/movies">Browse movies to rate</a>
        } @else {
          <ul aria-label="Recommended movies">
            @for (item of items(); track item.movie.id) {
              <li><a [routerLink]="['/movies', item.movie.id]">{{ item.movie.title }}</a></li>
            }
          </ul>
        }
      </section>
    </section>
  `,
  styles: [`
    .home { max-width: 1100px; margin: 2rem auto; padding: 0 1rem; }
    nav { display: flex; flex-wrap: wrap; gap: 1rem; margin: 1rem 0 2rem; }
    ul { padding-left: 1.25rem; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent implements OnInit {
  private readonly api = inject(RecommendationApiService);
  protected readonly items = signal<RecommendationItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.list({ size: 6 }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => this.items.set(response.items),
      error: () => this.error.set('Recommendations are temporarily unavailable.'),
    });
  }
}
