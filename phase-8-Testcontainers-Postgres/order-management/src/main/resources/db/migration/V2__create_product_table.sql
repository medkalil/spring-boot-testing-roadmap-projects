-- Phase 8.8 — Migration 2: products
-- NUMERIC(19, 2) matches BigDecimal @Column(precision = 19, scale = 2)
-- 19 = maximum total number of digits
-- 2  = digits after the decimal point
-- exp: 12345678901234567.89 (17 digits before + 2 digits after = 19 digits)
CREATE TABLE products (
    id    BIGSERIAL       PRIMARY KEY,
    name  VARCHAR(255)   NOT NULL,
    price NUMERIC(19, 2) NOT NULL
);