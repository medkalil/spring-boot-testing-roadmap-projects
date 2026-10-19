import { expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

// Shared helpers for the e2e specs. Your tests can import and reuse these,
// or you can inline them per-spec to practice. Expected selectors below are
// defined by the app; see PLAN.md "Locator contract" for the full list.

export const PASSWORD = 'StrongPassword123!';

/** Unique email per run so sharing one e2e database never collides. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}-${randomUUID()}@example.test`;
}

/** Fill the /register form and assert the success message appears. */
export async function registerUser(page: Page, fullName: string, email: string, password = PASSWORD) {
  await page.goto('/register');
  await page.getByLabel('Full name').fill(fullName);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByText('Account created successfully')).toBeVisible();
}

/** Fill the /login form and assert the redirect to /dashboard. */
export async function loginAs(page: Page, email: string, password = PASSWORD) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Login' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

/** Register + login in one go. Returns the created customer's email. */
export async function createSignedInCustomer(page: Page, fullName: string, prefix: string): Promise<string> {
  const email = uniqueEmail(prefix);
  await registerUser(page, fullName, email);
  await loginAs(page, email);
  return email;
}

/** From the catalog, add one product to the cart and wait for its toast. */
export async function addToCart(page: Page, productName: string) {
  await page.goto('/products');
  await page
    .getByTestId('product-card')
    .filter({ hasText: productName })
    .getByRole('button', { name: 'Add to cart' })
    .click();
  await expect(page.getByText('added to your cart')).toBeVisible();
}