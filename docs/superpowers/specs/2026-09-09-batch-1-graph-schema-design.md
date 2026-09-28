# Batch 1 Graph Schema, Migrations, and Test Harness Design

## Goal

Establish the Neo4j graph schema and operational foundation that later vertical slices consume: one-shot, versioned schema migrations; deterministic seed entry points; persistence mappings; and real Neo4j/GDS integration tests. Batch 1 introduces no public API behavior, authentication flow, or recommendation algorithm.

## Scope and boundaries

The graph remains one Neo4j Community 2026.07.x database shared by the four existing services. `database-migrator` is the only schema writer at startup. Business services consume the resulting schema but never execute migrations themselves.

The implementation uses Neo4j-Migrations 4.1.x from a dedicated `database/migrator` Java module/image. Its command accepts an explicit mode:

- `migrate` applies only versioned Cypher files in `database/migrations/` and exits successfully when already current.
- `verify` inspects the expected constraints and indexes after migration without modifying seed data.
- `seed-demo`, `seed-audit`, and `seed-load` invoke separate deterministic seed loaders; none runs implicitly during normal startup.

The existing Compose `database-migrator` service changes from a GDS-readiness placeholder to this one-shot migration command. It continues to wait for Neo4j health and a successful `RETURN gds.version()` query, and its non-zero exit remains a hard dependency failure for all business services.

## Schema migration design

Migrations are append-only, idempotent under Neo4j-Migrations tracking semantics, and named in canonical order:

1. `V001__core_node_constraints.cypher` creates unique constraints for `User.id`, `User.normalizedEmail`, `Movie.id`, `Genre.id`, and `Genre.normalizedName`.
2. `V002__relationship_uniqueness.cypher` creates relationship-property unique constraints for `RATED.key` and `WATCHLISTED.key`.
3. `V003__search_indexes.cypher` creates the supported Movie title/year lookup and Genre name indexes. No full-text search behavior is introduced until the Movie slice owns it.
4. `V004__auth_support_constraints.cypher` creates unique `AuthSession.id` and `AuthChallenge.id` constraints.
5. `V005__share_constraints.cypher` creates unique `RecommendationShare.id` and `RecommendationShare.publicTokenHash` constraints.

All constraints and indexes use explicit stable names. The migration runner must expose the configured location and database name, but never log Neo4j credentials. On an empty database it applies all five versions; on a current database it reports no pending migrations and leaves graph data unchanged.

## Persistence boundary

Minimal Spring Data Neo4j entity and relationship-property mappings live with their data owners:

- `user-service`: `User`, `AuthSession`, and `AuthChallenge` nodes plus `WATCHLISTED` relationship properties.
- `movie-service`: `Movie`, `Genre`, and `IN_GENRE` persistence relationship.
- `rating-service`: `RATED` relationship properties and narrow projections for later rating reads.
- `recommendation-service`: `RecommendationShare` and its ownership/movie relation projections.

All identifiers are application UUID strings; internal Neo4j element IDs are never surfaced. These mappings are persistence-only and are not returned from controllers. They contain no cross-service mutation helpers or domain use cases.

## Seeds

Seed inputs live under `database/seeds/demo`, `database/seeds/audit`, and `database/seeds/load`. They are independent of migrations and use explicit loader commands.

The audit fixture is a small deterministic graph with fixed UUIDs, UTC timestamps, a visible movie/genre cluster, and rating relationships suitable for later golden recommendation assertions. It contains no real credentials, raw secrets, or public share tokens. Demo passwords, when Batch 2 introduces user seeding, remain environment inputs and are not part of this batch's fixture data. Load data is deterministic and synthetic; Batch 1 supplies the framework and a small reproducibility fixture rather than a production-sized generator.

## Integration test strategy

A shared test fixture starts pinned Neo4j 2026.07.1 with the GDS plugin mechanism enabled through Testcontainers. Tests run migrations against an empty database and query Neo4j directly to prove the contract that unit tests cannot prove:

- every required named constraint/index exists after the first migration;
- rerunning migrations is successful and does not duplicate schema objects;
- GDS responds with a version;
- duplicate concurrent `RATED` creation attempts result in exactly one relationship because the relationship key constraint rejects the rival write;
- duplicate concurrent `WATCHLISTED` creation attempts likewise leave one relationship;
- the audit loader produces the exact fixed nodes, edges, and scores on a clean graph.

Test data is isolated per test or cleaned through explicit Cypher so tests never depend on execution order. The integration profile must not use an external developer Neo4j instance.

## Failure handling and verification

Malformed migration configuration, a failed Cypher migration, unavailable Neo4j, or unavailable GDS makes the migrator exit non-zero and blocks business service startup. Tests capture the empty-to-latest and latest-rerun paths. Compose smoke continues to prove the production-like order: Neo4j healthy, GDS verified, migrations successful, then services healthy.

The Batch 1 gate is met only with fresh evidence that an empty Neo4j reaches the latest version, rerunning is safe, expected constraints/indexes exist, relationship uniqueness holds under concurrency, the deterministic audit seed loads, and the GDS function smoke succeeds.

## Non-goals

Batch 1 does not add WebFlux, JPA/SQL, APOC, Kafka, RabbitMQ, Redis, GraphQL, Spring Cloud, an extra microservice, API endpoints, automatic demo seeding, full-text search endpoints, or recommendation scoring behavior. Those belong to later batches.
