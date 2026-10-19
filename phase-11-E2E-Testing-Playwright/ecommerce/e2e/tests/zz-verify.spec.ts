import { test, expect, request } from '@playwright/test';
import { randomUUID } from 'node:crypto';

const PASSWORD = 'StrongPassword123!';

function uniqueEmail(prefix: string): string {
  return `${prefix}-${randomUUID()}@example.test`;
}

async function register(page, fullName: string, email: string) {
  await page.goto('/register');
  await page.getByLabel('Full name').fill(fullName);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByText('Account created successfully')).toBeVisible();
}

async function login(page, email: string) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Login' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

test('homepage heading and catalog render', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Online Store' })).toBeVisible();

  await page.goto('/products');
  await expect(page.getByTestId('product-card').filter({ hasText: 'Wireless Headphones' })).toBeVisible();
  await expect(page.getByTestId('product-card')).toHaveCount(4);
});

test('registered customer can log in and protected routes redirect', async ({ page }) => {
  const email = uniqueEmail('auth');

  // Unauthenticated user redirected to login
  await page.goto('/cart');
  await expect(page).toHaveURL(/\/login$/);

  await register(page, 'Test Customer', email);
  await login(page, email);

  // Authenticated user may open the cart
  await page.goto('/cart');
  await expect(page).toHaveURL(/\/cart$/);
});

test('bad credentials show a clear error and do not redirect', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('nobody@example.test');
  await page.getByLabel('Password').fill('WrongPassword123!');
  await page.getByRole('button', { name: 'Login' }).click();

  await expect(page.getByRole('alert')).toContainText(/invalid credentials/i);
  await expect(page).not.toHaveURL(/\/dashboard$/);
});

test('logged-out customer cannot open the cart', async ({ page }) => {
  await page.goto('/cart');
  await expect(page).toHaveURL(/\/login/);
});

test('full checkout workflow end to end', async ({ page }) => {
  const email = uniqueEmail('buyer');

  await register(page, 'E2E Buyer', email);
  await login(page, email);

  // browse + add one product
  await page.goto('/products');
  const product = page.getByTestId('product-card').filter({ hasText: 'Wireless Headphones' });
  await expect(product).toBeVisible();
  await product.getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByText('added to your cart')).toBeVisible();

  // cart shows the item and the server-side total
  await page.getByRole('link', { name: 'Cart' }).click();
  await expect(page.getByText('Wireless Headphones')).toBeVisible();
  await expect(page.getByTestId('cart-quantity')).toHaveText('1');
  await expect(page.getByTestId('cart-total')).toHaveText('$99.99');

  // delta on + - buttons: qty 2, total recomputed
  await page.getByTestId('cart-quantity').locator('..').getByRole('button', { name: '+' }).click();
  await expect(page.getByTestId('cart-quantity')).toHaveText('2');
  await expect(page.getByTestId('cart-total')).toHaveText('$199.98');

  // checkout
  await page.getByTestId('cart-checkout').click();
  await expect(page).toHaveURL(/\/checkout$/);
  await page.getByLabel('Shipping address').fill('123 Test Street, Test City');
  await page.getByRole('button', { name: 'Place order' }).click();

  // confirmation carries real backend data
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeVisible();
  await expect(page.getByTestId('order-status')).toHaveText('CONFIRMED');
  await expect(page.getByTestId('order-total')).toHaveText('$199.98');
  await expect(page.getByText('Wireless Headphones')).toBeVisible();

  // order history (two "My orders" links exist on this page: navbar + confirmation)
  await page.getByRole('navigation').getByRole('link', { name: 'My orders' }).click();
  await expect(page).toHaveURL(/\/orders$/);
  await expect(page.getByText('Wireless Headphones')).toBeVisible();

  // cart was cleared by the successful checkout, persisted server-side
  await page.getByRole('link', { name: 'Cart' }).click();
  await expect(page.getByText('Your cart is empty.')).toBeVisible();
});

test('another customer cannot view the order, even knowing its id', async ({ page, context }) => {
  const buyerEmail = uniqueEmail('owner');
  await register(page, 'Order Owner', buyerEmail);
  await login(page, buyerEmail);

  // buy something so an order id exists
  await page.goto('/products');
  await page.getByTestId('product-card')
    .filter({ hasText: 'Ergonomic Mouse' })
    .getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByText('added to your cart')).toBeVisible();
  await page.getByRole('link', { name: 'Cart' }).click();
  await page.getByTestId('cart-checkout').click();
  await page.getByLabel('Shipping address').fill('10 Owner Lane');
  await page.getByRole('button', { name: 'Place order' }).click();
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeVisible();
  await expect(page).toHaveURL(/\/orders\/\d+$/);
  const orderId = page.url().split('/').pop()!;

  // a second, fresh customer tries to fetch that order via the API
  const snoopEmail = uniqueEmail('snoop');
  const bobApi = await request.newContext({ baseURL: 'http://localhost:8080' });
  await bobApi.post('/api/auth/register', {
    data: { fullName: 'Snooper', email: snoopEmail, password: PASSWORD },
  });
  const loginRes = await bobApi.post('/api/auth/login', {
    data: { email: snoopEmail, password: PASSWORD },
  });
  expect(loginRes.status()).toBe(200);

  const forbidden = await bobApi.get(`/api/orders/${orderId}`, {
    headers: { Authorization: `Bearer ${(await loginRes.json()).accessToken}` },
  });
  expect(forbidden.status()).toBe(404);

  await bobApi.dispose();
  await context.close();
});

test('simulated checkout API failure shows an error, never false success', async ({ page }) => {
  const email = uniqueEmail('fail');
  await register(page, 'Failure Case', email);
  await login(page, email);

  await page.goto('/products');
  await page.getByTestId('product-card')
    .filter({ hasText: 'USB-C Cable 2m' })
    .getByRole('button', { name: 'Add to cart' }).click();
  await page.getByRole('link', { name: 'Cart' }).click();
  await page.getByTestId('cart-checkout').click();

  // Force the order POST to fail at the network level: the UI must react
  // to the error and must NOT navigate to a confirmation page.
  // Mocking a result of an api request
  await page.route('**/api/orders', (route) => {
    if (route.request().method() === 'POST') {
      return route.fulfill({ status: 500, contentType: 'application/json', body: '{"message":"checkout backend unavailable"}' });
    }
    return route.continue();
  });

  await page.getByLabel('Shipping address').fill('1 Broken Street');
  await page.getByRole('button', { name: 'Place order' }).click();

  await expect(page.getByRole('alert')).toContainText('checkout backend unavailable');
  await expect(page).toHaveURL(/\/checkout$/);
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toHaveCount(0);
});