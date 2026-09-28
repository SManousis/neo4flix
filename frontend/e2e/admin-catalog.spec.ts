import { expect, test } from '@playwright/test';

const email = process.env.NEO4FLIX_E2E_ADMIN_EMAIL;
const password = process.env.NEO4FLIX_E2E_ADMIN_PASSWORD;

test('ADMIN can create, edit, and delete catalog entries in the browser', async ({ page, request }) => {
  test.skip(!email || !password, 'Set NEO4FLIX_E2E_ADMIN_EMAIL and NEO4FLIX_E2E_ADMIN_PASSWORD for the disposable admin fixture.');

  let movieId: string | undefined;
  let genreId: string | undefined;
  try {
    await page.goto('/auth/login');
    await page.getByRole('textbox', { name: 'Email' }).fill(email!);
    await page.getByRole('textbox', { name: 'Password' }).fill(password!);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();

    await page.goto('/admin/catalog');
    await expect(page.getByRole('heading', { name: 'Catalog administration' })).toBeVisible();
    page.on('dialog', (dialog) => dialog.accept());

    const movieTitle = `Browser movie ${Date.now()}`;
    await page.getByRole('textbox', { name: 'Title' }).fill(movieTitle);
    await page.getByRole('textbox', { name: 'Overview' }).fill('Created by the admin browser contract');
    await page.getByRole('spinbutton', { name: 'Release year' }).fill('2026');
    const movieCreate = page.waitForResponse((response) => response.url().includes('/api/v1/movies') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Create movie' }).click();
    movieId = (await (await movieCreate).json()).id;
    await expect(page.getByRole('status')).toContainText('Movie created.');

    const movieRow = page.locator('li').filter({ hasText: movieTitle });
    await movieRow.getByRole('button', { name: 'Edit' }).click();
    await expect(page.getByRole('button', { name: 'Update movie' })).toBeVisible();
    const updatedTitle = `${movieTitle} updated`;
    await page.getByRole('textbox', { name: 'Title' }).fill(updatedTitle);
    const updateRequest = page.waitForRequest((request) => request.url().includes('/api/v1/movies/') && request.method() === 'PATCH');
    const updateResponse = page.waitForResponse((response) => response.url().includes('/api/v1/movies/') && response.request().method() === 'PATCH');
    await page.getByRole('button', { name: 'Update movie' }).click();
    expect((await updateRequest).postDataJSON().title).toBe(updatedTitle);
    expect((await (await updateResponse).json()).title).toBe(updatedTitle);
    await expect(page.getByRole('status')).toContainText('Movie updated.');

    const updatedRow = page.locator('li').filter({ hasText: updatedTitle });
    await updatedRow.getByRole('button', { name: /^Delete/ }).click();
    await expect(page.getByRole('status')).toContainText('Movie deleted.');
    await expect(page.getByText(updatedTitle, { exact: false })).toHaveCount(0);

    const genreName = `Browser genre ${Date.now()}`;
    const genreForm = page.getByRole('form', { name: 'Genre form' });
    await genreForm.getByRole('textbox', { name: 'Genre name' }).fill(genreName);
    const genreCreate = page.waitForResponse((response) => response.url().includes('/api/v1/genres') && response.request().method() === 'POST');
    await genreForm.getByRole('button', { name: 'Create genre' }).click();
    genreId = (await (await genreCreate).json()).id;
    await expect(page.getByRole('status')).toContainText('Genre created.');
    const genreRow = page.locator('li').filter({ hasText: genreName });
    await genreRow.getByRole('button', { name: `Rename ${genreName}` }).click();
    const renamedGenre = `${genreName} updated`;
    await genreForm.getByRole('textbox', { name: 'Genre name' }).fill(renamedGenre);
    await genreForm.getByRole('button', { name: 'Rename genre' }).click();
    await expect(page.getByRole('status')).toContainText('Genre renamed.');
    const renamedGenreRow = page.locator('li').filter({ hasText: renamedGenre });
    await renamedGenreRow.getByRole('button', { name: `Delete ${renamedGenre}` }).click();
    await expect(page.getByRole('status')).toContainText('Genre deleted.');
  } finally {
    const login = await request.post('/api/v1/auth/login', { data: { email, password } });
    if (login.ok()) {
      const token = (await login.json()).accessToken as string;
      const headers = { Authorization: `Bearer ${token}` };
      if (movieId) await request.delete(`/api/v1/movies/${movieId}`, { headers });
      if (genreId) await request.delete(`/api/v1/genres/${genreId}`, { headers });
      await request.delete('/api/v1/users/me', { headers, data: { password, code: null } });
    }
  }
});
