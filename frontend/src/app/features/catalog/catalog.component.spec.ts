import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CatalogApiService } from '../../core/catalog-api.service';
import { CatalogComponent } from './catalog.component';

describe('CatalogComponent', () => {
  let fixture: ComponentFixture<CatalogComponent>;
  const api = { movies: vi.fn() };
  const router = { navigate: vi.fn() };

  beforeEach(async () => {
    api.movies.mockReset();
    router.navigate.mockReset();
    api.movies.mockReturnValue(of({ content: [], page: 0, size: 24, totalElements: 0, totalPages: 0 }));
    await TestBed.configureTestingModule({
      imports: [CatalogComponent],
      providers: [
        { provide: CatalogApiService, useValue: api },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({ title: 'Arrival' })) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CatalogComponent);
    fixture.detectChanges();
  });

  it('loads title filters from the URL and renders an actionable empty state', () => {
    expect(api.movies).toHaveBeenCalledWith({ title: 'Arrival' });
    expect(fixture.nativeElement.textContent).toContain('No movies found.');
    expect(fixture.nativeElement.querySelector('a[routerlink="/search"]')).not.toBeNull();
  });

  it('shows a retryable error instead of silently replacing failures with an empty page', () => {
    api.movies.mockReturnValue(throwError(() => new Error('offline')));
    fixture = TestBed.createComponent(CatalogComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unable to load movies. Please try again.');
    expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
  });
});
