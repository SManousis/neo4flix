import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuthResponse,
  LoginRequest,
  LoginResponse,
  PublicUser,
  RegisterRequest,
  TwoFactorVerifyRequest,
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/auth';

  register(request: RegisterRequest): Observable<PublicUser> {
    return this.http.post<PublicUser>(`${this.baseUrl}/register`, request);
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, request, {
      withCredentials: true,
    });
  }

  verifyTwoFactor(request: TwoFactorVerifyRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/2fa/verify`, request, {
      withCredentials: true,
    });
  }

  refresh(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/refresh`, null, {
      withCredentials: true,
    });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, null, {
      withCredentials: true,
    });
  }

  me(): Observable<PublicUser> {
    return this.http.get<PublicUser>(`${this.baseUrl}/me`);
  }
}
