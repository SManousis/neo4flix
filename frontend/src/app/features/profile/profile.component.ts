import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NavigationStart, Router, RouterLink } from '@angular/router';
import { filter, finalize, Subject, takeUntil } from 'rxjs';
import { AuthStore } from '../../core/auth.store';
import { PublicUser } from '../../core/auth.models';
import { ProfileApiService } from '../../core/profile-api.service';
import { RatingApiService } from '../../core/rating-api.service';
import { RatingHistoryEntry, RatingPage } from '../../core/rating.models';
import { TotpSetupResponse } from '../../core/totp.models';

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

function newPasswordsMatch(control: AbstractControl) {
  return control.get('newPassword')?.value === control.get('confirmation')?.value
    ? null
    : { passwordMismatch: true };
}

@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent implements OnInit {
  private readonly api = inject(ProfileApiService);
  private readonly ratingApi = inject(RatingApiService);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly navigationStarted = new Subject<void>();

  protected readonly profile = signal<PublicUser | null>(null);
  protected readonly enrollment = signal<TotpSetupResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly profileBusy = signal(false);
  protected readonly passwordBusy = signal(false);
  protected readonly twoFactorBusy = signal(false);
  protected readonly deletionBusy = signal(false);
  protected readonly profileStatus = signal<string | null>(null);
  protected readonly profileError = signal<string | null>(null);
  protected readonly securityStatus = signal<string | null>(null);
  protected readonly securityError = signal<string | null>(null);
  protected readonly accountError = signal<string | null>(null);
  protected readonly ratingHistory = signal<RatingPage | null>(null);
  protected readonly ratingsLoading = signal(true);
  protected readonly ratingsError = signal<string | null>(null);
  protected readonly ratingsBusy = signal<string | null>(null);

  protected readonly profileForm = this.formBuilder.nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(100)]],
    email: [''],
  });
  protected readonly passwordForm = this.formBuilder.nonNullable.group(
    {
      currentPassword: ['', [Validators.required, Validators.maxLength(128)]],
      newPassword: ['', [Validators.required, satisfiesPasswordPolicy]],
      confirmation: ['', Validators.required],
      code: ['', Validators.pattern(/^\d{6}$/)],
    },
    { validators: newPasswordsMatch },
  );
  protected readonly confirmTotpForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });
  protected readonly disableTwoFactorForm = this.formBuilder.nonNullable.group({
    password: ['', [Validators.required, Validators.maxLength(128)]],
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });
  protected readonly deleteAccountForm = this.formBuilder.nonNullable.group({
    password: ['', [Validators.required, Validators.maxLength(128)]],
    code: ['', Validators.pattern(/^\d{6}$/)],
    confirmation: ['', [Validators.required, Validators.pattern(/^DELETE$/)]],
  });

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationStart => event instanceof NavigationStart),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.clearEnrollment();
        this.navigationStarted.next();
      });
    this.destroyRef.onDestroy(() => {
      this.clearEnrollment();
      this.navigationStarted.next();
      this.navigationStarted.complete();
    });
  }

  ngOnInit(): void {
    this.loadProfile();
    this.loadRatings();
  }

  protected removeRating(entry: RatingHistoryEntry): void {
    if (this.ratingsBusy()) return;
    this.ratingsBusy.set(entry.movieId);
    this.ratingApi.remove(entry.movieId).pipe(finalize(() => this.ratingsBusy.set(null))).subscribe({
      next: () => this.ratingHistory.update((page) => page
        ? { ...page, content: page.content.filter((item) => item.movieId !== entry.movieId), totalElements: Math.max(0, page.totalElements - 1) }
        : page),
      error: () => this.ratingsError.set('Unable to remove this rating. Please try again.'),
    });
  }

  protected saveProfile(): void {
    if (this.profileForm.invalid || this.profileBusy()) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.profileStatus.set(null);
    this.profileError.set(null);
    this.profileBusy.set(true);
    this.api
      .updateProfile({ displayName: this.profileForm.controls.displayName.value })
      .pipe(finalize(() => this.profileBusy.set(false)))
      .subscribe({
        next: (updated) => {
          this.profile.set(updated);
          this.authStore.updateUser(updated);
          this.profileStatus.set('Profile updated.');
        },
        error: (error: unknown) => this.profileError.set(this.mapError(error, 'profile')),
      });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid || this.passwordBusy()) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const value = this.passwordForm.getRawValue();
    this.clearSecurityFeedback();
    this.passwordBusy.set(true);
    this.api
      .changePassword({
        currentPassword: value.currentPassword,
        newPassword: value.newPassword,
        code: value.code || null,
      })
      .pipe(finalize(() => this.passwordBusy.set(false)))
      .subscribe({
        next: () => {
          this.passwordForm.reset();
          this.securityStatus.set('Password changed.');
        },
        error: (error: unknown) => this.securityError.set(this.mapError(error, 'password')),
      });
  }

  protected setupTwoFactor(): void {
    if (this.twoFactorBusy()) {
      return;
    }
    this.clearEnrollment();
    this.clearSecurityFeedback();
    this.twoFactorBusy.set(true);
    this.api
      .setupTwoFactor()
      .pipe(
        takeUntil(this.navigationStarted),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.twoFactorBusy.set(false)),
      )
      .subscribe({
        next: (setup) => this.enrollment.set(setup),
        error: (error: unknown) => this.securityError.set(this.mapError(error, '2FA setup')),
      });
  }

  protected confirmTwoFactor(): void {
    if (!this.enrollment() || this.confirmTotpForm.invalid || this.twoFactorBusy()) {
      this.confirmTotpForm.markAllAsTouched();
      return;
    }
    this.clearSecurityFeedback();
    this.twoFactorBusy.set(true);
    this.api
      .confirmTwoFactor({ code: this.confirmTotpForm.controls.code.value })
      .pipe(finalize(() => this.twoFactorBusy.set(false)))
      .subscribe({
        next: () => {
          this.profile.update((current) =>
            current ? { ...current, twoFactorEnabled: true } : current,
          );
          this.configureTwoFactorRequirements(true);
          this.confirmTotpForm.reset();
          this.clearEnrollment();
          this.securityStatus.set('Two-factor authentication enabled.');
        },
        error: (error: unknown) => this.securityError.set(this.mapError(error, '2FA code')),
      });
  }

  protected cancelEnrollment(): void {
    this.confirmTotpForm.reset();
    this.clearEnrollment();
  }

  protected disableTwoFactor(): void {
    if (this.disableTwoFactorForm.invalid || this.twoFactorBusy()) {
      this.disableTwoFactorForm.markAllAsTouched();
      return;
    }
    const value = this.disableTwoFactorForm.getRawValue();
    this.clearSecurityFeedback();
    this.twoFactorBusy.set(true);
    this.api
      .disableTwoFactor({ password: value.password, code: value.code })
      .pipe(finalize(() => this.twoFactorBusy.set(false)))
      .subscribe({
        next: () => {
          this.profile.update((current) =>
            current ? { ...current, twoFactorEnabled: false } : current,
          );
          this.configureTwoFactorRequirements(false);
          this.disableTwoFactorForm.reset();
          this.securityStatus.set('Two-factor authentication disabled.');
        },
        error: (error: unknown) => this.securityError.set(this.mapError(error, '2FA')),
      });
  }

  protected deleteAccount(): void {
    if (this.deleteAccountForm.invalid || this.deletionBusy()) {
      this.deleteAccountForm.markAllAsTouched();
      return;
    }
    const value = this.deleteAccountForm.getRawValue();
    this.accountError.set(null);
    this.deletionBusy.set(true);
    this.api
      .deleteAccount({ password: value.password, code: value.code || null })
      .pipe(finalize(() => this.deletionBusy.set(false)))
      .subscribe({
        next: () => {
          this.clearEnrollment();
          this.deleteAccountForm.reset();
          this.authStore.clear();
          void this.router.navigateByUrl('/auth/login');
        },
        error: (error: unknown) => this.accountError.set(this.mapError(error, 'account')),
      });
  }

  private loadProfile(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .getProfile()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.configureTwoFactorRequirements(profile.twoFactorEnabled);
          this.profileForm.setValue({ displayName: profile.displayName, email: profile.email });
        },
        error: (error: unknown) => this.loadError.set(this.mapError(error, 'profile')),
      });
  }

  private loadRatings(): void {
    this.ratingsLoading.set(true);
    this.ratingsError.set(null);
    this.ratingApi.history().pipe(finalize(() => this.ratingsLoading.set(false))).subscribe({
      next: (history) => this.ratingHistory.set(history),
      error: () => this.ratingsError.set('Unable to load rating history. Please try again.'),
    });
  }

  private clearEnrollment(): void {
    this.enrollment.set(null);
  }

  private clearSecurityFeedback(): void {
    this.securityStatus.set(null);
    this.securityError.set(null);
  }

  private configureTwoFactorRequirements(enabled: boolean): void {
    const validators = enabled
      ? [Validators.required, Validators.pattern(/^\d{6}$/)]
      : [Validators.pattern(/^\d{6}$/)];
    this.passwordForm.controls.code.setValidators(validators);
    this.deleteAccountForm.controls.code.setValidators(validators);
    this.passwordForm.controls.code.updateValueAndValidity();
    this.deleteAccountForm.controls.code.updateValueAndValidity();
  }

  private mapError(error: unknown, action: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return `Unable to update ${action}. Please try again.`;
    }
    switch (error.status) {
      case 400:
        return 'Check the entered values and try again.';
      case 401:
        return 'Reauthentication failed. Check your password and authentication code.';
      case 409:
        return `Unable to update ${action} in its current state.`;
      case 429:
        return 'Too many attempts. Please wait and try again.';
      case 500:
      case 503:
        return 'The service is temporarily unavailable. Please try again later.';
      default:
        return `Unable to update ${action}. Please try again.`;
    }
  }
}
