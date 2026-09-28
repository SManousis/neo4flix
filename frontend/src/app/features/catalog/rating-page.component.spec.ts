import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, Subject } from 'rxjs';
import { CatalogApiService } from '../../core/catalog-api.service';
import { RatingApiService } from '../../core/rating-api.service';
import { RatingPageComponent } from './rating-page.component';

describe('RatingPageComponent', () => {
  let fixture: ComponentFixture<RatingPageComponent>;
  const catalog = {
    movie: vi.fn().mockReturnValue(of({ id: 'movie-1', title: 'Arrival', overview: 'First contact', releaseYear: 2016, releaseDate: null, posterUrl: null, genres: [], averageRating: 0, ratingCount: 0, runtimeMinutes: 116, externalSource: null, externalId: null, createdAt: '', updatedAt: '' })),
  };
  const ratings = {
    get: vi.fn().mockReturnValue(of(null)),
    create: vi.fn().mockReturnValue(of({ movieId: 'movie-1', score: 5, createdAt: '', updatedAt: '' })),
    update: vi.fn(),
    remove: vi.fn(),
  };

  beforeEach(async () => {
    Object.values(catalog).forEach((mock) => mock.mockClear());
    Object.values(ratings).forEach((mock) => mock.mockClear());
    ratings.get.mockReturnValue(of(null));
    ratings.create.mockReturnValue(of({ movieId: 'movie-1', score: 5, createdAt: '', updatedAt: '' }));
    await TestBed.configureTestingModule({
      imports: [RatingPageComponent],
      providers: [
        { provide: CatalogApiService, useValue: catalog },
        { provide: RatingApiService, useValue: ratings },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'movie-1' })) } },
      ],
    }).compileComponents();
  });

  function create(): void {
    fixture = TestBed.createComponent(RatingPageComponent);
    fixture.detectChanges();
  }

  it('renders accessible 1–5 controls and creates a missing rating', () => {
    create();
    const radios = fixture.nativeElement.querySelectorAll('input[type="radio"]');
    expect(radios.length).toBe(5);
    expect(fixture.nativeElement.querySelector('[role="radiogroup"]')?.getAttribute('aria-label'))
      .toBe('Choose a rating from one to five stars');
    expect(Array.from(radios).map((radio) => (radio as HTMLInputElement).getAttribute('aria-label')))
      .toEqual(['1 stars', '2 stars', '3 stars', '4 stars', '5 stars']);
    (radios[4] as HTMLInputElement).dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(ratings.create).toHaveBeenCalledWith('movie-1', 5);
    expect(fixture.nativeElement.querySelector('.status')?.textContent).toContain('Rating saved.');
  });

  it('preserves a user selection when the existing-rating lookup finishes later', () => {
    const pending = new Subject<null>();
    ratings.get.mockReturnValue(pending.asObservable());
    create();

    (fixture.nativeElement.querySelector('input[aria-label="5 stars"]') as HTMLInputElement).click();
    fixture.detectChanges();
    pending.next(null);
    pending.complete();
    fixture.detectChanges();

    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).disabled).toBe(false);
  });
});
