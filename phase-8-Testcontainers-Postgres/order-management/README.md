# Phase 8 — Testcontainers + PostgreSQL

Integration tests that run the **full application** against a **real PostgreSQL 16**
started automatically by Testcontainers — no mocks, no H2.

```
HTTP -> Controller -> Service -> Repository -> Hibernate -> JDBC -> PostgreSQL 16 (Docker)
```

Why not H2? H2 *is not* PostgreSQL. Different SQL dialect, types, timestamp/UUID
behaviour, sequences, constraints, locking, isolation. A green test on H2 does not
guarantee a green test (or a working query) on PostgreSQL.

## Prerequisites

```bash
docker --version
docker ps          # an empty list is fine: Docker is running
```

No local PostgreSQL installation is needed. A container image is pulled on first run
(`postgres:16`, plus Testcontainers' `ryuk` reaper).

## Run the tests

```bash
mvn test                            # OrderIntegrationTest
mvn test -Dtest=OrderIntegrationTest
```

Nothing to start by hand: Testcontainers creates the PostgreSQL container, waits until
it accepts connections, lets Flyway migrate it, runs the tests, then destroys the
container. The first run pulls `postgres:16` (~400 MB, cached afterwards).

## What Testcontainers actually does

`PostgreSQLContainer<?>` is not PostgreSQL. It is an object that knows how to create,
start, wait-for-ready, expose, and destroy a PostgreSQL Docker container.

```
JUnit starts
  -> @Testcontainers + @Container: Docker creates/runs postgres:16 (dynamic host port)
  -> Testcontainers waits until PostgreSQL accepts connections
  -> @DynamicPropertySource feeds url/username/password into the Spring context
  -> Flyway applies V1, V2, V3 to the empty database
  -> Hibernate validates the schema (ddl-auto=validate, it never creates tables)
  -> tests run against real PostgreSQL
  -> container is destroyed
```

The container field is `static` on purpose: one PostgreSQL per **class**, reused by
all test methods. Container lifecycle (`start -> tests -> stop`) is separate from the
data lifecycle (`transaction -> commit/rollback`).

## API

| Method | Path                | Body                                                        |
|--------|---------------------|-------------------------------------------------------------|
| POST   | `/api/customers`    | `{"name":"John","email":"john@example.com"}`                 |
| POST   | `/api/products`     | `{"name":"Laptop","price":1000}`                             |
| POST   | `/api/orders`       | `{"customerId":1,"items":[{"productId":1,"quantity":2}]}`    |
| GET    | `/api/orders/{id}`  | —                                                            |

Order total is computed by the service from the *current* product prices, and each
`order_items.unit_price` stores a snapshot of the price paid.

## Schema (Flyway, production-like)

`src/main/resources/db/migration`

| Migration | Contents                                     |
|-----------|----------------------------------------------|
| V1        | `customers` (unique email)                   |
| V2        | `products` (`NUMERIC(19,2)` price)           |
| V3        | `orders` + `order_items` with FKs and indexes |

Flyway owns the schema, so `spring.jpa.hibernate.ddl-auto=validate`: the application
fails fast if entities and migrations drift apart.

## Scenarios to practise in `OrderIntegrationTest`

1. PostgreSQL connection (`DataSource` -> Hikari -> container).
2. `POST /api/customers`, then assert with `customerRepository.findById(id)`.
3. `POST /api/products`, same idea.
4. `POST /api/orders`: 2 x 100 + 3 x 50 = **350**, then assert the DB rows.
5. `GET /api/orders/{id}`: assert customer, items, product names, total.
6. Invalid customer (`customerId: 99999`) -> **404** and **no order row**.
7. Invalid product -> exception + **transaction rollback**, no order row.
8. Test isolation (Phase 8.11): decide between `@Transactional` rollback, explicit
   cleanup, or a fresh container per test — and know the trade-offs.

## Notes

- `OrderItem.unitPrice` is intentionally a copy: changing `products.price` later must
  not rewrite history of an order.
- `ResourceNotFoundException` carries `@ResponseStatus(NOT_FOUND)`, and
  `GlobalExceptionHandler` also declares it explicitly: a catch-all
  `@ExceptionHandler(Exception.class)` would otherwise win and answer 500.
- `spring.jpa.open-in-view=false`: responses are built inside the service transaction.
- `Order` is a SQL keyword, hence `@Table(name = "orders")`.