import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { RecommendationShareApiService } from '../../core/recommendation-share-api.service';
import { PublicRecommendationShare } from '../../core/recommendation-share.models';

@Component({
  selector: 'app-share',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="share" aria-labelledby="share-title">
      @if (loading()) {
        <p role="status">Loading shared movie…</p>
      } @else if (unavailable()) {
        <h1 id="share-title">Shared movie unavailable</h1>
        <p role="alert">This shared link is no longer available.</p>
        <a routerLink="/movies">Browse movies</a>
        <a routerLink="/auth/register">Create an account</a>
      } @else if (share(); as current) {
        <a routerLink="/movies">← Browse movies</a>
        <p>Shared with you through Neo4flix</p>
        <article>
          <h1 id="share-title">{{ current.movie.title }}</h1>
          @if (current.movie.posterUrl) { <img [src]="current.movie.posterUrl" [alt]="current.movie.title" /> }
          @if (current.movie.releaseYear) { <p>{{ current.movie.releaseYear }}</p> }
          @if (current.movie.overview) { <p>{{ current.movie.overview }}</p> }
          @if (current.movie.genres.length) { <p>Genres: {{ current.movie.genres.map(genreName).join(', ') }}</p> }
          <p>Rating: {{ current.movie.averageRating }} ({{ current.movie.ratingCount }} ratings)</p>
          <a [routerLink]="['/movies', current.movie.id]">View movie</a>
        </article>
      }
    </main>
  `,
  styles: [`.share{max-width:720px;margin:2rem auto;padding:0 1rem}.share a{display:inline-block;margin:.5rem .75rem .5rem 0}.share article{border:1px solid #ddd;border-radius:8px;padding:1rem}.share img{max-width:240px}`],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShareComponent implements OnInit {
  private readonly api = inject(RecommendationShareApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = signal(true);
  protected readonly unavailable = signal(false);
  protected readonly share = signal<PublicRecommendationShare | null>(null);

  ngOnInit(): void {
    this.route.paramMap.pipe(
      switchMap((params) => {
        const token = params.get('publicToken');
        return token ? this.api.publicLookup(token).pipe(catchError(() => of(null))) : of(null);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((result) => {
      this.loading.set(false);
      this.share.set(result);
      this.unavailable.set(result === null);
    });
  }

  protected genreName(genre: { name: string }): string {
    return genre.name;
  }
}
