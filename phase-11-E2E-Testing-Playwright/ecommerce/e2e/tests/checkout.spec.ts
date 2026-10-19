import { test, expect, request } from '@playwright/test';
import { createSignedInCustomer, loginAs, PASSWORD, uniqueEmail } from './helpers';

// =====================================================================
// CHECKOUT + ORDERS spec (setup state - fill in the tests to practice)
// =====================================================================
// Locators that exist today:
//   /checkout   -> label 'Shipping address' (textarea), 'Place order' button,
//                  data-testid='checkout-total' (server total)
//   confirmation-> h1 'Order confirmed', data-testid='order-status' =
//                  'CONFIRMED', data-testid='order-total', URL /orders/{id}
//   /orders     -> your order cards (name, status, total),
//                  navbar link 'My orders' (getByRole('navigation') scopes it)
//   cart empty  -> 'Your cart is empty.' after a successful order
// =====================================================================
// Business rules exercised by these tests:
//   * stock is decremented when the order is placed (see /products after)
//   * prices/totals are re-read from the DB by the backend on checkout
//   * order visibility is OWNER-ONLY: another customer's order id -> HTTP 404,
//     both for the page and for GET /api/orders/{id}
//   * a blank shipping address is rejected (form 'required')
// =====================================================================
// Suggested scenarios to write:
//
// 1. Happy path end to end
//    - createSignedInCustomer + addToCart('Wireless Headphones')
//    - /cart -> cart-checkout -> fill address -> Place order
//    - confirmation: heading 'Order confirmed', order-status 'CONFIRMED',
//      order-total '$99.99', item name visible, URL /orders/{id}
//    - navbar 'My orders' -> order listed; open /cart -> 'Your cart is empty.'
//
// 2. Blank address is blocked
//    - with a non-empty cart, click 'Place order' with an empty address:
//      the browser's required validation stops the submit (no API call)
//
// 3. Another customer cannot view the order (owner-only 404)
//    - sign in as A, buy something, capture order id from the URL
//    - API-only with request.newContext({ baseURL: 'http://localhost:8080' }):
//      register+login as B, GET /api/orders/{id} with B's token -> 404
//    - (advanced) also check the URL /orders/{id} in a real browser
//      signed in as B -> 404 page, not a data leak
//
// 4. Simulated backend failure never shows false success
//    - use page.route('**/api/orders', route -> fulfill 500 with a body like
//      '{"message":"checkout backend unavailable"}') for POST
//    - click Place order -> role=alert contains that message, URL stays
//      /checkout, and no 'Order confirmed' heading ever appears
//
// 5. (Bonus) Stock correctness
//    - after an order, re-open /products and confirm the product's stock
//      number decreased by the ordered quantity
//
// =====================================================================
// Run one spec:   npx playwright test tests/checkout.spec.ts
// =====================================================================


// 1. Happy path end to end
//    - createSignedInCustomer + addToCart('Wireless Headphones')
//    - /cart -> cart-checkout -> fill address -> Place order
//    - confirmation: heading 'Order confirmed', order-status 'CONFIRMED',
//      order-total '$99.99', item name visible, URL /orders/{id}
//    - navbar 'My orders' -> order listed; open /cart -> 'Your cart is empty.'

test('full checkout workflow end to end', async ({ page }) => {

  await createSignedInCustomer(page, 'E2E Buyer Checkout Test Happy Path', "checkout-prefix");

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


// 2. Blank address is blocked
//    - with a non-empty cart, click 'Place order' with an empty address:
//      the browser's required validation stops the submit (no API call)

test('blank address is blocked', async ({ page }) => {
  // Register and sign in a customer.
  await createSignedInCustomer(page, 'E2E Buyer Checkout Test Blank Address', "checkout-prefix");

  // Add one product to the cart.
  await page.goto('/products');
  const product = page.getByTestId('product-card').filter({ hasText: 'Wireless Headphones' });
  await expect(product).toBeVisible();
  await product.getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByText('added to your cart')).toBeVisible();

  // Proceed to checkout.
  await page.getByRole('link', { name: 'Cart' }).click();
  await page.getByTestId('cart-checkout').click();
  await expect(page).toHaveURL(/\/checkout$/);

  // Attempt to place the order with a blank shipping address.
  // The browser's required validation prevents form submission.
  await page.getByLabel('Shipping address').fill('');
  await page.getByRole('button', { name: 'Place order' }).click();

  // Verify that the order confirmation page is NOT reached and the form stays present.
  // We assert that the URL still indicates checkout and the confirmation heading is not visible.
  await expect(page).toHaveURL(/\/checkout$/);
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeHidden();
  await expect(page.getByLabel('Shipping address')).toBeVisible(); // still there, unmet required
});

// 3. Another customer cannot view the order (owner-only 404)
//    - sign in as A, buy something, capture order id from the URL
//    - API-only with request.newContext({ baseURL: 'http://localhost:8080' }):
//      register+login as B, GET /api/orders/{id} with B's token -> 404
//    - (advanced) also check the URL /orders/{id} in a real browser
//      signed in as B -> 404 page, not a data leak

test('another customer cannot view the order', async ({ page, context }) => {
  // Register and sign in a customer.
  await createSignedInCustomer(page, 'E2E Buyer Checkout Test Owner', "checkout-prefix-owner");

  // Add one product to the cart.
  await page.goto('/products');
  const product = page.getByTestId('product-card').filter({ hasText: 'Wireless Headphones' });
  await expect(product).toBeVisible();
  await product.getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByText('added to your cart')).toBeVisible();

  // Proceed to checkout.
  await page.getByRole('link', { name: 'Cart' }).click();
  await page.getByTestId('cart-checkout').click();
  await expect(page).toHaveURL(/\/checkout$/);

  // Attempt to place the order with a blank shipping address.
  // The browser's required validation prevents form submission.
  await page.getByLabel('Shipping address').fill('11 rue du commerce, 75001 Paris');
  await page.getByRole('button', { name: 'Place order' }).click();
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeVisible();

  // get the orderId then verify that another loged in user can't access it not from the frontend or with get from the backend
  await expect(page).toHaveURL(/\/orders\/\d+$/);
  const orderId = page.url().split('/').pop()!;

  // the owner can assess it front & back   
  await page.goto('/orders/' + orderId);
  await expect(page).toHaveURL(/\/orders\/.*$/);
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeVisible();
  await expect(page.getByTestId('order-status')).toHaveText('CONFIRMED');
  await expect(page.getByTestId('order-total')).toHaveText('$99.99');
  await expect(page.getByText('Wireless Headphones')).toBeVisible();

  // another customer can't access it from front & back
    //   back
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

    //frontend
  await loginAs(page, snoopEmail, PASSWORD);
  await page.goto('/orders/' + orderId);
  await expect(page).toHaveURL(/\/orders\/.*$/);
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeHidden();
  await expect(page.getByText('Wireless Headphones')).toBeHidden();
});

// 4. Simulated backend failure never shows false success
//    - use page.route('**/api/orders', route -> fulfill 500 with a body like
//      '{"message":"checkout backend unavailable"}') for POST
//    - click Place order -> role=alert contains that message, URL stays
//      /checkout, and no 'Order confirmed' heading ever appears

test('simulated backend failure never shows false success', async ({ page }) => {
  await createSignedInCustomer(page, 'E2E Buyer Checkout Test Backend Failure', "checkout-prefix-failure");

    // Add one product to the cart.
  await page.goto('/products');
  const product = page.getByTestId('product-card').filter({ hasText: 'Wireless Headphones' });
  await expect(product).toBeVisible();
  await product.getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByText('added to your cart')).toBeVisible();

  // Proceed to checkout.
  await page.getByRole('link', { name: 'Cart' }).click();
  await page.getByTestId('cart-checkout').click();
  await expect(page).toHaveURL(/\/checkout$/);

  // Attempt to place the order with a blank shipping address.
  // The browser's required validation prevents form submission.
  await page.getByLabel('Shipping address').fill('11 rue du commerce, 75001 Paris');
  
  // Simulate a backend failure on POST /api/orders (force network error to see how the app behaves)
  await page.route('**/api/orders', async route => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'checkout backend unavailable' }),
    });
  });

  await page.getByRole('button', { name: 'Place order' }).click();

  // Verify that the order confirmation page is NOT reached and the form stays present.
  // We assert that the URL still indicates checkout and the confirmation heading is not visible.
  await expect(page).toHaveURL(/\/checkout$/);
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeHidden();
  await expect(page.getByLabel('Shipping address')).toBeVisible(); // still there, unmet required
});


// 5. (Bonus) Stock correctness
//    - after an order, re-open /products and confirm the product's stock
//      number decreased by the ordered quantity ?
//    - BUT: how can i test it where i can't be sure that number decreased by the ordered quantity because other test 
//      they may change the stock also and all the test use the same DB parallely?
//    - soluion here is : create the product here to only use it here so that the product is fully isolated for this test only
//      ether create proecut by api request (i don't have a post request in the controller), or ass it in the seed and only use this product for this test

test('stock correctness', async ({ page }) => {
  // Register and sign in a customer
  await createSignedInCustomer(page, 'E2E Buyer Checkout Test Stock Correctness', "checkout-prefix-stock-correctness");

  // go to product page and check initial stock
  await page.goto('/products');
  const product = page.getByTestId('product-card').filter({ hasText: 'stock correctness product' });
  await expect(product).toBeVisible();

  // Add one product to the cart.
  await product.getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByText('added to your cart')).toBeVisible();

  // Proceed to checkout.
  await page.getByRole('link', { name: 'Cart' }).click();
  await page.getByTestId('cart-checkout').click();
  await expect(page).toHaveURL(/\/checkout$/);
  await page.getByLabel('Shipping address').fill('123 Test Street, Test City');
  await page.getByRole('button', { name: 'Place order' }).click();

   // expect hete to auto-wait the page to load, so the api request will get the updated stock    
   await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeVisible();

   // do a api request and match the expected stock to 9 cz thz seeded qty is 10
    const api = await page.evaluate(() => fetch('/api/products').then(res => res.json()));
    const stock = api.find((p: any) => p.name === 'stock correctness product')?.stock;
    expect(stock).toBe(9);
});




  