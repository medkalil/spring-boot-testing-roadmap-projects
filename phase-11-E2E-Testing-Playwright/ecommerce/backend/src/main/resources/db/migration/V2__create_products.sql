-- The public catalog. price is trusted SERVER-side data: the frontend may display
-- it, but the backend reads it again at checkout time and never trusts a total
-- sent by the browser.
CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200)  NOT NULL,
    description TEXT          NOT NULL,
    price       NUMERIC(12,2) NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    stock       INT           NOT NULL DEFAULT 0 CHECK (stock >= 0)
);