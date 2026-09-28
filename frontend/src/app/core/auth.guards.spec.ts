import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { AuthResponse, UserRole } from './auth.models';
import { adminGuard, anonymousOnlyGuard, authGuard } from './auth.guards';
import { AuthStore } from './auth.store';

describe('authentication guards', () => {
  let store: AuthStore;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    store = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
    store.clear();
  });

  function runGuard(guard: CanActivateFn, url: string): boolean | UrlTree {
    return TestBed.runInInjectionContext(
      () =>
        guard(
          {} as ActivatedRouteSnapshot,
          { url } as RouterStateSnapshot,
        ) as boolean | UrlTree,
    );
  }

  function authenticate(role: UserRole): void {
    const response: AuthResponse = {
      accessToken: 'access-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: 'user-1',
        email: 'alice@example.com',
        displayName: 'Alice',
        role,
        twoFactorEnabled: false,
        createdAt: '2026-09-13T10:00:00Z',
      },
    };
    store.acceptAuthenticatedSession(response);
  }

  it('redirects anonymous users to login while preserving the protected destination', () => {
    const result = runGuard(authGuard, '/profile');

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/auth/login?returnUrl=%2Fprofile');
  });

  it('redirects authenticated users away from anonymous-only pages', () => {
    authenticate('USER');

    const result = runGuard(anonymousOnlyGuard, '/auth/login');

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/');
  });

  it('redirects a non-admin user away from the admin area', () => {
    authenticate('USER');

    const result = runGuard(adminGuard, '/admin');

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/');
  });

  it('allows authenticated users and administrators through their permitted guards', () => {
    authenticate('ADMIN');

    expect(runGuard(authGuard, '/profile')).toBe(true);
    expect(runGuard(adminGuard, '/admin')).toBe(true);
  });
});
