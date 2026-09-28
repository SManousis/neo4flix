import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import { CatalogApiService } from '../../core/catalog-api.service';
import { MovieDetail } from '../../core/catalog.models';
import { RatingApiService } from '../../core/rating-api.service';
import { RatingResponse } from '../../core/rating.models';

@Component({
  selector: 'app-rating-page', standalone: true, imports: [RouterLink],
  template: `
    <a routerLink="/movies">← Movies</a>
    @if (loading()) { <p role="status">Loading rating…</p> }
    @else if (error()) { <p role="alert" class="error">{{ error() }}</p> }
    @else if (movie(); as item) {
      <article aria-labelledby="rating-page-title">
        <h1 id="rating-page-title">Rate {{ item.title }}</h1>
        <p>{{ item.overview }}</p>
        @if (status()) { <p role="status" class="status">{{ status() }}</p> }
        <fieldset [attr.aria-busy]="busy()">
          <legend>Your rating</legend>
          <div role="radiogroup" aria-label="Choose a rating from one to five stars">
            @for (score of scores; track score) {
              <label>
                <input type="radio" name="score" [value]="score" [checked]="selectedScore() === score" [attr.aria-label]="score + ' stars'" (change)="selectScore(score)" />
                {{ score }} ★
              </label>
            }
          </div>
        </fieldset>
        <button type="button" [disabled]="busy() || selectedScore() === 0" (click)="save()">
          {{ busy() ? 'Saving…' : (currentRating() ? 'Update rating' : 'Save rating') }}
        </button>
        @if (currentRating()) { <button type="button" [disabled]="busy()" (click)="remove()">Remove rating</button> }
      </article>
    }
  `,
})
export class RatingPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(CatalogApiService);
  private readonly ratings = inject(RatingApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly scores = [1, 2, 3, 4, 5];
  protected readonly movie = signal<MovieDetail | null>(null);
  protected readonly currentRating = signal<RatingResponse | null>(null);
  protected readonly selectedScore = signal(0);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly status = signal<string | null>(null);
  private movieId: string | null = null;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.movieId = params.get('id');
      if (!this.movieId) {
        this.loading.set(false);
        return;
      }

      this.catalog.movie(this.movieId).pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => {
          this.error.set('Unable to load this movie rating.');
          return of(null);
        }),
      ).subscribe((movie) => {
        this.movie.set(movie);
        this.loading.set(false);
      });

      this.ratings.get(this.movieId).pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(null)),
      ).subscribe((rating) => {
        this.currentRating.set(rating);
        if (this.selectedScore() === 0) this.selectedScore.set(rating?.score ?? 0);
      });
    });
  }

  protected selectScore(score: number): void { this.selectedScore.set(score); }

  protected save(): void {
    if (!this.movieId || this.selectedScore() === 0 || this.busy()) return;
    this.busy.set(true); this.error.set(null); this.status.set(null);
    const request = this.currentRating()
      ? this.ratings.update(this.movieId, this.selectedScore())
      : this.ratings.create(this.movieId, this.selectedScore());
    request.pipe(finalize(() => this.busy.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (rating) => { this.currentRating.set(rating); this.status.set('Rating saved.'); },
      error: () => this.error.set('Unable to save your rating. Please try again.'),
    });
  }

  protected remove(): void {
    if (!this.movieId || this.busy() || !this.currentRating()) return;
    this.busy.set(true); this.error.set(null); this.status.set(null);
    this.ratings.remove(this.movieId).pipe(finalize(() => this.busy.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.currentRating.set(null); this.selectedScore.set(0); this.status.set('Rating removed.'); },
      error: () => this.error.set('Unable to remove your rating. Please try again.'),
    });
  }
}
