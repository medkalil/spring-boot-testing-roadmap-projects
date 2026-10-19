import { test, expect, request } from '@playwright/test';
import { createSignedInCustomer } from './helpers';
// import { addToCart } from './helpers';

// =====================================================================
// PRODUCTS CATALOG spec (setup state - fill in the tests to practice)
// =====================================================================
// Locators that exist today:
//   catalog cards -> getByTestId('product-card')  (one per listed product)
//   each card     -> heading product name, description text, '.price'
//                    (e.g. '$99.99'), stock text, button 'Add to cart'
//   home page     -> getByRole('heading', { name: 'Online Store' })
//   navbar        -> links: Products, Cart, My orders, and Dashboard/Login/Logout
// =====================================================================
// Seeded catalog (Flyway V6) - 4 ACTIVE products:
//   Wireless Headphones $99.99 (stock 500)
//   Mechanical Keyboard  $79.50 (stock 300)
//   Ergonomic Mouse      $29.99 (stock 200)
//   USB-C Cable 2m       $12.99 (stock 100)
// 'Discontinued Earbuds' is inactive (stock 0) and must NEVER be listed.
// =====================================================================
// Suggested scenarios to write:
//
// 1. Catalog renders
//    - open /products, expect exactly 4 getByTestId('product-card')
//    - each expected product name is visible; 'Discontinued Earbuds' is NOT
//
// 2. Each card shows the full server-side info
//    - price rendered as US dollars with 2 decimals, e.g. '$99.99' (lib/format money())
//    - description and stock are visible
//
// 3. Add to cart from the catalog
//    - hover/scroll to a card, click its 'Add to cart' button
//    - expect the toast 'added to your cart'
//
// 4. (Bonus) API check with the request fixture
//    - use request.newContext({ baseURL: 'http://localhost:8080' }) in a real test
//      double-fetch pattern is not needed here since products are public:
//      GET /api/products -> 200 and json has exactly 4 entries
//
// =====================================================================
// Run one spec:   npx playwright test tests/products.spec.ts
// =====================================================================


// 1. Catalog renders
//    - open /products, expect exactly 4 getByTestId('product-card')
//    - each expected product name is visible; 'Discontinued Earbuds' is NOT
test('catalog renders', async ({ page }) => {
  await page.goto('/products');
  await expect(page.getByTestId('product-card')).toHaveCount(4);
  await expect(page.getByTestId('product-card').first()).toContainText('Wireless Headphones');
  await expect(page.getByTestId('product-card').nth(1)).toContainText('Mechanical Keyboard');
  await expect(page.getByTestId('product-card').nth(2)).toContainText('Ergonomic Mouse');
  await expect(page.getByTestId('product-card').nth(3)).toContainText('USB-C Cable 2m');
});

// 2. Each card shows the full server-side info
//    - price rendered as US dollars with 2 decimals, e.g. '$99.99' (lib/format money())
//    - description and stock are visible
//
test('each card shows the full server-side info', async ({ page }) => {
  await page.goto('/products');
  await expect(page.getByTestId('product-card')).toHaveCount(4);
  await expect(page.getByText('$99.99')).toBeVisible();
  await expect(page.getByText('$79.50')).toBeVisible();
  await expect(page.getByText('$29.99')).toBeVisible();
  await expect(page.getByText('$12.99')).toBeVisible();
});

// 3. Add to cart from the catalog
//    - hover/scroll to a card, click its 'Add to cart' button
//    - needed to login 
//    - going back to products
//    - expect the toast 'added to your cart'
test('add to cart from catalog', async ({ page }) => {
  await page.goto('/products');
  await expect(page.getByTestId('product-card')).toHaveCount(4);
  await page.getByTestId('product-card').first().getByTestId('add-to-cart').click();

  await expect(page).toHaveURL(/\/login$/);
  // Sign in as a customer
  await createSignedInCustomer(page, "Kalil","user");

  // Going back to the products
  await page.goto('/products');
  await page.getByTestId('product-card').first().getByTestId('add-to-cart').click();
  await expect(page.getByText('added to your cart')).toBeVisible();
});

// 4. (Bonus) API check with the request fixture
//    - use request.newContext({ baseURL: 'http://localhost:8080' }) in a real test
//      double-fetch pattern is not needed here since products are public:
//      GET /api/products -> 200 and json has exactly 4 entries
test('API check with the request fixture', async ({ page }) => {
  const api = await request.newContext({ baseURL: 'http://localhost:8080' });
  const response = await api.get('/api/products');
  expect(response.status()).toBe(200);
  const products = await response.json();
  expect(products).toHaveLength(4);
});