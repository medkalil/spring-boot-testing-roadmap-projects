-- Registered customers and the E2E login identity (email, BCrypt password).
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    full_name  VARCHAR(150) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);