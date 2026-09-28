import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProfileApiService } from './profile-api.service';

describe('ProfileApiService', () => {
  let api: ProfileApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProfileApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ProfileApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the current-user profile endpoints and sends only editable profile data', () => {
    api.getProfile().subscribe();
    api.updateProfile({ displayName: 'Alice Updated' }).subscribe();

    const getRequest = http.expectOne(
      (request) => request.url === '/api/v1/users/me' && request.method === 'GET',
    );
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush({});

    const patchRequest = http.expectOne(
      (request) => request.url === '/api/v1/users/me' && request.method === 'PATCH',
    );
    expect(patchRequest.request.method).toBe('PATCH');
    expect(patchRequest.request.body).toEqual({ displayName: 'Alice Updated' });
    patchRequest.flush({});
  });

  it('uses the exact password and two-factor contracts without cookie credentials', () => {
    api
      .changePassword({
        currentPassword: 'OldPassword1!',
        newPassword: 'NewPassword1!',
        code: '428193',
      })
      .subscribe();
    api.setupTwoFactor().subscribe();
    api.confirmTwoFactor({ code: '428193' }).subscribe();
    api.disableTwoFactor({ password: 'Password1!', code: '428193' }).subscribe();

    const expected = [
      ['change-password', { currentPassword: 'OldPassword1!', newPassword: 'NewPassword1!', code: '428193' }],
      ['2fa/setup', null],
      ['2fa/confirm', { code: '428193' }],
      ['2fa/disable', { password: 'Password1!', code: '428193' }],
    ] as const;

    for (const [path, body] of expected) {
      const outgoing = http.expectOne(`/api/v1/auth/${path}`);
      expect(outgoing.request.method).toBe('POST');
      expect(outgoing.request.body).toEqual(body);
      expect(outgoing.request.withCredentials).toBe(false);
      outgoing.flush(path === '2fa/setup' ? {} : null);
    }
  });

  it('reauthenticates account deletion and opts into clearing the refresh cookie', () => {
    api.deleteAccount({ password: 'Password1!', code: '428193' }).subscribe();

    const outgoing = http.expectOne('/api/v1/users/me');
    expect(outgoing.request.method).toBe('DELETE');
    expect(outgoing.request.body).toEqual({ password: 'Password1!', code: '428193' });
    expect(outgoing.request.withCredentials).toBe(true);
    outgoing.flush(null);
  });
});
