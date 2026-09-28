import { DOCUMENT } from '@angular/common';
import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import {
  catchError,
  finalize,
  Observable,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { AuthApiService } from './auth-api.service';
import { AuthResponse } from './auth.models';
import { AuthStore } from './auth.store';

const AUTH_RETRIED = new HttpContextToken<boolean>(() => false);
const NO_REFRESH_ENDPOINTS = [
  '/api/v1/auth/register',
  '/api/v1/auth/login',
  '/api/v1/auth/2fa/verify',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
];

function sameOriginApiPath(url: string, baseUrl: string): string | null {
  try {
    const base = new URL(baseUrl);
    const resolved = new URL(url, base);
    const isApiPath =
      resolved.pathname === '/api/v1' || resolved.pathname.startsWith('/api/v1/');
    return resolved.origin === base.origin && isApiPath ? resolved.pathname : null;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthRefreshCoordinator {
  private readonly api = inject(AuthApiService);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private refreshInFlight: Observable<AuthResponse> | null = null;

  refresh(): Observable<AuthResponse> {
    if (!this.refreshInFlight) {
      const request = this.api.refresh().pipe(
        tap((response) => this.store.acceptAuthenticatedSession(response)),
        catchError((error: unknown) => {
          this.store.clear();
          void this.router.navigateByUrl('/auth/login');
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshInFlight = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.refreshInFlight = request;
    }
    return this.refreshInFlight;
  }
}

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const store = inject(AuthStore);
  const coordinator = inject(AuthRefreshCoordinator);
  const document = inject(DOCUMENT);
  const apiPath = sameOriginApiPath(request.url, document.baseURI);
  const isEligibleApiRequest = apiPath !== null;
  const isRefreshExcluded = apiPath !== null && NO_REFRESH_ENDPOINTS.includes(apiPath);
  const token = store.accessToken();
  const authenticatedRequest =
    token && isEligibleApiRequest && !isRefreshExcluded
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
      if (
        !isUnauthorized ||
        !isEligibleApiRequest ||
        isRefreshExcluded ||
        request.context.get(AUTH_RETRIED)
      ) {
        return throwError(() => error);
      }

      return coordinator.refresh().pipe(
        switchMap((response) =>
          next(
            request.clone({
              context: request.context.set(AUTH_RETRIED, true),
              setHeaders: { Authorization: `Bearer ${response.accessToken}` },
            }),
          ),
        ),
      );
    }),
  );
};
