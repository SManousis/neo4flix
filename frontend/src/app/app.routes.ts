import { Routes } from '@angular/router';
import { adminGuard, anonymousOnlyGuard, authGuard } from './core/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home.component').then((module) => module.HomeComponent),
  },
  {
    path: 'auth/login',
    canActivate: [anonymousOnlyGuard],
    loadComponent: () => import('./features/auth/login.component').then((module) => module.LoginComponent),
  },
  {
    path: 'auth/register',
    canActivate: [anonymousOnlyGuard],
    loadComponent: () =>
      import('./features/auth/register.component').then((module) => module.RegisterComponent),
  },
  {
    path: 'auth/2fa',
    canActivate: [anonymousOnlyGuard],
    loadComponent: () =>
      import('./features/auth/two-factor-login.component').then(
        (module) => module.TwoFactorLoginComponent,
      ),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/profile/profile.component').then((module) => module.ProfileComponent),
  },
  { path: 'home', canActivate: [authGuard], loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent) },
  { path: 'search', canActivate: [authGuard], loadComponent: () => import('./features/search/search.component').then((m) => m.SearchComponent) },
  { path: 'watchlist', canActivate: [authGuard], loadComponent: () => import('./features/watchlist/watchlist.component').then((m) => m.WatchlistComponent) },
  { path: 'recommendations', canActivate: [authGuard], loadComponent: () => import('./features/recommendations/recommendations.component').then((m) => m.RecommendationsComponent) },
  { path: 'share/:publicToken', loadComponent: () => import('./features/share/share.component').then((m) => m.ShareComponent) },
  { path: 'movies', loadComponent: () => import('./features/catalog/catalog.component').then((m) => m.CatalogComponent) },
  { path: 'movies/:id/rate', canActivate: [authGuard], loadComponent: () => import('./features/catalog/rating-page.component').then((m) => m.RatingPageComponent) },
  { path: 'movies/:id', loadComponent: () => import('./features/catalog/movie-detail.component').then((m) => m.MovieDetailComponent) },
  { path: 'admin', canActivate: [adminGuard], loadComponent: () => import('./features/admin/catalog-admin.component').then((m) => m.AdminCatalogComponent) },
  { path: 'admin/catalog', canActivate: [adminGuard], loadComponent: () => import('./features/admin/catalog-admin.component').then((m) => m.AdminCatalogComponent) },
  { path: 'admin/movies', canActivate: [adminGuard], loadComponent: () => import('./features/admin/catalog-admin.component').then((m) => m.AdminCatalogComponent) },
  { path: 'admin/movies/new', canActivate: [adminGuard], loadComponent: () => import('./features/admin/catalog-admin.component').then((m) => m.AdminCatalogComponent) },
  { path: 'admin/movies/:id/edit', canActivate: [adminGuard], loadComponent: () => import('./features/admin/catalog-admin.component').then((m) => m.AdminCatalogComponent) },
  { path: 'admin/genres', canActivate: [adminGuard], loadComponent: () => import('./features/admin/catalog-admin.component').then((m) => m.AdminCatalogComponent) },
  { path: '**', redirectTo: '' },
];
