import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { isTwoFactorChallenge } from '../../core/auth.models';
import { AuthStore } from '../../core/auth.store';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly registered = this.route.snapshot.queryParamMap.get('registered') === 'true';
  protected readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);
    this.store
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (response) => {
          if (isTwoFactorChallenge(response)) {
            void this.router.navigateByUrl('/auth/2fa');
            return;
          }
          const requested = this.route.snapshot.queryParamMap.get('returnUrl');
          const destination = requested?.startsWith('/') && !requested.startsWith('//') ? requested : '/';
          void this.router.navigateByUrl(destination);
        },
        error: (error: unknown) => this.errorMessage.set(this.messageFor(error)),
      });
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 429) {
      return 'Too many attempts. Please wait and try again.';
    }
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Email or password is incorrect.';
    }
    return 'Sign in is temporarily unavailable. Please try again.';
  }
}
