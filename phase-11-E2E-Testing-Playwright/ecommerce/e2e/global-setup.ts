/**
 * Playwright runs this once before the tests. NOTE: by the time this runs,
 * the webServer hook has ALREADY started a healthy backend (Playwright boots
 * webServer before globalSetup) - so the actual DB reset lives in
 * scripts/start-backend.sh, before the API boots.
 *
 * This file is the freshness guard: it verifies the run really is starting
 * from a reset database and warns loudly (without failing) if not - the case
 * when an already-running backend was reused with leftover data.
 *
 *   E2E_SKIP_RESET=1 -> disables the reset in start-backend.sh AND silences
 *   this guard for people who deliberately keep their own data.
 */
interface CatalogProduct {
  id: number;
  name: string;
  price: number;
  stock: number;
}

const SEED_STOCK = 500; // Wireless Headphones seed stock (Flyway V6)

export default async function globalSetup() {
  if (process.env.E2E_SKIP_RESET === '1') {
    console.log('[globalSetup] E2E_SKIP_RESET=1 set - DB reset disabled, freshness check skipped.');
    return;
  }

  let products: CatalogProduct[];
  try {
    products = await fetchJson<CatalogProduct[]>('http://localhost:8080/api/products');
  } catch (err) {
    throw new Error(`[globalSetup] backend on :8080 did not answer /api/products: ${(err as Error).message}`);
  }

  const headphones = products.find((p) => p.name === 'Wireless Headphones');
  if (!headphones || headphones.stock !== SEED_STOCK) {
    console.warn(
      `[globalSetup] WARNING: not a fresh database (Wireless Headphones stock = ${headphones?.stock ?? '?'},` +
        ` expected ${SEED_STOCK}). A pre-existing backend was reused, so leftover data from earlier runs is present.\n` +
        '            Fix: stop your backend ("pkill -f com.example.ecommerce") and re-run, or run' +
        ' "bash scripts/reset-db.sh" once.\n' +
        '            If you intend to keep your own data, export E2E_SKIP_RESET=1.',
    );
    return;
  }

  console.log('[globalSetup] Fresh e2e database verified - catalog seeded, stock reset to seed values.');
}

async function fetchJson<T>(url: string): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 5_000);
  try {
    const res = await fetch(url, { signal: controller.signal });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    return (await res.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}