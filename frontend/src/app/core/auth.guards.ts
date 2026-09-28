import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from './auth.store';

function loginRedirect(url: string) {
  return inject(Router).createUrlTree(['/auth/login'], {
    queryParams: { returnUrl: url },
  });
}

export const authGuard: CanActivateFn = (_route, state) => {
  const store = inject(AuthStore);
  return store.status() === 'authenticated' ? true : loginRedirect(state.url);
};

export const anonymousOnlyGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  return store.status() === 'authenticated' ? inject(Router).createUrlTree(['/']) : true;
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const store = inject(AuthStore);
  if (store.status() !== 'authenticated') {
    return loginRedirect(state.url);
  }
  return store.user()?.role === 'ADMIN' ? true : inject(Router).createUrlTree(['/']);
};
