-- Phase 8.8 — Migration 3: orders + order_items
-- "orders" keeps the entity name Order usable in Java without conflicting with SQL.
CREATE TABLE orders (
    id          BIGSERIAL       PRIMARY KEY,
    customer_id BIGINT          NOT NULL,
    total       NUMERIC(19, 2)  NOT NULL,
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id)
);

-- unit_price is copied from products.price when the order is created, so the
-- price the customer actually paid is preserved even if the product price changes later.
CREATE TABLE order_items (
    id         BIGSERIAL      PRIMARY KEY,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INTEGER        NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);