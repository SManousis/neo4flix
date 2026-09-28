import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApiService } from './auth-api.service';

describe('AuthApiService', () => {
  let api: AuthApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuthApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the exact public registration contract without cookie credentials', () => {
    const request = {
      email: 'alice@example.com',
      displayName: 'Alice',
      password: 'StrongPassword1!',
    };

    api.register(request).subscribe();

    const outgoing = http.expectOne('/api/v1/auth/register');
    expect(outgoing.request.method).toBe('POST');
    expect(outgoing.request.body).toEqual(request);
    expect(outgoing.request.withCredentials).toBe(false);
    outgoing.flush({
      id: 'user-1',
      email: request.email,
      displayName: request.displayName,
      role: 'USER',
      twoFactorEnabled: false,
      createdAt: '2026-09-13T10:00:00Z',
    });
  });

  it('opts into cookie credentials only for session-issuing or cookie-consuming calls', () => {
    api.login({ email: 'alice@example.com', password: 'StrongPassword1!' }).subscribe();
    api.verifyTwoFactor({ challengeToken: 'a'.repeat(43), code: '428193' }).subscribe();
    api.refresh().subscribe();
    api.logout().subscribe();

    for (const path of ['login', '2fa/verify', 'refresh', 'logout']) {
      const outgoing = http.expectOne(`/api/v1/auth/${path}`);
      expect(outgoing.request.method).toBe('POST');
      expect(outgoing.request.withCredentials).toBe(true);
      outgoing.flush(path === 'logout' ? null : {});
    }
  });

  it('loads the current identity without cookie credentials', () => {
    api.me().subscribe();

    const outgoing = http.expectOne('/api/v1/auth/me');
    expect(outgoing.request.method).toBe('GET');
    expect(outgoing.request.withCredentials).toBe(false);
    outgoing.flush({});
  });
});
