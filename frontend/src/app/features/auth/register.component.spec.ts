import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthStore } from '../../core/auth.store';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  const router = { navigate: vi.fn().mockResolvedValue(true) };
  const store = { register: vi.fn() };

  beforeEach(async () => {
    router.navigate.mockClear();
    store.register.mockReset();
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        { provide: AuthStore, useValue: store },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
  });

  function type(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('shows password policy guidance while the user types', () => {
    type('[data-testid="register-password"]', 'short');
    fixture.detectChanges();

    const policy = fixture.nativeElement.querySelector('[data-testid="password-policy"]');
    expect(policy.textContent).toContain('At least 10 characters');
    expect(policy.textContent).toContain('uppercase');
    expect(policy.textContent).toContain('lowercase');
    expect(policy.textContent).toContain('number');
    expect(policy.textContent).toContain('special character');
  });

  it('navigates to login with a success marker after registration', () => {
    store.register.mockReturnValue(
      of({
        id: 'user-1',
        email: 'alice@example.com',
        displayName: 'Alice',
        role: 'USER',
        twoFactorEnabled: false,
        createdAt: '2026-09-13T10:00:00Z',
      }),
    );
    type('[data-testid="register-email"]', 'alice@example.com');
    type('[data-testid="register-display-name"]', 'Alice');
    type('[data-testid="register-password"]', 'StrongPassword1!');
    type('[data-testid="register-confirm-password"]', 'StrongPassword1!');

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(store.register).toHaveBeenCalledWith({
      email: 'alice@example.com',
      displayName: 'Alice',
      password: 'StrongPassword1!',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { registered: true },
    });
  });
});
