import { expect, test } from '@playwright/test';

const password = 'Valid-password-123';

test('authenticated users can add, browse, open, and remove a watchlist movie', async ({ page, request }) => {
  const email = `watchlist-${Date.now()}@example.test`;
  const movies = await request.get('/api/v1/movies?size=1');
  expect(movies.status()).toBe(200);
  const movie = ((await movies.json()) as { content: Array<{ id: string; title: string }> }).content[0];
  test.skip(!movie, 'The catalog must contain a movie for the watchlist flow.');

  const register = await request.post('/api/v1/auth/register', {
    data: { email, displayName: 'Watchlist Playwright', password },
  });
  expect(register.status()).toBe(201);
  const login = await request.post('/api/v1/auth/login', { data: { email, password } });
  expect(login.status()).toBe(200);
  const authResponse = await login.json() as { accessToken: string; user: unknown };
  const accessToken = authResponse.accessToken;
  await page.route('**/api/v1/auth/refresh', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(authResponse),
  }));

  try {
    await page.goto(`/movies/${movie.id}`);
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
    await expect(page.getByRole('heading', { name: movie.title })).toBeVisible();
    const add = page.waitForResponse((response) => response.url().includes(`/api/v1/users/me/watchlist/${movie.id}`) && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Add to watchlist' }).click();
    expect((await add).status()).toBe(201);
    await expect(page.getByRole('status')).toHaveText('Added to watchlist.');

    await page.goto('/watchlist');
    await expect(page.getByTestId('watchlist')).toContainText(movie.title);
    await page.getByRole('link', { name: movie.title }).click();
    await expect(page.getByRole('heading', { name: movie.title })).toBeVisible();

    await page.goto('/watchlist');
    const remove = page.waitForResponse((response) => response.url().includes(`/api/v1/users/me/watchlist/${movie.id}`) && response.request().method() === 'DELETE');
    await page.getByRole('button', { name: 'Remove' }).click();
    expect((await remove).status()).toBe(204);
    await expect(page.getByRole('status')).toHaveText('Your watchlist is empty.');
  } finally {
    await request.delete('/api/v1/users/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { password, code: null },
    });
  }
});
