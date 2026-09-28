import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { WatchlistApiService } from '../../core/watchlist-api.service';
import { WatchlistComponent } from './watchlist.component';

const page = (content: Array<{ movieId: string; title: string; overview: string; releaseYear: number; posterUrl: string | null; createdAt: string }>) => ({
  content, page: 0, size: 24, totalElements: content.length, totalPages: content.length ? 1 : 0,
});

describe('WatchlistComponent', () => {
  let fixture: ComponentFixture<WatchlistComponent>;
  const api = { list: vi.fn(), remove: vi.fn() };

  beforeEach(async () => {
    api.list.mockReset();
    api.remove.mockReset();
    api.list.mockReturnValue(of(page([])));
    await TestBed.configureTestingModule({
      imports: [WatchlistComponent],
      providers: [
        { provide: WatchlistApiService, useValue: api },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();
  });

  function create(): void {
    fixture = TestBed.createComponent(WatchlistComponent);
    fixture.detectChanges();
  }

  it('renders an explicit loading state and then the empty state', () => {
    const pending = new Subject<ReturnType<typeof page>>();
    api.list.mockReturnValue(pending);
    create();
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('Loading watchlist');

    pending.next(page([]));
    pending.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty[role="status"]')?.textContent).toContain('Your watchlist is empty.');
  });

  it('renders entries and removes one after a successful mutation', () => {
    api.list.mockReturnValue(of(page([{ movieId: 'movie-1', title: 'Arrival', overview: 'First contact', releaseYear: 2016, posterUrl: null, createdAt: '2026-09-14T00:00:00Z' }])));
    api.remove.mockReturnValue(of(void 0));
    create();
    expect(fixture.nativeElement.querySelector('[data-testid="watchlist"]')?.textContent).toContain('Arrival');

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.remove).toHaveBeenCalledWith('movie-1');
    expect(fixture.nativeElement.querySelector('.empty[role="status"]')?.textContent).toContain('Your watchlist is empty.');
  });

  it('keeps the entry and shows an accessible error when removal fails', () => {
    api.list.mockReturnValue(of(page([{ movieId: 'movie-1', title: 'Arrival', overview: 'First contact', releaseYear: 2016, posterUrl: null, createdAt: '2026-09-14T00:00:00Z' }])));
    api.remove.mockReturnValue(throwError(() => new Error('unavailable')));
    create();

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="watchlist"]')?.textContent).toContain('Arrival');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Unable to update your watchlist.');
  });

  it('renders a load error with a retry action', () => {
    api.list
      .mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValueOnce(of(page([{ movieId: 'movie-2', title: 'Dune', overview: 'Desert', releaseYear: 2021, posterUrl: null, createdAt: '2026-09-14T00:00:00Z' }])));
    create();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Unable to load your watchlist.');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.list).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('[data-testid="watchlist"]')?.textContent).toContain('Dune');
  });
});
