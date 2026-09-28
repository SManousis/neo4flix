import { adminGuard, anonymousOnlyGuard, authGuard } from './core/auth.guards';
import { LoginComponent } from './features/auth/login.component';
import { RegisterComponent } from './features/auth/register.component';
import { TwoFactorLoginComponent } from './features/auth/two-factor-login.component';
import { ProfileComponent } from './features/profile/profile.component';
import { routes } from './app.routes';
import { AdminCatalogComponent } from './features/admin/catalog-admin.component';
import { ShareComponent } from './features/share/share.component';
import { HomeComponent } from './features/home/home.component';
import { SearchComponent } from './features/search/search.component';

describe('auth routes', () => {
  it('registers the three lazy auth pages with anonymous-only protection', async () => {
    const expected = [
      ['auth/login', LoginComponent],
      ['auth/register', RegisterComponent],
      ['auth/2fa', TwoFactorLoginComponent],
    ] as const;

    for (const [path, component] of expected) {
      const route = routes.find((candidate) => candidate.path === path);
      expect(route?.canActivate).toContain(anonymousOnlyGuard);
      expect(await route?.loadComponent?.()).toBe(component);
    }
  });

  it('protects the root application route for authenticated users', () => {
    const root = routes.find((route) => route.path === '');

    expect(root?.canActivate).toContain(authGuard);
  });

  it('protects the lazy profile page for authenticated users', async () => {
    const profile = routes.find((route) => route.path === 'profile');

    expect(profile?.canActivate).toContain(authGuard);
    expect(await profile?.loadComponent?.()).toBe(ProfileComponent);
  });

  it('protects the catalog admin page with the admin guard', async () => {
    const admin = routes.find((route) => route.path === 'admin/catalog');

    expect(admin?.canActivate).toContain(adminGuard);
    expect(await admin?.loadComponent?.()).toBe(AdminCatalogComponent);
  });

  it('registers the anonymous lazy share page without an auth guard', async () => {
    const share = routes.find((route) => route.path === 'share/:publicToken');

    expect(share?.canActivate).toBeUndefined();
    expect(await share?.loadComponent?.()).toBe(ShareComponent);
  });

  it('registers authenticated home and search pages', async () => {
    const home = routes.find((route) => route.path === 'home');
    const search = routes.find((route) => route.path === 'search');

    expect(home?.canActivate).toContain(authGuard);
    expect(await home?.loadComponent?.()).toBe(HomeComponent);
    expect(home?.pathMatch).toBeUndefined();
    expect(search?.canActivate).toContain(authGuard);
    expect(await search?.loadComponent?.()).toBe(SearchComponent);
  });

  it('registers canonical admin aliases with the admin guard', () => {
    for (const path of ['admin', 'admin/movies', 'admin/movies/new', 'admin/movies/:id/edit', 'admin/genres']) {
      const route = routes.find((candidate) => candidate.path === path);
      expect(route?.canActivate, path).toContain(adminGuard);
    }
  });
});
