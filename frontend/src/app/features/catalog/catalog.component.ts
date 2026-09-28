import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { CatalogApiService } from '../../core/catalog-api.service';
import { CatalogFilters, MovieSummary, PageResult } from '../../core/catalog.models';

const EMPTY_PAGE: PageResult<MovieSummary> = { content: [], page: 0, size: 24, totalElements: 0, totalPages: 0 };

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="catalog" aria-labelledby="catalog-title">
      <h1 id="catalog-title">Movies</h1>
      <form (submit)="$event.preventDefault(); applySearch()" aria-label="Movie filters">
        <mat-form-field appearance="outline">
          <mat-label>Search titles</mat-label>
          <input matInput [formControl]="search" />
        </mat-form-field>
        <button mat-flat-button type="submit">Search</button>
      </form>
      @if (loading()) {
        <p role="status">Loading movies…</p>
      } @else if (error()) {
        <p role="alert">{{ error() }}</p>
        <button mat-button type="button" (click)="retry()">Retry</button>
      } @else if (!results().content.length) {
        <p role="status">No movies found.</p>
        <a routerLink="/search">Try search</a>
      } @else {
        <p role="status">{{ results().totalElements }} movies</p>
        <div class="grid" data-testid="movie-grid">
          @for (movie of results().content; track movie.id) {
            <mat-card>
              @if (movie.posterUrl) { <img mat-card-image [src]="movie.posterUrl" [alt]="movie.title + ' poster'" /> }
              <mat-card-header>
                <mat-card-title>{{ movie.title }}</mat-card-title>
                <mat-card-subtitle>{{ movie.releaseYear }}</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <p>{{ movie.overview }}</p>
                <p>{{ movie.averageRating ? (movie.averageRating + ' (' + movie.ratingCount + ' ratings)') : 'No ratings yet' }}</p>
                @if (movie.genres.length) { <p>{{ movie.genres.map(genreName).join(', ') }}</p> }
              </mat-card-content>
              <mat-card-actions><a mat-button [routerLink]="['/movies', movie.id]">Details</a></mat-card-actions>
            </mat-card>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    .catalog { max-width: 1100px; margin: 2rem auto; padding: 0 1rem; }
    form { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; }
    img { width: 100%; aspect-ratio: 2 / 3; object-fit: cover; }
    @media (max-width: 37.5rem) {
      form { align-items: stretch; }
      form mat-form-field, form button { width: 100%; }
      .grid { grid-template-columns: 1fr; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly results = signal<PageResult<MovieSummary>>(EMPTY_PAGE);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private readonly filters = signal<CatalogFilters>({});

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const filters = this.readFilters(params);
      this.filters.set(filters);
      this.search.setValue(filters.title ?? '', { emitEvent: false });
      this.load(filters);
    });
  }

  protected applySearch(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { title: this.search.value || null, page: null },
      queryParamsHandling: 'merge',
    });
  }

  protected retry(): void { this.load(this.filters()); }

  protected genreName(genre: { name: string }): string { return genre.name; }

  private load(filters: CatalogFilters): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.movies(filters).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (page) => this.results.set(page),
      error: () => this.error.set('Unable to load movies. Please try again.'),
    });
  }

  private readFilters(params: import('@angular/router').ParamMap): CatalogFilters {
    const number = (name: string): number | undefined => {
      const raw = params.get(name);
      if (raw === null || raw === '') return undefined;
      const value = Number(raw);
      return Number.isFinite(value) ? value : undefined;
    };
    const text = (name: string): string | undefined => params.get(name) || undefined;
    const direction = text('direction');
    return {
      title: text('title'), genre: text('genre'), minYear: number('minYear'), maxYear: number('maxYear'),
      fromDate: text('fromDate'), sort: text('sort'), direction: direction === 'asc' ? 'asc' : direction === 'desc' ? 'desc' : undefined,
      page: number('page'), size: number('size'),
    };
  }
}
