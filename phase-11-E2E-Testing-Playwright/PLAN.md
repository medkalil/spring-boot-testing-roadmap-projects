# Phase 11 - E2E Testing with Playwright

End-to-end testing of a small **e-commerce** app using **Playwright** driving a real
**React (Vite)** frontend against a real **Spring Boot** API backed by **PostgreSQL 16**,
all orchestrated automatically by Playwright's `webServer`.

## Stack

| Layer    | Tech                                                        | Notes                                              |
|----------|-------------------------------------------------------------|----------------------------------------------------|
| Frontend | React 18, TypeScript, Vite 5, react-router 6                | dev server on `127.0.0.1:5173`                     |
| Backend  | Spring Boot 3.4.1, Spring Security, Spring Data JPA, Flyway | API on `127.0.0.1:8080` (e2e profile)              |
| DB       | `postgres:16` via Docker Compose                            | container `ecommerce-e2e-db`, host port **5433**   |
| Tests    | `@playwright/test` 1.49.1, Chromium                         | tests in `e2e/tests/**`, trace/images in `test-results/` |

Local PostgreSQL already owns `5432`, so the container is mapped to host `5433:5432`
(see `compose.e2e.yaml` and `application-e2e.properties`). The database is replaced
from scratch (`docker compose down -v`) by the reset script for repeatability.

## Running

Everything starts automatically when you run Playwright (two `webServer` entries in
`playwright.config.ts` bring up DB + Spring Boot + Vite and wait for health):

```bash
cd ecommerce/e2e
npx playwright test                      # run every spec in tests/
npx playwright test tests/checkout.spec.ts
npx playwright test --ui                 # interactive UI (great for practising)
npx playwright test --headed             # watch the browser
npx playwright test --trace on           # traces for every test
npx playwright show-trace test-results/<dir>/trace.zip
```

**The e2e database is reset before EVERY run** - automatically. The reset lives in
`scripts/start-backend.sh` (before the API boots), not in `global-setup.ts`, because
Playwright starts the `webServer` BEFORE `globalSetup` runs. Each run therefore starts
from a fresh Flyway-seeded state: full product stock, empty users/carts/orders.
`global-setup.ts` is the freshness guard - it verifies the catalog is seeded and warns
if a leftover backend with old data got reused (it never fails the run).

- `E2E_SKIP_RESET=1` disables the reset AND the freshness check
  (e.g. you keep your own backend running and Playwright reuses it).
- `reuseExistingServer: !process.env.CI` means a backend already answering on
  `:8080` is reused and `start-backend.sh` never runs - so no reset happens then.
- `scripts/start-backend.sh` runs the API with `spring-boot.run.fork=false` (app inside
  the Maven JVM) so Playwright owns ONE child process and full teardown is guaranteed -
  no orphaned JVM holding `:8080` for the next run.

Manual stack (equivalent to what the webServer does):

```bash
cd ecommerce/e2e
bash scripts/start-backend.sh            # reset DB, compose up e2e-db, wait, mvn spring-boot:run (e2e profile)
bash scripts/reset-db.sh                 # drop the volume and recreate the DB (Flyway re-runs)
cd ../frontend && npm run dev            # in a second terminal
```

## Locator contract (source of truth for every test)

| Page         | Locator                                                             |
|--------------|---------------------------------------------------------------------|
| anywhere     | navbar: links `Online Store`, `Products`, `Cart` (navbar only), `My orders` (navbar + confirmation page!), Login/Logout/`Dashboard` |
| `/`          | `getByRole('heading', { name: 'Online Store' })`                    |
| `/register`  | `getByLabel('Full name' \| 'Email' \| 'Password')`, button `Register`, success `Account created successfully` |
| `/login`     | `getByLabel('Email' \| 'Password')`, button `Login`, error `getByRole('alert')` contains `/invalid credentials/i` |
| `/dashboard` | `getByRole('heading', { name: 'Dashboard' })`                       |
| `/products`  | cards `getByTestId('product-card')` (name heading, description, `.price`, stock, button `Add to cart`); toast `added to your cart` |
| `/cart`      | item name, `$x each`, `data-testid='cart-quantity'`, `data-testid='cart-total'` (server total), `Remove`, `+`/`-`, `data-testid='cart-checkout'`; empty: `Your cart is empty.` |
| `/checkout`  | label `Shipping address` (textarea, `required`), button `Place order`, `data-testid='checkout-total'` |
| confirmation | `getByRole('heading', { name: 'Order confirmed' })`, `data-testid='order-status'` (`CONFIRMED`), `data-testid='order-total'`, URL `/orders/{id}` |
| `/orders`    | order cards (name, status, total); scope to `getByRole('navigation')` when a page also has a `My orders` link |

Per-item quantity steppers: `getByTestId('cart-quantity').locator('..').getByRole('button', { name: '+' })`.

## API + business rules the tests exercise

- `POST /api/auth/register` → `201` with **empty body** (tests only assert the page text)
- `POST /api/auth/login` → `200` `{ accessToken, refreshToken }`; token stored in localStorage key `ecommerce.token`; frontend sends `Authorization: Bearer <token>`
- `GET /api/products` → public catalog, prices in plain `price` field (frontend/`lib/format.ts money()` formats as `$99.99`)
- `GET/PATCH item/DELETE /api/cart/**` → cart is **per-customer server-side**; totals are computed by the backend from DB unit prices
- `POST /api/orders` → validates non-empty cart + non-blank address, snapshots `productName`/`unitPrice` into `order_items`, decrements stock, clears cart — all in one transaction
- `GET /api/orders/{id}` → **owner-only**: any other customer gets `404`
- Stock conflict → `409`; quantity `<= 0` → `400`; duplicate email → `409`
- Auth failures → `401`; frontend maps login 401 to role=alert `Invalid credentials...`

## What to practice (grouped specs, currently at setup state)

- `tests/auth.spec.ts` - registration, validation (short password, duplicate email),
  login success/failure, route protection, localStorage token
- `tests/products.spec.ts` - catalog rendering, 4 seeded products only (inactive
  `Discontinued Earbuds` never shown), formatted prices, add-to-cart toast
- `tests/cart.spec.ts` - empty cart, server totals, `+`/`-` steppers,
  remove item, logged-out redirect
- `tests/checkout.spec.ts` - happy-path checkout, blank-address block, owner-only
  404 (use the `request` fixture against `http://localhost:8080`), simulated
  backend failure via `page.route`, stock decrement bonus
- `tests/helpers.ts` - working helpers you can import or copy: `uniqueEmail`,
  `registerUser`, `loginAs`, `createSignedInCustomer`, `addToCart`, `PASSWORD`

Example helper usage inside a spec:

```ts
import { test, expect } from '@playwright/test';
import { createSignedInCustomer, addToCart } from './helpers';

test('cart total reflects the server price', async ({ page }) => {
  await createSignedInCustomer(page, 'Sam Buyer', 'sam');
  await addToCart(page, 'Wireless Headphones');
  await page.getByRole('link', { name: 'Cart' }).click();
  await expect(page.getByTestId('cart-total')).toHaveText('$99.99');
});
```

API-only probing from a spec (owner-only check / double-fetch):

```ts
const api = await request.newContext({ baseURL: 'http://localhost:8080' });
await api.post('/api/auth/register', { data: { fullName: 'B', email, password } });
const res = await api.post('/api/auth/login', { data: { email, password } });
const { accessToken } = await res.json();
const order = await api.get(`/api/orders/${id}`, { headers: { Authorization: `Bearer ${accessToken}` } });
expect(order.status()).toBe(404);
```

The verification suite for this phase lived in a temporary `tests/zz-verify.spec.ts`
and was deleted after going green; the scenarios listed above mirror it.

## Gotchas discovered while building

1. **`201` with an empty body**: `RegisterPage` returns 201 and no body; the frontend
   `api()` client previously always called `response.json()`, throwing `SyntaxError`
   and showing "Registration failed". Fixed by parsing JSON only when the body is
   non-empty (`src/api/client.ts`).
2. **Strict-mode locator collisions**: the confirmation page contains a `My orders`
   link *and* the navbar has one → scope with `getByRole('navigation')`.
3. **First request slowness / timeouts**: keep `expect(...)` timeouts generous for
   the first backend hit after DB startup, or rely on Playwright's auto-wait.
4. Always use `uniqueEmail(...)` - the DB is shared across runs; duplicate emails
   are rejected with 409.
5. Ports: app API = 8080, e2e DB = **5433** (not 5432), Vite = 5173.
6. **Orphaned backends**: `spring-boot:run` forks a separate app JVM by default, which
   survives Playwright's SIGKILL and keeps `:8080` + a Hikari pool to the e2e DB. A
   later run then "reuses" a stale backend whose DB was just dropped under it (empty
   carts, odd 500s). Fixed with `-Dspring-boot.run.fork=false` so the API runs inside
   the Maven JVM and dies with it.
7. **webServer starts before globalSetup**: a DB reset cannot run in `global-setup.ts`
   after the API is already healthy - it must run inside `start-backend.sh` first.

## Debugging checklist

- Run with `DEBUG=pw:api npx playwright test ...` to see every HTTP request/response.
- `npx playwright show-trace test-results/<dir>/trace.zip` shows snapshots + network.
- HEALTHCHECK/TIMEOUT issues → check `scripts/start-backend.sh` logs; `curl localhost:8080/api/products`
- DB reset → `bash scripts/reset-db.sh`

## File map

```
phase-11-E2E-Testing-Playwright/
├── PLAN.md
└── ecommerce/
    ├── compose.e2e.yaml                 # postgres:16, db ecommerce_e2e, host 5433
    ├── backend/                          # Spring Boot 3.4.1 (no test deps)
    │   ├── pom.xml
    │   └── src/main/{java/com/example/ecommerce,resources}
    │       ├── application.properties          # ddl-auto=validate, jwt props
    │       ├── application-e2e.properties      # datasource 5433/ecommerce_e2e
    │       ├── db/migration/V1..V6.sql         # schema + users/products/orders + seed
    │       └── jwt, security, config, entity, repository, dto, service, controller, exception
    ├── frontend/                          # React 18 + Vite 5 + TS
    │   └── src/{api/client.ts, api/types.ts, components, pages, lib/format.ts}
    └── e2e/                               # @playwright/test 1.49.1
        ├── playwright.config.ts            # webServer[] auto-starts DB+API+Vite
        ├── global-setup.ts                 # freshness guard (reset itself lives in start-backend.sh)
        ├── scripts/{start-backend.sh, reset-db.sh}
        └── tests/{auth,products,cart,checkout}.spec.ts + helpers.ts + zz-verify.spec.ts
```