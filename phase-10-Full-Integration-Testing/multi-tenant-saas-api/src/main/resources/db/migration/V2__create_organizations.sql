-- V2: organizations = the tenants. Every project and task belongs, directly or
-- indirectly, to exactly one organization. Nothing else owns tenant data.
CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);