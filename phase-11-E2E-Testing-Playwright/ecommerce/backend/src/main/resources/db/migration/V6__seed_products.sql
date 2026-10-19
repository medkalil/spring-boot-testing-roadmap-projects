-- Deterministic catalog: the E2E tests assert exact names, prices and stock,
-- so the seed must be idempotent and versioned like the schema itself.
-- Playwright data strategy = "Seed known test data, create fresh customers"
-- (Phase 11 document, section 11.2).
INSERT INTO products (name, description, price, active, stock) VALUES
    ('Wireless Headphones', 'Over-ear wireless headphones with noise cancelling.', 99.99, TRUE, 500),
    ('Mechanical Keyboard', 'Cherry MX switches, RGB backlight, tenkeyless.', 79.50, TRUE, 300),
    ('Ergonomic Mouse', 'Vertical wireless mouse for everyday work.', 29.99, TRUE, 400),
    ('USB-C Cable 2m',    'Durable braided cable, 100W charging.',              12.99, TRUE, 1000),
    ('stock correctness product',    'product for stock correctness test to test it only there.', 10.0, TRUE, 10);

-- Deliberately unavailable product: NOT in the public catalog (active = FALSE),
-- so adding it must be rejected and must never create a cart entry.
INSERT INTO products (name, description, price, active, stock) VALUES
    ('Discontinued Earbuds', 'Legacy model - no longer for sale.', 19.99, FALSE, 0);