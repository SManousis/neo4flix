export interface TotpSetupResponse {
  otpauthUri: string;
  qrCodeDataUrl: string;
  expiresAt: string;
}

export interface TotpCodeRequest {
  code: string;
}

export interface ReauthenticationRequest {
  password: string;
  code: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  code: string | null;
}
