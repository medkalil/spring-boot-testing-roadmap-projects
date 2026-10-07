-- Phase 8.8 — Migration 1: customers
-- BIGSERIAL is PostgreSQL-specific: it creates a BIGINT column plus a sequence
-- that GenerationType.IDENTITY uses for the generated id.
CREATE TABLE customers (
    id    BIGSERIAL    PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
);