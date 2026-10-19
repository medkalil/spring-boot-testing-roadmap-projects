#!/usr/bin/env bash
# Playwright webServer hook for the backend. Playwright runs this command when a
# test starts and terminates it when the test run ends.
#
# Startup order (Phase 11 document, section 13):
#   PostgreSQL ready -> Spring Boot ready -> React ready -> Playwright tests
#
# The PostgreSQL container is started HERE, idempotently. Playwright's
# readiness URL for this webServer is http://localhost:8080/api/products, so
# the test run cannot begin until the API - not just the port - answers.
set -euo pipefail

COMPOSE_FILE="../compose.e2e.yaml"

# 1. Reset the e2e database to a deterministic state BEFORE the API boots.
#    `down -v` drops the volume (schema + data); `up` recreates the container;
#    Flyway re-runs V1..V6 when the API starts -> full product stock, empty
#    users/carts/orders. This is the single source of "reset every run".
#
#    NOTE: Playwright starts the webServer BEFORE globalSetup runs, so the
#    reset lives here - in global-setup.ts it would run after the API was
#    already healthy and drop the volume underneath it.
#
#    Disable with E2E_SKIP_RESET=1 - e.g. you keep your own stack running and
#    Playwright `reuseExistingServer` skips this script entirely.
if [ "${E2E_SKIP_RESET:-0}" != "1" ]; then
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans
fi

# 2. Ensure PostgreSQL is up. `up` on an already-running container is a no-op,
#    which keeps repeated local runs fast.
docker compose -f "$COMPOSE_FILE" up -d e2e-db

# 3. Wait until PostgreSQL accepts connections. pg_isready ships inside the
#    official postgres image, so no extra tools are needed here.
CID=$(docker compose -f "$COMPOSE_FILE" ps -q e2e-db)
for i in $(seq 1 60); do
  if docker exec "$CID" pg_isready -U ecommerce >/dev/null 2>&1; then
    break
  fi
  if [ "$i" = "60" ]; then
    echo "ERROR: e2e-db did not become ready in time" >&2
    exit 1
  fi
  sleep 1
done

# 4. Start the Spring Boot API with the e2e profile.
#    `fork=false` runs the app INSIDE the Maven JVM, so `exec` leaves Playwright
#    owning exactly ONE child process - the whole API. Playwright's SIGKILL on
#    teardown then terminates everything. A forked app JVM (spring-boot:run's
#    default) would survive that kill, stay on :8080, and the next run would
#    reuse a stale backend whose database was just dropped under it.
exec mvn -f ../backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=e2e -Dspring-boot.run.fork=false