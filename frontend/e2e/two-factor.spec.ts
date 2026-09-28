import { createHmac } from 'node:crypto';
import { expect, test } from '@playwright/test';

const password = 'Valid-password-123';

type AuthResponse = Record<string, unknown> & { accessToken: string };

function decodeBase32(value: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = 0;
  let buffer = 0;
  const bytes: number[] = [];
  for (const character of value.replace(/=+$/, '').toUpperCase()) {
    const index = alphabet.indexOf(character);
    if (index < 0) throw new Error('Invalid TOTP secret');
    buffer = (buffer << 5) | index;
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((buffer >> bits) & 0xff);
    }
  }
  return Buffer.from(bytes);
}

function totpCode(secret: string, now = Date.now()): string {
  const counter = Math.floor(now / 1000 / 30);
  const message = Buffer.alloc(8);
  message.writeBigUInt64BE(BigInt(counter));
  const digest = createHmac('sha1', decodeBase32(secret)).update(message).digest();
  const offset = digest[digest.length - 1] & 0x0f;
  const value = ((digest[offset] & 0x7f) << 24)
    | ((digest[offset + 1] & 0xff) << 16)
    | ((digest[offset + 2] & 0xff) << 8)
    | (digest[offset + 3] & 0xff);
  return String(value % 1_000_000).padStart(6, '0');
}

function secretFromUri(uri: string): string {
  const secret = new URL(uri).searchParams.get('secret');
  if (!secret) throw new Error('TOTP setup URI did not include a secret');
  return secret;
}

test('users can enroll 2FA and complete the browser login challenge', async ({ page, request }) => {
  const email = `two-factor-${Date.now()}@example.test`;
  const register = await request.post('/api/v1/auth/register', {
    data: { email, displayName: 'Two Factor Playwright', password },
  });
  expect(register.status()).toBe(201);

  let token: string | undefined;
  let secret: string | undefined;
  try {
    let authResponse: AuthResponse;
    try {
      const login = await request.post('/api/v1/auth/login', { data: { email, password } });
      expect(login.status()).toBe(200);
      authResponse = await login.json() as AuthResponse;
    } catch (error) {
      const cleanupLogin = await request.post('/api/v1/auth/login', { data: { email, password } });
      if (cleanupLogin.ok()) {
        const cleanupResponse = await cleanupLogin.json() as AuthResponse;
        await request.delete('/api/v1/users/me', {
          headers: { Authorization: `Bearer ${cleanupResponse.accessToken}` },
          data: { password, code: null },
        });
      }
      throw error;
    }
    token = authResponse.accessToken;

    await page.route('**/api/v1/auth/login', (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(authResponse),
    }));
    await page.goto('/auth/login');
    await page.getByRole('textbox', { name: 'Email' }).fill(email);
    await page.getByRole('textbox', { name: 'Password' }).fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
    await page.getByLabel('Primary navigation').getByRole('link', { name: 'Profile' }).click();
    await expect(page.getByRole('region', { name: 'Profile and security' })).toBeVisible();

    await page.getByTestId('setup-2fa').click();
    await expect(page.getByTestId('totp-enrollment')).toBeVisible();
    secret = secretFromUri(await page.locator('[data-testid="totp-enrollment"] code').textContent() ?? '');
    await page.getByTestId('confirm-totp-code').fill(totpCode(secret));
    await page.getByRole('button', { name: 'Confirm 2FA' }).click();
    await expect(page.locator('p.status')).toHaveText('Two-factor authentication enabled.');

    await page.unroute('**/api/v1/auth/login');
    await page.getByRole('button', { name: 'Logout' }).click();
    await page.goto('/auth/login');
    await page.getByRole('textbox', { name: 'Email' }).fill(email);
    await page.getByRole('textbox', { name: 'Password' }).fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/auth\/2fa/);
    await expect(page.getByRole('region', { name: 'Two-factor verification' })).toBeVisible();
    await page.getByTestId('two-factor-code').fill(totpCode(secret));
    await page.getByRole('button', { name: 'Verify' }).click();
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
  } finally {
    if (token) {
      await request.delete('/api/v1/users/me', {
        headers: { Authorization: `Bearer ${token}` },
        data: { password, code: secret ? totpCode(secret) : null },
      });
    }
  }
});
