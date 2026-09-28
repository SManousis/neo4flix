import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, map, Observable, of, tap, throwError } from 'rxjs';
import { AuthApiService } from './auth-api.service';
import {
  AuthResponse,
  AuthState,
  isTwoFactorChallenge,
  LoginRequest,
  LoginResponse,
  PublicUser,
  RegisterRequest,
} from './auth.models';

const INITIAL_STATE: AuthState = {
  status: 'idle',
  user: null,
  accessToken: null,
  pendingChallenge: null,
};

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApiService);
  private readonly mutableState = signal<AuthState>(INITIAL_STATE);

  readonly state = this.mutableState.asReadonly();
  readonly status = computed(() => this.state().status);
  readonly user = computed(() => this.state().user);
  readonly accessToken = computed(() => this.state().accessToken);
  readonly pendingChallenge = computed(() => this.state().pendingChallenge);

  bootstrap(): Observable<void> {
    this.mutableState.set({ ...INITIAL_STATE, status: 'bootstrapping' });
    return this.api.refresh().pipe(
      tap((response) => this.acceptAuthenticatedSession(response)),
      map(() => undefined),
      catchError(() => {
        this.clear();
        return of(undefined);
      }),
    );
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.api.login(request).pipe(
      tap((response) => {
        if (isTwoFactorChallenge(response)) {
          this.mutableState.set({
            status: 'anonymous',
            user: null,
            accessToken: null,
            pendingChallenge: {
              challengeToken: response.challengeToken,
              expiresIn: response.expiresIn,
            },
          });
          return;
        }
        this.acceptAuthenticatedSession(response);
      }),
    );
  }

  register(request: RegisterRequest): Observable<PublicUser> {
    return this.api.register(request);
  }

  verifyTwoFactor(code: string): Observable<AuthResponse> {
    const challenge = this.pendingChallenge();
    if (!challenge) {
      return throwError(() => new Error('No active two-factor challenge'));
    }
    return this.api
      .verifyTwoFactor({ challengeToken: challenge.challengeToken, code })
      .pipe(tap((response) => this.acceptAuthenticatedSession(response)));
  }

  logout(): Observable<void> {
    return this.api.logout().pipe(finalize(() => this.clear()));
  }

  acceptAuthenticatedSession(response: AuthResponse): void {
    this.mutableState.set({
      status: 'authenticated',
      user: response.user,
      accessToken: response.accessToken,
      pendingChallenge: null,
    });
  }

  clear(): void {
    this.mutableState.set({ ...INITIAL_STATE, status: 'anonymous' });
  }

  updateUser(user: PublicUser): void {
    this.mutableState.update((current) =>
      current.status === 'authenticated' && current.user?.id === user.id
        ? { ...current, user }
        : current,
    );
  }
}
