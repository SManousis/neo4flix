import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthStore } from '../../core/auth.store';

function satisfiesPasswordPolicy(control: AbstractControl) {
  const password = String(control.value ?? '');
  const valid =
    password.length >= 10 &&
    password.length <= 128 &&
    /\p{Lu}/u.test(password) &&
    /\p{Ll}/u.test(password) &&
    /\p{Nd}/u.test(password) &&
    /[^\p{L}\p{N}]/u.test(password) &&
    !/[\s\p{Cc}]/u.test(password);
  return valid ? null : { passwordPolicy: true };
}

function passwordsMatch(control: AbstractControl) {
  const password = control.get('password')?.value;
  const confirmation = control.get('confirmPassword')?.value;
  return password === confirmation ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.formBuilder.nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      displayName: ['', [Validators.required, Validators.maxLength(100)]],
      password: ['', [Validators.required, satisfiesPasswordPolicy]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected get password(): string {
    return this.form.controls.password.value;
  }

  protected hasUppercase(): boolean {
    return /\p{Lu}/u.test(this.password);
  }

  protected hasLowercase(): boolean {
    return /\p{Ll}/u.test(this.password);
  }

  protected hasNumber(): boolean {
    return /\p{Nd}/u.test(this.password);
  }

  protected hasSpecialCharacter(): boolean {
    return /[^\p{L}\p{N}]/u.test(this.password) && !/[\s\p{Cc}]/u.test(this.password);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, displayName, password } = this.form.getRawValue();
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.store
      .register({ email, displayName, password })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          void this.router.navigate(['/auth/login'], { queryParams: { registered: true } });
        },
        error: (error: unknown) => {
          this.errorMessage.set(
            error instanceof HttpErrorResponse && error.status === 429
              ? 'Too many attempts. Please wait and try again.'
              : 'Unable to create an account with those details.',
          );
        },
      });
  }
}
