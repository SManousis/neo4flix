import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PublicUser } from './auth.models';
import {
  ChangePasswordRequest,
  ReauthenticationRequest,
  TotpCodeRequest,
  TotpSetupResponse,
} from './totp.models';

export interface UpdateProfileRequest {
  displayName: string;
}

@Injectable({ providedIn: 'root' })
export class ProfileApiService {
  private readonly http = inject(HttpClient);
  private readonly profileUrl = '/api/v1/users/me';
  private readonly authUrl = '/api/v1/auth';

  getProfile(): Observable<PublicUser> {
    return this.http.get<PublicUser>(this.profileUrl);
  }

  updateProfile(request: UpdateProfileRequest): Observable<PublicUser> {
    return this.http.patch<PublicUser>(this.profileUrl, request);
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.authUrl}/change-password`, request);
  }

  setupTwoFactor(): Observable<TotpSetupResponse> {
    return this.http.post<TotpSetupResponse>(`${this.authUrl}/2fa/setup`, null);
  }

  confirmTwoFactor(request: TotpCodeRequest): Observable<void> {
    return this.http.post<void>(`${this.authUrl}/2fa/confirm`, request);
  }

  disableTwoFactor(request: ReauthenticationRequest): Observable<void> {
    return this.http.post<void>(`${this.authUrl}/2fa/disable`, request);
  }

  deleteAccount(request: ReauthenticationRequest): Observable<void> {
    return this.http.delete<void>(this.profileUrl, {
      body: request,
      withCredentials: true,
    });
  }
}
