import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, finalize, of, switchMap, takeUntil, tap } from 'rxjs';
import { CatalogApiService } from '../../core/catalog-api.service';
import { MovieSummary, PageResult } from '../../core/catalog.models';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="search" aria-labelledby="search-title">
      <h1 id="search-title">Search movies</h1>
      <form (submit)="$event.preventDefault(); applySearch()" aria-label="Movie search">
        <label for="movie-search">Search titles</label>
        <input id="movie-search" [formControl]="title" autocomplete="off" />
        <button type="submit">Search</button>
      </form>
      @if (loading()) {
        <p role="status">Searching movies…</p>
      } @else if (error()) {
        <p role="alert">{{ error() }}</p>
        <button type="button" (click)="load(title.value)">Retry</button>
      } @else if (!results().content.length) {
        <p role="status">No movies match your search.</p>
        <a routerLink="/movies">Browse all movies</a>
      } @else {
        <p>{{ results().totalElements }} movies</p>
        <ul aria-label="Search results">
          @for (movie of results().content; track movie.id) {
            <li><a [routerLink]="['/movies', movie.id]">{{ movie.title }}</a></li>
          }
        </ul>
      }
    </section>
  `,
  styles: [`
    .search { max-width: 900px; margin: 2rem auto; padding: 0 1rem; }
    form { display: flex; flex-wrap: wrap; align-items: end; gap: .75rem; }
    label { flex-basis: 100%; }
    input { min-width: min(30rem, 100%); padding: .6rem; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(CatalogApiService);
  private readonly destroyed$ = new Subject<void>();
  protected readonly title = new FormControl('', { nonNullable: true });
  protected readonly results = signal<PageResult<MovieSummary>>({ content: [], page: 0, size: 24, totalElements: 0, totalPages: 0 });
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.route.queryParamMap.pipe(
      takeUntil(this.destroyed$),
      switchMap((params) => {
        const value = params.get('title') ?? '';
        this.title.setValue(value, { emitEvent: false });
        return this.load(value);
      }),
    ).subscribe();
  }

  protected applySearch(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { title: this.title.value || null },
      queryParamsHandling: 'merge',
    });
  }

  protected load(value: string) {
    this.loading.set(true);
    this.error.set(null);
    return this.api.movies({ title: value }).pipe(
      finalize(() => this.loading.set(false)),
      tap((page) => this.results.set(page)),
      catchError(() => {
        this.error.set('Unable to search movies. Please try again.');
        return of(this.results());
      }),
    );
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }
}
