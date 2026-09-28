import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { CatalogApiService } from '../../core/catalog-api.service';
import { AdminCatalogComponent } from './catalog-admin.component';

describe('AdminCatalogComponent', () => {
  let fixture: ComponentFixture<AdminCatalogComponent>;
  const api = {
    movies: vi.fn(),
    genres: vi.fn(),
    movie: vi.fn(),
    createMovie: vi.fn(),
    updateMovie: vi.fn(),
    deleteMovie: vi.fn(),
    createGenre: vi.fn(),
    renameGenre: vi.fn(),
    deleteGenre: vi.fn(),
  };

  const movie = {
    id: 'movie-1', title: 'Arrival', overview: 'First contact', releaseYear: 2016,
    releaseDate: '2016-11-11', posterUrl: null, genres: [], averageRating: 0, ratingCount: 0,
  };
  const genre = { id: 'genre-1', name: 'Science Fiction' };
  const movieDetail = {
    ...movie, runtimeMinutes: 116, externalSource: 'tmdb', externalId: '123', createdAt: '', updatedAt: '',
    genres: [genre],
  };

  beforeEach(async () => {
    Object.values(api).forEach((mock) => mock.mockReset());
    api.movies.mockReturnValue(of({ content: [movie], page: 0, size: 24, totalElements: 1, totalPages: 1 }));
    api.genres.mockReturnValue(of([genre]));
    api.movie.mockReturnValue(of(movieDetail));
    api.createMovie.mockReturnValue(of(movie));
    api.updateMovie.mockReturnValue(of(movie));
    api.deleteMovie.mockReturnValue(of(undefined));
    api.createGenre.mockReturnValue(of(genre));
    api.renameGenre.mockReturnValue(of(genre));
    api.deleteGenre.mockReturnValue(of(undefined));
    await TestBed.configureTestingModule({
      imports: [AdminCatalogComponent],
      providers: [{ provide: CatalogApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminCatalogComponent);
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  it('loads current movies and genres with edit and delete controls', () => {
    expect(fixture.nativeElement.textContent).toContain('Arrival');
    expect(fixture.nativeElement.textContent).toContain('Science Fiction');
    expect(fixture.nativeElement.querySelector('[data-testid="edit-movie-movie-1"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="delete-movie-movie-1"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="delete-genre-genre-1"]')).not.toBeNull();
  });

  it('updates the selected movie without discarding existing metadata or genres', () => {
    api.updateMovie.mockReturnValue(of({ ...movieDetail, title: 'Arrival Updated' }));
    (fixture.nativeElement.querySelector('[data-testid="edit-movie-movie-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const title = fixture.nativeElement.querySelector('[data-testid="movie-title"]') as HTMLInputElement;
    title.value = 'Arrival Updated';
    title.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('[data-testid="movie-form"]') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(api.updateMovie).toHaveBeenCalledWith('movie-1', {
      title: 'Arrival Updated', overview: 'First contact', releaseYear: 2016,
      releaseDate: '2016-11-11', runtimeMinutes: 116, posterUrl: null,
      externalSource: 'tmdb', externalId: '123', genreIds: ['genre-1'],
    });
    expect(fixture.nativeElement.textContent).toContain('Movie updated.');
    expect(fixture.nativeElement.textContent).toContain('Arrival Updated');
  });

  it('disables movie submission while edit details are loading', () => {
    const details = new Subject<typeof movieDetail>();
    api.movie.mockReturnValue(details.asObservable());
    (fixture.nativeElement.querySelector('[data-testid="edit-movie-movie-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect((fixture.nativeElement.querySelector('[data-testid="movie-form"] button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
    details.next(movieDetail);
    details.complete();
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('[data-testid="movie-form"] button[type="submit"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('ignores movie details that arrive after editing is cancelled', () => {
    const details = new Subject<typeof movieDetail>();
    api.movie.mockReturnValue(details.asObservable());
    (fixture.nativeElement.querySelector('[data-testid="edit-movie-movie-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="movie-form"] button[type="button"]') as HTMLButtonElement).click();
    details.next(movieDetail);
    details.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Create movie');
    expect(fixture.nativeElement.textContent).not.toContain('Loading movie details');
  });

  it('submits poster and selected genre data when creating a movie', () => {
    const title = fixture.nativeElement.querySelector('[data-testid="movie-title"]') as HTMLInputElement;
    title.value = 'New movie';
    title.dispatchEvent(new Event('input'));
    const overview = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    overview.value = 'Overview';
    overview.dispatchEvent(new Event('input'));
    const year = fixture.nativeElement.querySelector('[formcontrolname="releaseYear"]') as HTMLInputElement;
    year.value = '2026';
    year.dispatchEvent(new Event('input'));
    const poster = fixture.nativeElement.querySelector('[formcontrolname="posterUrl"]') as HTMLInputElement;
    poster.value = 'https://example.test/poster.jpg';
    poster.dispatchEvent(new Event('input'));
    const checkbox = fixture.nativeElement.querySelector('input[type="checkbox"]') as HTMLInputElement;
    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change'));
    (fixture.nativeElement.querySelector('[data-testid="movie-form"]') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(api.createMovie).toHaveBeenCalledWith(expect.objectContaining({ posterUrl: 'https://example.test/poster.jpg', genreIds: ['genre-1'] }));
  });

  it('deletes movies and genres through their admin controls', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    (fixture.nativeElement.querySelector('[data-testid="delete-movie-movie-1"]') as HTMLButtonElement).click();
    (fixture.nativeElement.querySelector('[data-testid="delete-genre-genre-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(api.deleteMovie).toHaveBeenCalledWith('movie-1');
    expect(api.deleteGenre).toHaveBeenCalledWith('genre-1');
    expect(fixture.nativeElement.textContent).toContain('Genre deleted.');
  });

  it('does not delete when the confirmation is declined', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    (fixture.nativeElement.querySelector('[data-testid="delete-movie-movie-1"]') as HTMLButtonElement).click();
    expect(api.deleteMovie).not.toHaveBeenCalled();
  });

  it('rejects a release date whose year differs from the release year', () => {
    fixture.componentInstance.movieForm.patchValue({
      title: 'Mismatch', overview: 'Overview', releaseYear: 2026, releaseDate: '2025-12-31',
    });
    fixture.componentInstance.saveMovie();
    fixture.detectChanges();

    expect(fixture.componentInstance.movieForm.hasError('releaseYearMismatch')).toBe(true);
    expect(api.createMovie).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Release date must match the release year.');
  });

  it('explains referenced genre deletion conflicts', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    api.deleteGenre.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    (fixture.nativeElement.querySelector('[data-testid="delete-genre-genre-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Genre cannot be deleted while movies reference it.');
  });

  it('offers a retry when catalog loading fails', () => {
    api.movies
      .mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValueOnce(of({ content: [movie], page: 0, size: 24, totalElements: 1, totalPages: 1 }));
    fixture.componentInstance.retryCatalog();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Movies could not be loaded.');
    (fixture.nativeElement.querySelector('[data-testid="retry-catalog"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.movies).toHaveBeenCalledTimes(3);
    expect(fixture.nativeElement.textContent).toContain('Arrival');
  });
});
