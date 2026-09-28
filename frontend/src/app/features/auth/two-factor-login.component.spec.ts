import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { AuthStore } from '../../core/auth.store';
import { TwoFactorLoginComponent } from './two-factor-login.component';

describe('TwoFactorLoginComponent', () => {
  let fixture: ComponentFixture<TwoFactorLoginComponent>;
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };
  const pendingChallenge = signal<{ challengeToken: string; expiresIn: number } | null>({
    challengeToken: 'challenge-token',
    expiresIn: 300,
  });
  const store = {
    pendingChallenge,
    verifyTwoFactor: vi.fn(),
    clear: vi.fn(),
  };

  async function createComponent(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TwoFactorLoginComponent],
      providers: [
        { provide: AuthStore, useValue: store },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TwoFactorLoginComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    router.navigateByUrl.mockClear();
    store.verifyTwoFactor.mockReset();
    store.clear.mockReset();
    pendingChallenge.set({ challengeToken: 'challenge-token', expiresIn: 300 });
  });

  it('returns direct navigation without an in-memory challenge to login', async () => {
    pendingChallenge.set(null);

    await createComponent();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/login');
  });

  it('accepts exactly six digits and routes a verified session home', async () => {
    store.verifyTwoFactor.mockReturnValue(of({}));
    await createComponent();
    const code = fixture.nativeElement.querySelector('[data-testid="two-factor-code"]');
    code.value = '428193';
    code.dispatchEvent(new Event('input'));

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(store.verifyTwoFactor).toHaveBeenCalledWith('428193');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    expect(fixture.nativeElement.textContent).not.toContain('Resend');
  });

  it('shows a generic expired-or-invalid message without exposing challenge details', async () => {
    store.verifyTwoFactor.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' })),
    );
    await createComponent();
    const code = fixture.nativeElement.querySelector('[data-testid="two-factor-code"]');
    code.value = '428193';
    code.dispatchEvent(new Event('input'));

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('The code or challenge is invalid or expired.');
    expect(fixture.nativeElement.textContent).not.toContain('challenge-token');
  });
});
