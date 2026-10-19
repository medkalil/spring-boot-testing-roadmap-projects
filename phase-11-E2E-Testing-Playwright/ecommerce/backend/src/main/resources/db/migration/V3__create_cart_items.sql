-- Database-backed cart: one row per (user, product). Persists across page
-- reloads, which is exactly the behaviour Exercise 5 ("reload and the cart is
-- still there") verifies from the browser.
CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity   INT    NOT NULL CHECK (quantity > 0),
    CONSTRAINT uq_cart_item_user_product UNIQUE (user_id, product_id)
);