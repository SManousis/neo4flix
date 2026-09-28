import { APIRequestContext, expect, Page, test } from '@playwright/test';

const password = 'Valid-password-123';

type DisposableUser = {
  email: string;
  token: string;
  authResponse: Record<string, unknown>;
};

async function signIn(page: Page, email: string, authResponse: Record<string, unknown>): Promise<void> {
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
}

async function registerDisposableUser(request: APIRequestContext): Promise<DisposableUser> {
  const email = `recommendations-${Date.now()}-${Math.random().toString(36).slice(2)}@example.test`;
  const register = await request.post('/api/v1/auth/register', {
    data: { email, displayName: 'Recommendations Playwright', password },
  });
  expect(register.status()).toBe(201);
  try {
    const login = await request.post('/api/v1/auth/login', { data: { email, password } });
    expect(login.status()).toBe(200);
    const authResponse = await login.json() as Record<string, unknown>;
    return { email, token: authResponse.accessToken as string, authResponse };
  } catch (error) {
    const cleanupLogin = await request.post('/api/v1/auth/login', { data: { email, password } });
    if (cleanupLogin.ok()) {
      const cleanupResponse = await cleanupLogin.json() as { accessToken: string };
      await deleteDisposableUser(request, cleanupResponse.accessToken);
    }
    throw error;
  }
}

async function deleteDisposableUser(request: APIRequestContext, token: string): Promise<void> {
  const deletion = await request.delete('/api/v1/users/me', {
    headers: { Authorization: `Bearer ${token}` },
    data: { password, code: null },
  });
  expect(deletion.status()).toBe(204);
}

test('authenticated users can load recommendations, persist filters, and survive recommendation outage', async ({ page, request }) => {
  const user = await registerDisposableUser(request);
  try {
    await signIn(page, user.email, user.authResponse);
    await page.getByLabel('Primary navigation').getByRole('link', { name: 'Recommendations' }).click();
    await expect(page.getByRole('heading', { name: 'Recommendations' })).toBeVisible();
    await expect(page.getByRole('status').or(page.getByTestId('recommendations'))).toBeVisible();

    await page.getByLabel('Genre').fill('Science Fiction');
    await page.getByTestId('apply-filters').click();
    await expect(page).toHaveURL(/genre=Science(?:%20|\+)Fiction/);
    await expect(page.getByRole('heading', { name: 'Recommendations' })).toBeVisible();

    await page.route('**/api/v1/recommendations/me**', (route) => route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({ code: 'RECOMMENDATION_SERVICE_UNAVAILABLE' }),
    }));
    await page.getByLabel('Primary navigation').getByRole('link', { name: 'Movies' }).click();
    await page.getByLabel('Primary navigation').getByRole('link', { name: 'Recommendations' }).click();
    await expect(page.getByRole('alert')).toContainText('temporarily unavailable');
    await page.getByRole('link', { name: 'Browse movies' }).first().click();
    await expect(page.getByRole('heading', { name: 'Movies' })).toBeVisible();
  } finally {
    await deleteDisposableUser(request, user.token);
  }
});
