import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { AuthApiService } from './auth-api.service';
import {
  AuthResponse,
  LoginResponse,
  PublicUser,
} from './auth.models';
import { AuthStore } from './auth.store';

const user: PublicUser = {
  id: 'user-1',
  email: 'alice@example.com',
  displayName: 'Alice',
  role: 'USER',
  twoFactorEnabled: false,
  createdAt: '2026-09-13T10:00:00Z',
};

const authenticated: AuthResponse = {
  accessToken: 'access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user,
};

class FakeAuthApiService {
  loginResponse: LoginResponse = authenticated;
  verifyResponse = authenticated;

  login(): Observable<LoginResponse> {
    return of(this.loginResponse);
  }

  register(): Observable<PublicUser> {
    return of(user);
  }

  verifyTwoFactor(): Observable<AuthResponse> {
    return of(this.verifyResponse);
  }

  refresh(): Observable<AuthResponse> {
    return of(authenticated);
  }

  logout(): Observable<void> {
    return of(undefined);
  }
}

describe('AuthStore', () => {
  let api: FakeAuthApiService;
  let store: AuthStore;

  beforeEach(() => {
    api = new FakeAuthApiService();
    TestBed.configureTestingModule({
      providers: [AuthStore, { provide: AuthApiService, useValue: api }],
    });
    store = TestBed.inject(AuthStore);
  });

  it('keeps an authenticated session in memory without writing browser storage', () => {
    const localWrite = vi.spyOn(window.localStorage, 'setItem');
    const sessionWrite = vi.spyOn(window.sessionStorage, 'setItem');

    store.login({ email: 'alice@example.com', password: 'StrongPassword1!' }).subscribe();

    expect(store.state()).toEqual({
      status: 'authenticated',
      user,
      accessToken: 'access-token',
      pendingChallenge: null,
    });
    expect(localWrite).not.toHaveBeenCalled();
    expect(sessionWrite).not.toHaveBeenCalled();
  });

  it('holds only the pending challenge when password login requires 2FA', () => {
    api.loginResponse = {
      requiresTwoFactor: true,
      challengeToken: 'challenge-token',
      expiresIn: 300,
    };

    store.login({ email: 'alice@example.com', password: 'StrongPassword1!' }).subscribe();

    expect(store.state()).toEqual({
      status: 'anonymous',
      user: null,
      accessToken: null,
      pendingChallenge: {
        challengeToken: 'challenge-token',
        expiresIn: 300,
      },
    });
  });

  it('consumes the pending challenge and stores the verified session', () => {
    api.loginResponse = {
      requiresTwoFactor: true,
      challengeToken: 'challenge-token',
      expiresIn: 300,
    };
    store.login({ email: 'alice@example.com', password: 'StrongPassword1!' }).subscribe();

    store.verifyTwoFactor('428193').subscribe();

    expect(store.state()).toEqual({
      status: 'authenticated',
      user,
      accessToken: 'access-token',
      pendingChallenge: null,
    });
  });

  it('does not replay a consumed two-factor challenge from client memory', () => {
    api.loginResponse = {
      requiresTwoFactor: true,
      challengeToken: 'challenge-token',
      expiresIn: 300,
    };
    store.login({ email: 'alice@example.com', password: 'StrongPassword1!' }).subscribe();
    store.verifyTwoFactor('428193').subscribe();
    const replayError = vi.fn();

    store.verifyTwoFactor('428193').subscribe({ error: replayError });

    expect(replayError).toHaveBeenCalledOnce();
    expect(replayError.mock.calls[0][0]).toEqual(new Error('No active two-factor challenge'));
  });
});
