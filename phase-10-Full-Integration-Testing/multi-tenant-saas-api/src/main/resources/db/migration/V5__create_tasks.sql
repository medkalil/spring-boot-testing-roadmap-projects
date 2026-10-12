-- V5: tasks belong to exactly one project, and through it to exactly one
-- organization (task -> project -> organization is the ownership chain the
-- tenant checks follow).
CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    project_id BIGINT NOT NULL REFERENCES projects (id) ON DELETE CASCADE
);