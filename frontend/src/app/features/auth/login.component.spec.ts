import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { AuthStore } from '../../core/auth.store';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };
  const store = { login: vi.fn() };

  beforeEach(async () => {
    router.navigateByUrl.mockClear();
    store.login.mockReset();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthStore, useValue: store },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  });

  function enterCredentials(): void {
    const email = fixture.nativeElement.querySelector('[data-testid="login-email"]');
    const password = fixture.nativeElement.querySelector('[data-testid="login-password"]');
    email.value = 'alice@example.com';
    email.dispatchEvent(new Event('input'));
    password.value = 'StrongPassword1!';
    password.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('shows the same generic message for an invalid credential response', () => {
    store.login.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' })),
    );
    enterCredentials();

    submit();

    expect(fixture.nativeElement.textContent).toContain('Email or password is incorrect.');
    expect(fixture.nativeElement.textContent).not.toContain('account does not exist');
  });

  it('shows a rate-limit message and disables duplicate submissions while loading', () => {
    const pending = new Subject<never>();
    store.login.mockReturnValue(pending);
    enterCredentials();
    submit();

    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);

    pending.error(new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Too many attempts. Please wait and try again.');
  });

  it('routes a challenge-only login to the 2FA page without treating it as authenticated', () => {
    store.login.mockReturnValue(
      of({ requiresTwoFactor: true, challengeToken: 'challenge-token', expiresIn: 300 }),
    );
    enterCredentials();

    submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/2fa');
  });
});
