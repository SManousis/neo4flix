import { expect, test } from '@playwright/test';

test('anonymous users can browse the public movie catalog', async ({ page }) => {
  await page.goto('/movies');

  await expect(page.getByRole('heading', { name: 'Movies' })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Search titles' })).toBeVisible();
  await expect(page.getByText(/movies$/)).toBeVisible();
});

test('authenticated USER accounts are denied the admin catalog and movie mutations', async ({ page, request }) => {
  const email = `catalog-user-${Date.now()}@example.test`;
  const password = 'Valid-password-123';
  const register = await request.post('/api/v1/auth/register', {
    data: { email, displayName: 'Catalog User', password },
  });
  expect(register.status()).toBe(201);

  const login = await request.post('/api/v1/auth/login', { data: { email, password } });
  expect(login.status()).toBe(200);
  const authResponse = await login.json() as { accessToken: string; user: unknown };
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

  const mutation = await request.post('/api/v1/movies', {
    headers: { Authorization: `Bearer ${authResponse.accessToken}` },
    data: {
      title: 'Denied movie', overview: 'Denied', releaseYear: 2026, releaseDate: null,
      runtimeMinutes: null, posterUrl: null, externalSource: null, externalId: null, genreIds: [],
    },
  });
  expect(mutation.status()).toBe(403);

  const deletion = await request.delete('/api/v1/users/me', {
    headers: { Authorization: `Bearer ${authResponse.accessToken}` },
    data: { password, code: null },
  });
  expect(deletion.status()).toBe(204);
});
