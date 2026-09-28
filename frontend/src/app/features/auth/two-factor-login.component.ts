import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthStore } from '../../core/auth.store';

@Component({
  selector: 'app-two-factor-login',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './two-factor-login.component.html',
  styleUrl: './two-factor-login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TwoFactorLoginComponent {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  constructor() {
    if (!this.store.pendingChallenge()) {
      void this.router.navigateByUrl('/auth/login');
    }
  }

  protected submit(): void {
    if (!this.store.pendingChallenge()) {
      void this.router.navigateByUrl('/auth/login');
      return;
    }
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);
    this.store
      .verifyTwoFactor(this.form.controls.code.value)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/'),
        error: (error: unknown) => {
          this.errorMessage.set(
            error instanceof HttpErrorResponse && error.status === 429
              ? 'Too many attempts. Please wait and try again.'
              : 'The code or challenge is invalid or expired.',
          );
        },
      });
  }

  protected cancel(): void {
    this.store.clear();
    void this.router.navigateByUrl('/auth/login');
  }
}
