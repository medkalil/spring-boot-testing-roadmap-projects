import { test, expect } from '@playwright/test';
import { registerUser, loginAs, uniqueEmail, PASSWORD } from './helpers';

// =====================================================================
// AUTH + REGISTRATION spec (setup state - fill in the tests to practice)
// =====================================================================
// Locators that exist today:
//   /register -> getByLabel('Full name'|'Email'|'Password'), getByRole('button', { name: 'Register' })
//                success text: 'Account created successfully'
//   /login    -> getByLabel('Email'|'Password'), getByRole('button', { name: 'Login' })
//                error: getByRole('alert') contains /invalid credentials/i
//                success redirects to /dashboard (h1 'Dashboard')
// =====================================================================
// Suggested scenarios to write:
//
// 1. Successful registration
//    - use registerUser(page, 'Full Name', uniqueEmail('auth'))
//    - assert the success message, then that Login -> /dashboard works
//
// 2. Registration validation
//    - empty fields: the browser's required attribute blocks submission
//    - password shorter than 8 chars -> backend 400, error message shown, stay on /register
//    - duplicate email -> backend 409 Conflict, error message shown
//
// 3. Successful login
//    - register once, then loginAs -> expect URL /dashboard and heading Dashboard
//
// 4. Login failures
//    - unknown email and/or wrong password -> alert contains 'Invalid credentials'
//    - assert you do NOT land on /dashboard
//
// 5. Route protection (logged-out)
//    - /cart, /checkout, /orders all redirect to /login
//    - after login those routes become reachable
//
// 6. Token storage (advanced)
//    - after login, localStorage key 'ecommerce.token' is set
//    - a logged-in page can call a protected API (e.g. /api/cart) successfully
//
// =====================================================================
// Run one spec:        npx playwright test tests/auth.spec.ts
// Run with the web UI: npx playwright test tests/auth.spec.ts --ui
// Trace on failure:    npx playwright show-trace test-results/<dir>/trace.zip
// =====================================================================


// 1. Successful registration
//    - use registerUser(page, 'Full Name', uniqueEmail('auth'))
//    - assert the success message

test('successful registration', async ({ page }) => {
  const email = uniqueEmail('auth');
  await registerUser(page, 'Full Name', email);
  await expect(page.getByText('Account created successfully')).toBeVisible();
});

// 2. Registration validation
//    - empty fields: the browser's required attribute blocks submission (We did only the backend validation)
//    - password shorter than 8 chars -> backend 400, error message shown, stay on /register
//    - duplicate email -> backend 409 Conflict, error message shown

test('empty fields in registration form', async ({ page }) => {
  await page.goto('/register');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByRole('alert')).toContainText('full name, email and password are required');
});

// 3. Successful login
//    - register once, then loginAs -> expect URL /dashboard and heading Dashboard

test('successful login', async ({ page }) => {
  const email = uniqueEmail('auth');
  await registerUser(page, 'Full Name', email);
  await loginAs(page, email);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
});

//
// 4. Login failures
//    - unknown email and/or wrong password -> alert contains 'Invalid credentials'
//    - assert you do NOT land on /dashboard
test('login failures', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('nonexistent@example.com');
  await page.getByLabel('Password').fill('wrong-password');
  await page.getByRole('button', { name: 'Login' }).click();
  await expect(page.getByRole('alert')).toContainText('Invalid credentials');
  await expect(page).not.toHaveURL(/\/dashboard$/);
});

// 5. Route protection (logged-out)
//    - /cart, /checkout, /orders all redirect to /login
//    - after login those routes become reachable
test('cart route protection logged out', async ({ page }) => {
  await page.goto('/cart');
  await expect(page).toHaveURL(/\/login$/);
});

// 6. Token storage (advanced)
//    - after login, localStorage key 'ecommerce.token' is set
//    - a logged-in page can call a protected API (e.g. /api/cart) successfully
test('stores the token after login and accesses the protected cart API', async ({
  page,
}) => {
  const email = uniqueEmail('auth');
  await registerUser(page, 'Full Name', email);
  await loginAs(page, email);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  // 1. Verify that the token was stored in localStorage
  const token = await page.evaluate(() =>
    localStorage.getItem('ecommerce.token')
  );

  expect(token).toBeTruthy();

  // 2. Verify that the logged-in frontend can call the protected API
  const cartResponse = await page.evaluate(async () => {
    return fetch('/api/cart', {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('ecommerce.token')}`,
      },
    }).then(async (response) => ({
      status: response.status,
      body: await response.text(),
    }));
  });

  expect(cartResponse.status).toBe(200);
});