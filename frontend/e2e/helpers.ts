import { Page } from '@playwright/test';

export const TEST_USER = {
  email:    process.env.E2E_EMAIL    ?? 'test@example.com',
  password: process.env.E2E_PASSWORD ?? 'testpassword123',
};

/** Log in via the auth page and wait for the dashboard to load. */
export async function login(page: Page) {
  await page.goto('/auth');
  await page.getByPlaceholder(/email/i).fill(TEST_USER.email);
  await page.getByPlaceholder(/password/i).fill(TEST_USER.password);
  await page.getByRole('button', { name: /sign in|login/i }).click();
  // Wait for dashboard to appear
  await page.waitForURL('/', { timeout: 15_000 });
  await page.waitForSelector('text=Control Center', { timeout: 10_000 });
}

/** Log out via the sidebar button. */
export async function logout(page: Page) {
  await page.getByRole('button', { name: /logout/i }).click();
  await page.waitForURL('/auth', { timeout: 10_000 });
}

/** Submit a prompt and wait for the response view. */
export async function submitPrompt(page: Page, prompt: string) {
  const textarea = page.locator('textarea').first();
  await textarea.fill(prompt);
  await page.getByRole('button', { name: /initialize/i }).click();
  // Wait for either the response view or the generating spinner
  await page.waitForSelector('[data-testid="response-view"], text=Analyzing', { timeout: 5_000 });
}
