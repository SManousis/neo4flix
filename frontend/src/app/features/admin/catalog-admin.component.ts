import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CatalogApiService } from '../../core/catalog-api.service';
import { GenreSummary, MovieDetail, MovieSummary, MovieWrite } from '../../core/catalog.models';

const releaseYearDateValidator: ValidatorFn = (control: AbstractControl) => {
  const year = control.get('releaseYear')?.value as number | null;
  const date = control.get('releaseDate')?.value as string | null;
  return year && date && Number(date.slice(0, 4)) !== year ? { releaseYearMismatch: true } : null;
};

@Component({
  selector: 'app-admin-catalog',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  template: `
    <section class="admin-catalog" aria-labelledby="admin-catalog-title">
      <h1 id="admin-catalog-title">Catalog administration</h1>
      <p class="status" role="status">{{ status() }}</p>
      @if (catalogError()) {
        <p class="field-error" role="alert">{{ catalogError() }}</p>
        <button mat-button data-testid="retry-catalog" type="button" (click)="retryCatalog()">Retry catalog loading</button>
      }

      <mat-card>
        <mat-card-header><mat-card-title>{{ editingMovieId() ? 'Edit movie' : 'Create movie' }}</mat-card-title></mat-card-header>
        <mat-card-content>
          <form [formGroup]="movieForm" (ngSubmit)="saveMovie()" aria-label="Movie form" data-testid="movie-form">
            <mat-form-field appearance="outline"><mat-label>Title</mat-label><input matInput data-testid="movie-title" formControlName="title" required /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Overview</mat-label><textarea matInput formControlName="overview" required></textarea></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Release year</mat-label><input matInput type="number" formControlName="releaseYear" required /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Release date</mat-label><input matInput type="date" formControlName="releaseDate" /></mat-form-field>
            @if (movieForm.hasError('releaseYearMismatch')) { <p class="field-error" role="alert">Release date must match the release year.</p> }
            <mat-form-field appearance="outline"><mat-label>Runtime minutes</mat-label><input matInput type="number" formControlName="runtimeMinutes" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Poster URL</mat-label><input matInput type="url" formControlName="posterUrl" /></mat-form-field>
            @if (genres().length) {
              <fieldset><legend>Genres</legend>
                @for (genre of genres(); track genre.id) {
                  <label><input type="checkbox" [checked]="movieGenreSelected(genre.id)" (change)="toggleMovieGenre(genre.id, $event)" /> {{ genre.name }}</label>
                }
              </fieldset>
            }
            <div class="actions">
              <button mat-flat-button type="submit" [disabled]="loadingMovieDetails()">{{ editingMovieId() ? 'Update movie' : 'Create movie' }}</button>
              @if (editingMovieId()) { <button mat-button type="button" (click)="cancelMovieEdit()">Cancel</button> }
            </div>
            @if (loadingMovieDetails()) { <p role="status">Loading movie details…</p> }
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header><mat-card-title>Movies</mat-card-title></mat-card-header>
        <mat-card-content>
          @if (loadingMovies()) { <p role="status">Loading movies…</p>
          } @else if (movies().length) {
            <ul aria-label="Current movies">
              @for (movie of movies(); track movie.id) {
                <li><span>{{ movie.title }} ({{ movie.releaseYear }})</span>
                  <span class="actions">
                    <button mat-button type="button" [attr.data-testid]="'edit-movie-' + movie.id" [attr.aria-label]="'Edit ' + movie.title" (click)="beginMovieEdit(movie)">Edit</button>
                    <button mat-button type="button" [attr.data-testid]="'delete-movie-' + movie.id" [attr.aria-label]="'Delete ' + movie.title" (click)="removeMovie(movie)">Delete</button>
                  </span>
                </li>
              }
            </ul>
          } @else { <p>No movies found.</p> }
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header><mat-card-title>{{ editingGenreId() ? 'Rename genre' : 'Create genre' }}</mat-card-title></mat-card-header>
        <mat-card-content>
          <form [formGroup]="genreForm" (ngSubmit)="saveGenre()" aria-label="Genre form">
            <mat-form-field appearance="outline"><mat-label>Genre name</mat-label><input matInput formControlName="name" required /></mat-form-field>
            <div class="actions">
              <button mat-flat-button type="submit">{{ editingGenreId() ? 'Rename genre' : 'Create genre' }}</button>
              @if (editingGenreId()) { <button mat-button type="button" (click)="cancelGenreEdit()">Cancel</button> }
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header><mat-card-title>Genres</mat-card-title></mat-card-header>
        <mat-card-content>
          @if (loadingGenres()) { <p role="status">Loading genres…</p>
          } @else if (genres().length) {
            <ul aria-label="Current genres">
              @for (genre of genres(); track genre.id) {
                <li><span>{{ genre.name }}</span>
                  <span class="actions">
                    <button mat-button type="button" [attr.data-testid]="'edit-genre-' + genre.id" [attr.aria-label]="'Rename ' + genre.name" (click)="beginGenreEdit(genre)">Rename</button>
                    <button mat-button type="button" [attr.data-testid]="'delete-genre-' + genre.id" [attr.aria-label]="'Delete ' + genre.name" (click)="removeGenre(genre)">Delete</button>
                  </span>
                </li>
              }
            </ul>
          } @else { <p>No genres found.</p> }
        </mat-card-content>
      </mat-card>
    </section>
  `,
  styles: [`.admin-catalog{width:min(100%,52rem);margin:0 auto;padding-inline:clamp(0rem,2vw,1rem)}.admin-catalog mat-card{margin-block:1rem}.admin-catalog form{display:grid;gap:1rem}.admin-catalog fieldset{border:0;display:flex;flex-wrap:wrap;gap:.75rem;padding:0}.admin-catalog ul{list-style:none;padding:0}.admin-catalog li{align-items:center;display:flex;justify-content:space-between;padding:.5rem 0;gap:.75rem}.actions{display:flex;gap:.5rem;flex-wrap:wrap}.status{min-height:1.5rem}@media (max-width:37.5rem){.admin-catalog li{align-items:flex-start;flex-direction:column}.admin-catalog .actions{width:100%}.admin-catalog .actions button{flex:1 1 auto}}`],
})
export class AdminCatalogComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  private movieEditRequestId = 0;
  readonly status = signal('');
  readonly movies = signal<MovieSummary[]>([]);
  readonly genres = signal<GenreSummary[]>([]);
  readonly loadingMovies = signal(true);
  readonly loadingGenres = signal(true);
  readonly loadingMovieDetails = signal(false);
  readonly catalogError = signal<string | null>(null);
  readonly editingMovieId = signal<string | null>(null);
  readonly editingMovieDetails = signal<MovieDetail | null>(null);
  readonly editingGenreId = signal<string | null>(null);
  readonly movieForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    overview: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    releaseYear: new FormControl<number | null>(null, Validators.required),
    releaseDate: new FormControl<string | null>(null),
    runtimeMinutes: new FormControl<number | null>(null),
    posterUrl: new FormControl('', { nonNullable: true }),
    genreIds: new FormControl<string[]>([], { nonNullable: true }),
  }, { validators: releaseYearDateValidator });
  readonly genreForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void { this.loadCatalog(); }

  retryCatalog(): void { this.loadCatalog(); }

  private loadCatalog(): void {
    this.catalogError.set(null);
    this.loadingMovies.set(true);
    this.loadingGenres.set(true);
    this.api.movies().subscribe({ next: (page) => this.movies.set(page.content), error: () => { this.loadingMovies.set(false); this.catalogError.set('Movies could not be loaded. Please try again.'); }, complete: () => this.loadingMovies.set(false) });
    this.api.genres().subscribe({ next: (genres) => this.genres.set(genres), error: () => { this.loadingGenres.set(false); this.catalogError.set('Genres could not be loaded. Please try again.'); }, complete: () => this.loadingGenres.set(false) });
  }

  private toMovieSummary(movie: MovieDetail): MovieSummary {
    return {
      id: movie.id, title: movie.title, overview: movie.overview, releaseYear: movie.releaseYear,
      releaseDate: movie.releaseDate, posterUrl: movie.posterUrl, genres: movie.genres ?? [],
      averageRating: movie.averageRating ?? 0, ratingCount: movie.ratingCount ?? 0,
    };
  }

  beginMovieEdit(movie: MovieSummary): void {
    const requestId = ++this.movieEditRequestId;
    this.editingMovieId.set(movie.id);
    this.loadingMovieDetails.set(true);
    this.api.movie(movie.id).subscribe({
      next: (detail) => {
        if (requestId !== this.movieEditRequestId) return;
        this.editingMovieId.set(detail.id);
        this.editingMovieDetails.set(detail);
        this.movieForm.patchValue({
          title: detail.title, overview: detail.overview, releaseYear: detail.releaseYear,
          releaseDate: detail.releaseDate, runtimeMinutes: detail.runtimeMinutes,
          posterUrl: detail.posterUrl ?? '', genreIds: detail.genres.map((genre) => genre.id),
        });
        this.loadingMovieDetails.set(false);
      },
      error: () => {
        if (requestId !== this.movieEditRequestId) return;
        this.loadingMovieDetails.set(false);
        this.cancelMovieEdit();
        this.status.set('Movie could not be loaded for editing.');
      },
    });
  }

  cancelMovieEdit(): void { this.movieEditRequestId++; this.loadingMovieDetails.set(false); this.editingMovieId.set(null); this.editingMovieDetails.set(null); this.movieForm.reset(); }

  movieGenreSelected(id: string): boolean { return this.movieForm.controls.genreIds.value.includes(id); }

  toggleMovieGenre(id: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const current = this.movieForm.controls.genreIds.value;
    this.movieForm.controls.genreIds.setValue(checked ? [...new Set([...current, id])] : current.filter((genreId) => genreId !== id));
  }

  saveMovie(): void {
    if (this.movieForm.invalid) { this.status.set('Enter the required movie fields.'); return; }
    const value = this.movieForm.getRawValue();
    const movie: MovieWrite = {
      title: value.title.trim(), overview: value.overview.trim(), releaseYear: value.releaseYear!,
      releaseDate: value.releaseDate || null, runtimeMinutes: value.runtimeMinutes,
      posterUrl: value.posterUrl.trim() || null,
      externalSource: this.editingMovieDetails()?.externalSource ?? null,
      externalId: this.editingMovieDetails()?.externalId ?? null,
      genreIds: value.genreIds,
    };
    const id = this.editingMovieId();
    const request = id ? this.api.updateMovie(id, movie) : this.api.createMovie(movie);
    request.subscribe({
      next: (saved) => {
        const summary = this.toMovieSummary(saved);
        this.movies.update((current) => id ? current.map((item) => item.id === id ? summary : item) : [summary, ...current]);
        this.status.set(id ? 'Movie updated.' : 'Movie created.');
        this.cancelMovieEdit();
      },
      error: () => this.status.set(id ? 'Movie could not be updated.' : 'Movie could not be created.'),
    });
  }

  removeMovie(movie: MovieSummary): void {
    if (!window.confirm(`Delete movie “${movie.title}”?`)) return;
    this.api.deleteMovie(movie.id).subscribe({ next: () => { this.movies.update((current) => current.filter((item) => item.id !== movie.id)); this.status.set('Movie deleted.'); }, error: () => this.status.set('Movie could not be deleted.') });
  }

  beginGenreEdit(genre: GenreSummary): void { this.editingGenreId.set(genre.id); this.genreForm.setValue({ name: genre.name }); }
  cancelGenreEdit(): void { this.editingGenreId.set(null); this.genreForm.reset(); }

  saveGenre(): void {
    if (this.genreForm.invalid) { this.status.set('Enter a genre name.'); return; }
    const name = this.genreForm.controls.name.value.trim();
    const id = this.editingGenreId();
    const request = id ? this.api.renameGenre(id, name) : this.api.createGenre(name);
    request.subscribe({
      next: (saved) => {
        this.genres.update((current) => id ? current.map((item) => item.id === id ? saved : item) : [saved, ...current]);
        this.status.set(id ? 'Genre renamed.' : 'Genre created.');
        this.cancelGenreEdit();
      },
      error: () => this.status.set(id ? 'Genre could not be renamed.' : 'Genre could not be created.'),
    });
  }

  removeGenre(genre: GenreSummary): void {
    if (!window.confirm(`Delete genre “${genre.name}”?`)) return;
    this.api.deleteGenre(genre.id).subscribe({
      next: () => { this.genres.update((current) => current.filter((item) => item.id !== genre.id)); this.status.set('Genre deleted.'); },
      error: (error: HttpErrorResponse) => this.status.set(error.status === 409 ? 'Genre cannot be deleted while movies reference it.' : 'Genre could not be deleted.'),
    });
  }
}
