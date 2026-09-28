# Batch 0 — Repository Bootstrap and Reproducible Baseline Design

## Scope

Batch 0 establishes a buildable, runnable foundation for Neo4flix. It creates no
product vertical slice, graph schema, authentication behavior, catalog behavior,
or recommendation behavior; those belong to later batches.

The design implements exactly four business applications: User, Movie, Rating,
and Recommendation. `database-migrator` is an infrastructure component only.

## Architecture and repository shape

The repository uses a root Maven reactor with a `backend` aggregator. The
backend contains `platform-common` and the four executable Spring Boot 4.1.1
service modules. `platform-common` is strictly limited to cross-cutting
infrastructure such as request IDs, Problem Details helpers, and shared test
utilities; it contains no domain model, repository, or business rule.

Each service is a minimal imperative Spring MVC application with its own
configuration and Actuator health endpoint. No service starts graph migrations;
the future one-shot `database-migrator` owns that responsibility. The services
connect to the same configured Neo4j endpoint and expose only baseline
configuration/health behavior in this batch.

The frontend is an Angular 22.1.5 standalone application with Angular Material,
SCSS, Signals/services, and a locked npm dependency graph. It is intentionally a
small shell rather than a premature collection of feature pages.

## Runtime topology and configuration

`infra/compose.yml` defines Neo4j with the pinned GDS-compatible image,
`database-migrator`, the four services, and `web` (Angular static assets served
by Nginx). Dependency order is Neo4j readiness, migrator completion, service
health, then web. The migrator remains a clearly documented placeholder until
Batch 1 supplies real Neo4j-Migrations scripts.

Environment values are supplied through `.env`/runtime environment and described
by `.env.example`. The example contains placeholders only. Docker image tags and
explicitly declared dependencies are pinned; no release path uses `latest`.

Nginx provides static SPA hosting and `/api/v1` route forwarding only; it does
not replace backend authorization. The baseline reserves the canonical service
paths and passes `X-Request-Id` through.

## Cross-cutting behavior

Each backend service gets a request-ID filter that accepts a valid incoming
`X-Request-Id` or generates one, adds it to responses and logging context, and
clears request-local state afterward. A common exception handler emits a minimal
RFC 7807 `ProblemDetail` response with a stable code and trace/request ID,
without exception internals or secrets.

Actuator health is enabled for local/container readiness checks with non-sensitive
endpoints only. Baseline configuration validates essential settings early enough
to make a missing runtime dependency visible rather than silently producing a
false healthy service.

## Verification strategy

The batch supplies root commands that prove the baseline at the appropriate
layer: Maven reactor verification, frontend clean install/lint/unit/build,
Compose configuration validation, and Docker health/GDS smoke checks. Focused
backend tests cover the request-ID and Problem Detail contracts; frontend tests
cover that the standalone application shell initializes. Runtime checks confirm
all four service health endpoints after the migrator placeholder succeeds.

This batch does not claim a real Neo4j schema or graph behavior. Batch 1 will
add migrations and Testcontainers integration evidence; later behavior-bearing
slices will use red-green-refactor TDD.

## Acceptance criteria

- A clean checkout can run the Java reactor and Angular baseline with committed
  dependency locks.
- Compose syntax/configuration validates with pinned images and no secrets.
- Neo4j with GDS starts and its version can be queried.
- The migrator placeholder completes before services start.
- User, Movie, Rating, and Recommendation services reach their restricted
  health endpoints with baseline configuration.
- No extra business service, prohibited framework, schema migration ownership,
  or committed secret is introduced.

## Canonical references

- `docs/reference/00_MASTER_EXECUTION_PLAN.md`, Batch 0
- `docs/reference/01_PRODUCT_SPEC.md`, sections 1–3 and 23–28
- `docs/reference/02_TECHNICAL_ARCHITECTURE.md`, sections 2–6, 10–30
- `docs/reference/06_TESTING_SECURITY.md`, sections 1–2, 26–27, and 43
- `docs/reference/08_DEPLOYMENT_OPERATIONS.md`, sections 2–20 and 25–27
