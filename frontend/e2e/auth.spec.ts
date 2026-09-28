import { expect, test } from '@playwright/test';

const password = 'Valid-password-123';

test('login reload profile and logout preserve the browser auth contract', async ({ page, request }) => {
  const email = `playwright-${Date.now()}@example.test`;
  const register = await request.post('/api/v1/auth/register', {
    data: { email, displayName: 'Playwright Smoke', password },
  });
  expect(register.status()).toBe(201);

  await page.goto('/auth/login');
  await page.getByRole('textbox', { name: 'Email' }).fill(email);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
  await expect(page.getByText('Playwright Smoke')).toBeVisible();

  const storage = await page.evaluate(() => ({
    local: Object.keys(localStorage),
    session: Object.keys(sessionStorage),
  }));
  expect(storage.local).toEqual([]);
  expect(storage.session).toEqual([]);

  await page.reload();
  await expect(page.getByRole('link', { name: 'Profile' })).toBeVisible();
  await page.getByRole('link', { name: 'Profile' }).click();
  await expect(page.getByRole('region', { name: 'Profile and security' })).toBeVisible();

  await page.getByRole('button', { name: 'Logout' }).click();
  await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible();

  const login = await request.post('/api/v1/auth/login', { data: { email, password } });
  expect(login.status()).toBe(200);
  const token = (await login.json()).accessToken as string;
  const deletion = await request.delete('/api/v1/users/me', {
    headers: { Authorization: `Bearer ${token}` },
    data: { password, code: null },
  });
  expect(deletion.status()).toBe(204);
});
