import { test, expect } from '@playwright/test';
import { createSignedInCustomer, addToCart } from './helpers';

// =====================================================================
// CART spec (setup state - fill in the tests to practice)
// =====================================================================
// Locators that exist today:
//   /cart empty -> 'Your cart is empty.' text (inside a box) + 'Browse products' link
//   /cart items -> per item: name, '{price} each', quantity element
//                  data-testid='cart-quantity', line total, 'Remove' link,
//                  +/- buttons (siblings of cart-quantity)
//   totals      -> data-testid='cart-total' (server-computed, e.g. '$199.98')
//   checkout btn-> data-testid='cart-checkout'
//   navbar      -> link 'Cart' (getByRole('link', { name: 'Cart' }))
// =====================================================================
// NOTE: totals are ALWAYS computed by the backend from unit prices in the
// DB - never trust the browser. The cart is per-customer on the server.
// =====================================================================
// Suggested scenarios to write:
//
// 1. Empty cart
//    - sign in (createSignedInCustomer), open /cart, expect 'Your cart is empty.'
//
// 2. Add then verify server state
//    - addToCart(page, 'Wireless Headphones'), goto /cart
//    - item name visible, cart-quantity = '1', cart-total = '$99.99'
//
// 3. Quantity steppers recompute the total server-side
//    - click '+' twice -> cart-quantity '3', cart-total '$399.97' (99.99*3)
//    - click '-' once  -> cart-quantity '2', cart-total '$199.98'
//    - with quantity already 1, '-' does nothing (UI guards quantity <= 0;
//      backend rejects quantity <= 0 with HTTP 400)
//
// 4. Remove an item
//    - add an item, click its 'Remove' link, expect 'Your cart is empty.'
//
// 5. Route protection
//    - logged out, opening /cart redirects to /login (RequireAuth + backend 401)
//
// =====================================================================
// Run one spec:   npx playwright test tests/cart.spec.ts
// =====================================================================


// 1. Empty cart
//    - sign in (createSignedInCustomer), open /cart, expect 'Your cart is empty.'

test('empty cart', async ({ page }) => {
  // Sign in as a customer
  await createSignedInCustomer(page, "Kalil","user");

  // Navigate to the cart page
  await page.goto('/cart');

  // Expect the cart to be empty
  await expect(page.getByText('Your cart is empty.')).toBeVisible();
});


// 2. Add then verify server state
//    - addToCart(page, 'Wireless Headphones'), goto /cart
//    - item name visible, cart-quantity = '1', cart-total = '$99.99'

test('add then verify server state', async ({ page }) => {
  // Sign in as a customer
  await createSignedInCustomer(page, "Kalil","user");

  // Add a product to the cart
  await addToCart(page, 'Wireless Headphones');

  // Navigate to the cart page
  await page.goto('/cart');

  // Expect the item to be visible
  await expect(page.getByText('Wireless Headphones')).toBeVisible();

  // Expect the cart quantity to be 1
  await expect(page.getByTestId('cart-quantity')).toHaveText('1');

  // Expect the cart total to be $99.99
  await expect(page.getByTestId('cart-total')).toHaveText('$99.99');
});

// 3. Quantity steppers recompute the total server-side
//    - click '+' twice -> cart-quantity '3', cart-total '$399.97' (99.99*3)
//    - click '-' once  -> cart-quantity '2', cart-total '$199.98'
//    - with quantity already 1, '-' does nothing (UI guards quantity <= 0;
//      backend rejects quantity <= 0 with HTTP 400)

test('quantity steppers recompute the total server-side', async ({ page }) => {
  // Sign in as a customer
  await createSignedInCustomer(page, "Kalil","user");

  // Add a product to the cart
  await addToCart(page, 'Wireless Headphones');

  // Navigate to the cart page
  await page.goto('/cart');

  // Expect the item to be visible
  await expect(page.getByText('Wireless Headphones')).toBeVisible();

  // Expect the cart quantity to be 1
  await expect(page.getByTestId('cart-quantity')).toHaveText('1');

  // Expect the cart total to be $99.99
  await expect(page.getByTestId('cart-total')).toHaveText('$99.99');

  // Click '+' twice
  await page.getByRole('button', { name: '+' }).click();
  await expect(page.getByTestId('cart-quantity')).toHaveText('2');
  
  await page.getByRole('button', { name: '+' }).click();
  await expect(page.getByTestId('cart-quantity')).toHaveText('3');

  // Expect the cart total to be $399.97
  await expect(page.getByTestId('cart-total')).toHaveText('$299.97');

  // Click '-'
  await page.getByRole('button', { name: '-' }).click();

  // Expect the cart quantity to be 2
  await expect(page.getByTestId('cart-quantity')).toHaveText('2');

  // Expect the cart total to be $199.98
  await expect(page.getByTestId('cart-total')).toHaveText('$199.98');
});

// 4. Remove an item
//    - add an item, click its 'Remove' link, expect 'Your cart is empty.'
test('remove an item', async ({ page }) => {
  // Sign in as a customer
  await createSignedInCustomer(page, "Kalil","user");

  // Add a product to the cart
  await addToCart(page, 'Wireless Headphones');

  // Navigate to the cart page
  await page.goto('/cart');

  // Expect the item to be visible
  await expect(page.getByText('Wireless Headphones')).toBeVisible();

  // Click 'Remove'
  await page.getByTestId('remove-btn').click();

  // Expect the cart to be empty
  await expect(page.getByText('Your cart is empty.')).toBeVisible();
});

// 5. Route protection
//    - logged out, opening /cart redirects to /login (RequireAuth)
test('Route Protection', async ({ page }) => {
  // Navigate to the cart page
  await page.goto('/cart');

  // this frontend redirects to home page commig from the RequireAuth
  await expect(page).toHaveURL(/\/login$/);
});