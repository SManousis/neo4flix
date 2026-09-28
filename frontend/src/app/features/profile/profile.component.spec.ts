import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, NavigationStart, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { AuthStore } from '../../core/auth.store';
import { AuthApiService } from '../../core/auth-api.service';
import { PublicUser } from '../../core/auth.models';
import { ProfileApiService } from '../../core/profile-api.service';
import { RatingApiService } from '../../core/rating-api.service';
import { ProfileComponent } from './profile.component';
import { AppComponent } from '../../app.component';

const user: PublicUser = {
  id: 'user-1',
  email: 'alice@example.com',
  displayName: 'Alice',
  role: 'USER',
  twoFactorEnabled: false,
  createdAt: '2026-09-13T10:00:00Z',
};

describe('ProfileComponent', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  const routerEvents = new Subject<NavigationStart>();
  const router = {
    events: routerEvents.asObservable(),
    navigateByUrl: vi.fn().mockResolvedValue(true),
    createUrlTree: vi.fn((commands: string[]) => commands),
    serializeUrl: vi.fn((commands: string[]) => commands[0]),
  };
  const api = {
    getProfile: vi.fn(),
    updateProfile: vi.fn(),
    changePassword: vi.fn(),
    setupTwoFactor: vi.fn(),
    confirmTwoFactor: vi.fn(),
    disableTwoFactor: vi.fn(),
    deleteAccount: vi.fn(),
  };
  const ratingApi = {
    history: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 24, totalElements: 0, totalPages: 0 })),
    remove: vi.fn(),
  };
  let authStore: AuthStore;

  beforeEach(async () => {
    Object.values(api).forEach((mock) => mock.mockReset());
    Object.values(ratingApi).forEach((mock) => mock.mockReset());
    api.getProfile.mockReturnValue(of(user));
    ratingApi.history.mockReturnValue(of({ content: [], page: 0, size: 24, totalElements: 0, totalPages: 0 }));
    router.navigateByUrl.mockClear();
    await TestBed.configureTestingModule({
      imports: [ProfileComponent, AppComponent],
      providers: [
        { provide: ProfileApiService, useValue: api },
        { provide: RatingApiService, useValue: ratingApi },
        { provide: AuthApiService, useValue: {} },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();
    authStore = TestBed.inject(AuthStore);
    authStore.acceptAuthenticatedSession({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 900, user });
    vi.spyOn(authStore, 'clear');
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
  });

  function type(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(selector: string): void {
    (fixture.nativeElement.querySelector(selector) as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function recreateComponent(): void {
    fixture.destroy();
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
  }

  function expectAccessibleError(message: string): void {
    const alert = fixture.nativeElement.querySelector('[role="alert"].error') as HTMLElement | null;
    expect(alert?.textContent).toContain(message);
    expect(fixture.nativeElement.querySelector('[role="status"].error')).toBeNull();
  }

  function expectFieldError(inputSelector: string, message: string): void {
    const input = fixture.nativeElement.querySelector(inputSelector) as HTMLInputElement;
    expect(input.closest('mat-form-field')?.textContent).toContain(message);
  }

  it('announces profile loading, empty, and load-error states accessibly', () => {
    const pendingProfile = new Subject<PublicUser>();
    api.getProfile.mockReturnValue(pendingProfile.asObservable());
    recreateComponent();

    expect(
      fixture.nativeElement.querySelector('[role="status"][aria-label="Loading profile"]'),
    ).not.toBeNull();

    pendingProfile.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty[role="status"]')?.textContent).toContain(
      'Profile information is unavailable.',
    );

    api.getProfile.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    recreateComponent();
    expectAccessibleError('The service is temporarily unavailable.');
  });

  it('renders profile edit API failures as errors instead of success statuses', () => {
    api.updateProfile.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    type('[data-testid="profile-display-name"]', 'Alice Updated');
    submit('[data-testid="profile-form"]');

    expectAccessibleError('Unable to update profile in its current state.');
    expect(fixture.nativeElement.querySelector('[role="status"].status')).toBeNull();
  });

  it('renders password-change API failures in the security error region', () => {
    api.changePassword.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );
    type('[data-testid="current-password"]', 'OldPassword1!');
    type('[data-testid="new-password"]', 'NewPassword1!');
    type('[data-testid="confirm-new-password"]', 'NewPassword1!');
    submit('[data-testid="password-form"]');

    expectAccessibleError('Reauthentication failed.');
    expect(fixture.nativeElement.querySelector('[role="status"].status')).toBeNull();
  });

  it('renders TOTP setup and confirmation API failures in the security error region', () => {
    api.setupTwoFactor.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    (fixture.nativeElement.querySelector('[data-testid="setup-2fa"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expectAccessibleError('The service is temporarily unavailable.');

    api.setupTwoFactor.mockReturnValue(
      of({
        otpauthUri: 'otpauth://totp/Neo4flix:alice?secret=MEMORYONLY',
        qrCodeDataUrl: 'data:image/png;base64,cXItY29kZQ==',
        expiresAt: '2026-09-13T10:10:00Z',
      }),
    );
    api.confirmTwoFactor.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400 })),
    );
    (fixture.nativeElement.querySelector('[data-testid="setup-2fa"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    type('[data-testid="confirm-totp-code"]', '428193');
    submit('[data-testid="confirm-totp-form"]');

    expectAccessibleError('Check the entered values and try again.');
    expect(fixture.nativeElement.querySelector('[role="status"].status')).toBeNull();
  });

  it('renders TOTP disable API failures in the security error region', () => {
    api.getProfile.mockReturnValue(of({ ...user, twoFactorEnabled: true }));
    recreateComponent();
    api.disableTwoFactor.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 429 })),
    );
    type('[data-testid="disable-password"]', 'Password1!');
    type('[data-testid="disable-totp-code"]', '428193');
    submit('[data-testid="disable-2fa-form"]');

    expectAccessibleError('Too many attempts.');
    expect(fixture.nativeElement.querySelector('[role="status"].status')).toBeNull();
  });

  it('renders deletion API failures in an accessible account error region', () => {
    api.deleteAccount.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    type('[data-testid="delete-password"]', 'Password1!');
    type('[data-testid="delete-confirmation"]', 'DELETE');
    submit('[data-testid="delete-account-form"]');

    expectAccessibleError('The service is temporarily unavailable.');
    expect(authStore.clear).not.toHaveBeenCalled();
  });

  it('associates validation output with password and enrollment fields', () => {
    submit('[data-testid="password-form"]');
    expectFieldError('[data-testid="current-password"]', 'Enter your current password.');
    expectFieldError('[data-testid="new-password"]', 'Use 10–128 characters');
    expectFieldError('[data-testid="confirm-new-password"]', 'Confirm your new password.');

    api.setupTwoFactor.mockReturnValue(
      of({
        otpauthUri: 'otpauth://totp/Neo4flix:alice?secret=MEMORYONLY',
        qrCodeDataUrl: 'data:image/png;base64,cXItY29kZQ==',
        expiresAt: '2026-09-13T10:10:00Z',
      }),
    );
    (fixture.nativeElement.querySelector('[data-testid="setup-2fa"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    submit('[data-testid="confirm-totp-form"]');
    expectFieldError('[data-testid="confirm-totp-code"]', 'Enter exactly six digits.');
  });

  it('associates validation output with active-2FA and deletion reauthentication fields', () => {
    api.getProfile.mockReturnValue(of({ ...user, twoFactorEnabled: true }));
    recreateComponent();

    submit('[data-testid="password-form"]');
    expectFieldError('[data-testid="password-totp-code"]', 'Enter exactly six digits.');

    submit('[data-testid="disable-2fa-form"]');
    expectFieldError('[data-testid="disable-password"]', 'Enter your current password.');
    expectFieldError('[data-testid="disable-totp-code"]', 'Enter exactly six digits.');

    submit('[data-testid="delete-account-form"]');
    expectFieldError('[data-testid="delete-password"]', 'Enter your current password.');
    expectFieldError('[data-testid="delete-totp-code"]', 'Enter exactly six digits.');
    expectFieldError('[data-testid="delete-confirmation"]', 'Type DELETE exactly to confirm.');
  });

  it('loads profile data, keeps email read-only, and saves only the display name', () => {
    const shell = TestBed.createComponent(AppComponent);
    shell.detectChanges();
    api.updateProfile.mockReturnValue(of({ ...user, displayName: 'Alice Updated' }));
    const email = fixture.nativeElement.querySelector('[data-testid="profile-email"]') as HTMLInputElement;
    expect(email.readOnly).toBe(true);
    expect(email.value).toBe('alice@example.com');

    type('[data-testid="profile-display-name"]', 'Alice Updated');
    submit('[data-testid="profile-form"]');

    expect(api.updateProfile).toHaveBeenCalledWith({ displayName: 'Alice Updated' });
    expect(authStore.user()?.displayName).toBe('Alice Updated');
    expect(authStore.accessToken()).toBe('token');
    shell.detectChanges();
    expect(shell.nativeElement.querySelector('mat-toolbar').textContent).toContain('Alice Updated');
    expect(fixture.nativeElement.textContent).toContain('Profile updated.');
  });

  it('changes the password with reauthentication and clears sensitive fields', () => {
    api.changePassword.mockReturnValue(of(undefined));
    type('[data-testid="current-password"]', 'OldPassword1!');
    type('[data-testid="new-password"]', 'NewPassword1!');
    type('[data-testid="confirm-new-password"]', 'NewPassword1!');
    submit('[data-testid="password-form"]');

    expect(api.changePassword).toHaveBeenCalledWith({
      currentPassword: 'OldPassword1!',
      newPassword: 'NewPassword1!',
      code: null,
    });
    expect((fixture.nativeElement.querySelector('[data-testid="current-password"]') as HTMLInputElement).value).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Password changed.');
  });

  it('keeps 2FA enrollment in memory and clears it after confirmation or navigation', () => {
    const localWrite = vi.spyOn(window.localStorage, 'setItem');
    const sessionWrite = vi.spyOn(window.sessionStorage, 'setItem');
    api.setupTwoFactor.mockReturnValue(
      of({
        otpauthUri: 'otpauth://totp/Neo4flix:alice?secret=MEMORYONLY',
        qrCodeDataUrl: 'data:image/png;base64,cXItY29kZQ==',
        expiresAt: '2026-09-13T10:10:00Z',
      }),
    );
    api.confirmTwoFactor.mockReturnValue(of(undefined));

    (fixture.nativeElement.querySelector('[data-testid="setup-2fa"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="totp-enrollment"]')).not.toBeNull();

    type('[data-testid="confirm-totp-code"]', '428193');
    submit('[data-testid="confirm-totp-form"]');
    expect(api.confirmTwoFactor).toHaveBeenCalledWith({ code: '428193' });
    expect(fixture.nativeElement.querySelector('[data-testid="totp-enrollment"]')).toBeNull();
    expect(localWrite).not.toHaveBeenCalled();
    expect(sessionWrite).not.toHaveBeenCalled();

    fixture.destroy();
    api.getProfile.mockReturnValue(of(user));
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    api.setupTwoFactor.mockReturnValue(of({ otpauthUri: 'secret', qrCodeDataUrl: 'data:image/png;base64,eA==', expiresAt: 'later' }));
    (fixture.nativeElement.querySelector('[data-testid="setup-2fa"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    routerEvents.next(new NavigationStart(1, '/watchlist'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="totp-enrollment"]')).toBeNull();
    localWrite.mockRestore();
    sessionWrite.mockRestore();
  });

  it('does not restore enrollment data when a setup response arrives after navigation starts', () => {
    const delayedSetup = new Subject<{
      otpauthUri: string;
      qrCodeDataUrl: string;
      expiresAt: string;
    }>();
    api.setupTwoFactor.mockReturnValue(delayedSetup.asObservable());

    (fixture.nativeElement.querySelector('[data-testid="setup-2fa"]') as HTMLButtonElement).click();
    routerEvents.next(new NavigationStart(2, '/auth/login'));
    delayedSetup.next({
      otpauthUri: 'otpauth://totp/Neo4flix:alice?secret=SHOULDNOTRETURN',
      qrCodeDataUrl: 'data:image/png;base64,eA==',
      expiresAt: 'later',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="totp-enrollment"]')).toBeNull();
  });

  it('disables 2FA only with password and a six-digit code', () => {
    api.getProfile.mockReturnValue(of({ ...user, twoFactorEnabled: true }));
    fixture.destroy();
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    api.disableTwoFactor.mockReturnValue(of(undefined));

    type('[data-testid="disable-password"]', 'Password1!');
    type('[data-testid="disable-totp-code"]', '428193');
    submit('[data-testid="disable-2fa-form"]');

    expect(api.disableTwoFactor).toHaveBeenCalledWith({ password: 'Password1!', code: '428193' });
    expect(fixture.nativeElement.textContent).toContain('Two-factor authentication disabled.');
  });

  it('requires the current 2FA code for password changes and deletion when 2FA is enabled', () => {
    api.getProfile.mockReturnValue(of({ ...user, twoFactorEnabled: true }));
    fixture.destroy();
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    api.changePassword.mockReturnValue(of(undefined));
    api.deleteAccount.mockReturnValue(of(undefined));

    type('[data-testid="current-password"]', 'OldPassword1!');
    type('[data-testid="new-password"]', 'NewPassword1!');
    type('[data-testid="confirm-new-password"]', 'NewPassword1!');
    submit('[data-testid="password-form"]');
    expect(api.changePassword).not.toHaveBeenCalled();

    type('[data-testid="password-totp-code"]', '428193');
    submit('[data-testid="password-form"]');
    expect(api.changePassword).toHaveBeenCalledWith({
      currentPassword: 'OldPassword1!',
      newPassword: 'NewPassword1!',
      code: '428193',
    });

    type('[data-testid="delete-password"]', 'Password1!');
    type('[data-testid="delete-confirmation"]', 'DELETE');
    submit('[data-testid="delete-account-form"]');
    expect(api.deleteAccount).not.toHaveBeenCalled();

    type('[data-testid="delete-totp-code"]', '428193');
    submit('[data-testid="delete-account-form"]');
    expect(api.deleteAccount).toHaveBeenCalledWith({ password: 'Password1!', code: '428193' });
  });

  it('requires typed confirmation and reauthentication before deleting the account', () => {
    api.deleteAccount.mockReturnValue(of(undefined));
    type('[data-testid="delete-password"]', 'Password1!');
    type('[data-testid="delete-confirmation"]', 'not delete');
    submit('[data-testid="delete-account-form"]');
    expect(api.deleteAccount).not.toHaveBeenCalled();

    type('[data-testid="delete-confirmation"]', 'DELETE');
    submit('[data-testid="delete-account-form"]');

    expect(api.deleteAccount).toHaveBeenCalledWith({ password: 'Password1!', code: null });
    expect(authStore.clear).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/login');
  });

  it('provides links to rating history and the watchlist', () => {
    const ratingLink = fixture.nativeElement.querySelector('[data-testid="ratings-link"]') as HTMLAnchorElement;
    const watchlistLink = fixture.nativeElement.querySelector('[data-testid="watchlist-link"]') as HTMLAnchorElement;

    expect(ratingLink.getAttribute('href')).toBe('/profile#ratings');
    expect(watchlistLink.getAttribute('href')).toBe('/watchlist');
  });

  it('renders rating history and removes an entry through the shared rating API', () => {
    ratingApi.history.mockReturnValue(of({
      content: [{ movieId: 'movie-1', movieTitle: 'Arrival', score: 5, createdAt: '', updatedAt: '' }],
      page: 0, size: 24, totalElements: 1, totalPages: 1,
    }));
    ratingApi.remove.mockReturnValue(of(void 0));
    recreateComponent();

    expect(fixture.nativeElement.querySelector('[data-testid="rating-history"]')?.textContent).toContain('Arrival');
    (fixture.nativeElement.querySelector('[data-testid="rating-history"] button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(ratingApi.remove).toHaveBeenCalledWith('movie-1');
    expect(fixture.nativeElement.querySelector('[data-testid="rating-history"]')).toBeNull();
  });
});
