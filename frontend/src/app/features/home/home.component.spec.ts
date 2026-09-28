import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RecommendationApiService } from '../../core/recommendation-api.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  const recommendationApi = { list: vi.fn() };

  beforeEach(async () => {
    recommendationApi.list.mockReset();
    recommendationApi.list.mockReturnValue(of({ items: [], strategy: 'POPULARITY', page: 0, size: 6, totalItems: 0, totalPages: 0 }));
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        { provide: RecommendationApiService, useValue: recommendationApi },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
  });

  it('offers the required discovery destinations', () => {
    expect(fixture.nativeElement.querySelector('a[href="/movies"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/recommendations"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/watchlist"]')).not.toBeNull();
  });

  it('explains recommendation outages instead of rendering a blank section', () => {
    recommendationApi.list.mockReturnValue(throwError(() => new Error('offline')));
    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Recommendations are temporarily unavailable');
  });
});
