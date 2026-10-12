-- V4: projects belong to exactly one organization.
-- organization_id is the tenant boundary of every project query.
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    organization_id BIGINT NOT NULL REFERENCES organizations (id) ON DELETE CASCADE
);