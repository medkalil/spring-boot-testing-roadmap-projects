-- Deterministic seed data so a freshly migrated database has content and so
-- tests can prove Flyway actually ran (they look for this exact title).
INSERT INTO tasks (title, description, status)
VALUES ('Sample task (seeded)', 'Created by Flyway V2 migration', 'TODO');
