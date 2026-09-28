export type UserRole = 'USER' | 'ADMIN';

export interface PublicUser {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  twoFactorEnabled: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: PublicUser;
}

export interface TwoFactorChallengeResponse {
  requiresTwoFactor: true;
  challengeToken: string;
  expiresIn: number;
}

export type LoginResponse = AuthResponse | TwoFactorChallengeResponse;

export interface PendingChallenge {
  challengeToken: string;
  expiresIn: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest extends LoginRequest {
  displayName: string;
}

export interface TwoFactorVerifyRequest {
  challengeToken: string;
  code: string;
}

export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  traceId?: string;
  fieldErrors?: Record<string, string>;
}

export type AuthStatus = 'idle' | 'bootstrapping' | 'anonymous' | 'authenticated';

export interface AuthState {
  status: AuthStatus;
  user: PublicUser | null;
  accessToken: string | null;
  pendingChallenge: PendingChallenge | null;
}

export function isTwoFactorChallenge(
  response: LoginResponse,
): response is TwoFactorChallengeResponse {
  return 'requiresTwoFactor' in response && response.requiresTwoFactor;
}
