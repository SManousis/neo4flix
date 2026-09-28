import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AppStateService } from './core/app-state.service';
import { AuthState } from './core/auth.models';
import { AuthStore } from './core/auth.store';

describe('AppComponent', () => {
  const authState = signal<AuthState>({
    status: 'anonymous',
    user: null,
    accessToken: null,
    pendingChallenge: null,
  });
  const authStore = { state: authState, logout: vi.fn(() => of(undefined)) };
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };

  beforeEach(() => {
    authState.set({
      status: 'anonymous',
      user: null,
      accessToken: null,
      pendingChallenge: null,
    });
    authStore.logout.mockClear();
    router.navigateByUrl.mockClear();
  });

  it('renders the Neo4flix product shell', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: AuthStore, useValue: authStore },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('main')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Neo4flix');

    TestBed.inject(AppStateService).applicationName.set('Neo4flix preview');
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-toolbar').textContent).toContain(
      'Neo4flix preview',
    );
    expect(fixture.nativeElement.querySelector('main h1')).toBeNull();
    const mobileMenu = fixture.nativeElement.querySelector('.mobile-menu-trigger') as HTMLButtonElement;
    expect(mobileMenu.getAttribute('aria-label')).toBe('Open primary navigation menu');
  });

  it('shows anonymous actions and logs an authenticated user out through the store', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: AuthStore, useValue: authStore },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sign in');
    expect(fixture.nativeElement.textContent).toContain('Register');

    authState.set({
      status: 'authenticated',
      user: {
        id: 'user-1',
        email: 'alice@example.com',
        displayName: 'Alice',
        role: 'USER' as const,
        twoFactorEnabled: false,
        createdAt: '2026-09-13T10:00:00Z',
      },
      accessToken: 'access-token',
      pendingChallenge: null,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Alice');
    for (const destination of [
      'Home',
      'Movies',
      'Recommendations',
      'Watchlist',
      'Search',
      'Profile',
    ]) {
      expect(fixture.nativeElement.textContent).toContain(destination);
    }
    fixture.nativeElement.querySelector('[data-testid="logout"]').click();
    expect(authStore.logout).toHaveBeenCalledOnce();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/login');
  });
});
