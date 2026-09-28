import { expect, test } from '@playwright/test';

const password = 'Valid-password-123';

test('authenticated users can create, review, and remove a movie rating', async ({ page, request }) => {
  const email = `ratings-${Date.now()}@example.test`;
  const movies = await request.get('/api/v1/movies?size=1');
  expect(movies.status()).toBe(200);
  const movie = ((await movies.json()) as { content: Array<{ id: string; title: string }> }).content[0];
  test.skip(!movie, 'The catalog must contain a movie for the rating flow.');

  const register = await request.post('/api/v1/auth/register', {
    data: { email, displayName: 'Ratings Playwright', password },
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

  try {
    await page.goto('/auth/login');
    await page.getByRole('textbox', { name: 'Email' }).fill(email);
    await page.getByRole('textbox', { name: 'Password' }).fill(password);
    let loggedIn = false;
    for (let attempt = 0; attempt < 3 && !loggedIn; attempt += 1) {
      await page.getByRole('button', { name: 'Sign in' }).click();
      try {
        await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 3_000 });
        loggedIn = true;
      } catch (error) {
        if (attempt === 2) throw error;
        await page.waitForTimeout(4_000);
      }
    }

    await page.getByLabel('Primary navigation').getByRole('link', { name: 'Movies' }).click();
    await expect(page.getByRole('heading', { name: 'Movies' })).toBeVisible();
    await page.getByRole('link', { name: 'Details' }).first().click();
    await expect(page.getByRole('heading', { name: movie.title })).toBeVisible();
    await page.getByRole('link', { name: 'Rate this movie' }).click();
    await expect(page.getByRole('heading', { name: `Rate ${movie.title}` })).toBeVisible();
    await page.getByRole('radio', { name: '5 stars' }).check();
    const createRequest = page.waitForResponse((response) => response.url().endsWith('/api/v1/ratings') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save rating' }).click();
    expect((await createRequest).status()).toBe(201);
    await expect(page.getByRole('status')).toHaveText('Rating saved.');

    await page.getByRole('radio', { name: '4 stars' }).check();
    const updateRequest = page.waitForResponse((response) => response.url().includes(`/api/v1/ratings/${movie.id}`) && response.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Update rating' }).click();
    expect((await updateRequest).status()).toBe(200);
    await expect(page.getByRole('status')).toHaveText('Rating saved.');

    await page.getByRole('link', { name: 'Movies', exact: true }).click();
    await page.getByLabel('Primary navigation').getByRole('link', { name: 'Profile' }).click();
    await expect(page.getByTestId('rating-history')).toContainText(movie.title);
    await expect(page.getByTestId('rating-history')).toContainText('4 / 5');

    const deleteRequest = page.waitForResponse((response) => response.url().includes(`/api/v1/ratings/${movie.id}`) && response.request().method() === 'DELETE');
    await page.getByRole('button', { name: 'Remove' }).click();
    expect((await deleteRequest).status()).toBe(204);
    await expect(page.getByRole('status')).toHaveText('You have not rated any movies yet.');
  } finally {
    await request.delete('/api/v1/users/me', {
      headers: { Authorization: `Bearer ${authResponse.accessToken}` },
      data: { password, code: null },
    });
  }
});
