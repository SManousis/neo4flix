import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { CatalogApiService } from '../../core/catalog-api.service';
import { SearchComponent } from './search.component';

describe('SearchComponent', () => {
  let fixture: ComponentFixture<SearchComponent>;
  const catalogApi = { movies: vi.fn() };

  beforeEach(async () => {
    catalogApi.movies.mockReset();
    catalogApi.movies.mockReturnValue(of({ content: [], page: 0, size: 24, totalElements: 0, totalPages: 0 }));
    await TestBed.configureTestingModule({
      imports: [SearchComponent],
      providers: [
        { provide: CatalogApiService, useValue: catalogApi },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({ title: 'Arrival' })) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SearchComponent);
    fixture.detectChanges();
  });

  it('loads the title from the URL and sends it to the typed catalog client', () => {
    expect(catalogApi.movies).toHaveBeenCalledWith({ title: 'Arrival' });
    expect((fixture.nativeElement.querySelector('input') as HTMLInputElement).value).toBe('Arrival');
  });

  it('renders a useful empty state', () => {
    expect(fixture.nativeElement.textContent).toContain('No movies match your search.');
    expect(fixture.nativeElement.querySelector('a[href="/movies"]')).not.toBeNull();
  });
});
