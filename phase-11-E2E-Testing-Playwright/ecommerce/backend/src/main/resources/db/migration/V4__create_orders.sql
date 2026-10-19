-- Order. status is a string from {CONFIRMED} for the simulated checkout;
-- total is computed by the service from the PRODUCT table (never from the
-- browser) and survived the checkout transaction, so it is an immutable snapshot.
CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    status           VARCHAR(20)  NOT NULL,
    shipping_address TEXT         NOT NULL,
    total            NUMERIC(12,2) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);