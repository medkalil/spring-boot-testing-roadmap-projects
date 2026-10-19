-- Line items of an order. unit_price and product_name are snapshots taken at
-- checkout: the total in the history must stay truthful even if the catalog
-- price changes later (the frontend's order history renders these, not the
-- current product table).
CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(200)  NOT NULL,
    quantity     INT           NOT NULL,
    unit_price   NUMERIC(12,2) NOT NULL,
    line_total   NUMERIC(12,2) NOT NULL
);