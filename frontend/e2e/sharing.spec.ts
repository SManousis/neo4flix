import { expect, test } from '@playwright/test';

const password = 'Valid-password-123';

test('authenticated users can create a share and anonymous visitors see only public movie data', async ({ page, request }) => {
  const email = `sharing-${Date.now()}@example.test`;
  const movies = await request.get('/api/v1/movies?size=1');
  expect(movies.status()).toBe(200);
  const movie = ((await movies.json()) as { content: Array<{ id: string }> }).content[0];
  test.skip(!movie, 'The catalog must contain a movie for the sharing flow.');

  const register = await request.post('/api/v1/auth/register', {
    data: { email, displayName: 'Sharing Playwright', password },
  });
  expect(register.status()).toBe(201);

  let token: string | undefined;
  try {
    const login = await request.post('/api/v1/auth/login', { data: { email, password } });
    expect(login.status()).toBe(200);
    const authResponse = await login.json() as Record<string, unknown>;
    token = authResponse.accessToken as string;
    const rating = await request.post('/api/v1/ratings', {
      headers: { Authorization: `Bearer ${token}` },
      data: { movieId: movie.id, score: 5 },
    });
    expect(rating.status()).toBe(201);

    await page.goto('/auth/login');
    await page.getByRole('textbox', { name: 'Email' }).fill(email);
    await page.getByRole('textbox', { name: 'Password' }).fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();

    await page.route('**/api/v1/auth/login', (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(authResponse),
    }));
    await page.getByLabel('Primary navigation').getByRole('link', { name: 'Recommendations' }).click();
    await expect(page.getByTestId('recommendations')).toBeVisible();
    await page.getByTestId('share-action').first().click();
    const shareUrl = await page.getByTestId('share-url').first().textContent();
    expect(shareUrl).toMatch(/\/share\/[^\s]+$/);

    await page.getByRole('button', { name: 'Logout' }).click();
    await page.goto(shareUrl!);
    await expect(page.getByText('Shared with you through Neo4flix')).toBeVisible();
    await expect(page.locator('article h1')).toBeVisible();
    await expect(page.locator('body')).not.toContainText('ownerId');
    await expect(page.locator('body')).not.toContainText('email');
  } finally {
    if (token) {
      await request.delete('/api/v1/users/me', {
        headers: { Authorization: `Bearer ${token}` },
        data: { password, code: null },
      });
    }
  }
});
