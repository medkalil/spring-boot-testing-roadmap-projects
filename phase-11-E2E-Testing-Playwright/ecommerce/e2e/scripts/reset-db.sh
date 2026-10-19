#!/usr/bin/env bash
# Recreate the E2E database from scratch: drops the volume (schema + data),
# then starts a fresh container. Flyway re-runs V1..V6 on first boot of the
# API, so the catalog is deterministically re-seeded and order/cart state is
# gone. Use this to restore repeatable conditions (Phase 11, Exercise 8).
set -euo pipefail

docker compose -f ../compose.e2e.yaml down -v --remove-orphans
docker compose -f ../compose.e2e.yaml up -d e2e-db
echo "E2E database recreated. Run 'npx playwright test' now."