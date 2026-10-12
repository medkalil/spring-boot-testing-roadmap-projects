-- V3: memberships = the tenant authorization table.
--
--   user_id | organization_id | role
--
-- The UNIQUE (user_id, organization_id) constraint is the database-level
-- guarantee that a user cannot join the same organization twice - one of the
-- constraints the Phase 10 test suite exercises (duplicate membership must be
-- rejected, and the failed insert must leave no partial state behind).
--
-- The role (ADMIN / MEMBER) lives HERE, not on users: Alice can be ADMIN of
-- Acme and MEMBER of Globex at the same time. Spring Security's ROLE_* in the
-- JWT cannot express that - it is one global role per token.
CREATE TABLE memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    CONSTRAINT uq_membership_user_org UNIQUE (user_id, organization_id)
);