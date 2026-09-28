# Batch 1 Graph Schema, Migrations, and Test Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a versioned Neo4j schema, one-shot migrator, deterministic seed loaders, persistence mappings, and real Neo4j/GDS integration proof for all Batch 1 graph invariants.

**Architecture:** A dedicated `database-migrator` Maven module owns Neo4j-Migrations 4.1.2 and runs as the existing one-shot Compose dependency. Versioned Cypher files own schema, seed loaders stay explicit and separate, and a Testcontainers fixture proves the actual Neo4j/GDS behavior rather than mocking Cypher or constraints.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data Neo4j, Neo4j-Migrations 4.1.2, Neo4j 2026.07.1 Community + GDS 2026.07, Testcontainers, JUnit 5, AssertJ, Docker Compose, PowerShell.

**Spec:** `docs/superpowers/specs/2026-09-09-batch-1-graph-schema-design.md`

## Global Constraints

- Keep exactly the four existing business services; `database-migrator` is infrastructure, not a business service.
- Use Java 21, Spring MVC, Spring Data Neo4j, parameterized Cypher, and exact dependency/image pins; never use `latest`.
- No business service may apply migrations; only the one-shot migrator owns `database/migrations/` execution.
- Migrations are append-only, stable-named, and are the only source of schema changes; seeds are explicit commands and never startup side effects.
- IDs are application UUID strings; Neo4j internal element IDs never cross persistence boundaries.
- Persistence entities and relationship-property classes never reach controllers or public APIs.
- Preserve mutation ownership: User owns User/AuthSession/AuthChallenge/WATCHLISTED; Movie owns Movie/Genre/IN_GENRE; Rating owns RATED; Recommendation owns RecommendationShare relations.
- Relationship keys are deterministic `<userId>:<movieId>` and uniqueness is proven with real concurrent Neo4j writes.
- No WebFlux, JPA/SQL, APOC, Kafka, RabbitMQ, Redis, GraphQL, Spring Cloud, extra business services, automatic demo data, committed secrets, or raw Cypher interpolation.
- The migrator must verify `RETURN gds.version()` without logging credentials; failed migration/GDS blocks service startup.

---

## File structure

| Path | Responsibility |
| --- | --- |
| `database/migrator/pom.xml` | Standalone executable migration/seed tool and Neo4j-Migrations pin. |
| `database/migrator/src/main/java/com/neo4flix/migrator/*` | CLI mode parsing, GDS probe, migration execution, schema verification, seed dispatch. |
| `database/migrations/V00*__*.cypher` | Append-only graph constraints and indexes. |
| `database/seeds/{demo,audit,load}/` | Versioned deterministic seed fixtures. |
| `backend/*-service/.../persistence/` | Service-owned SDN node/relationship mapping models and narrow projections. |
| `backend/platform-common/src/test/java/.../Neo4jGdsContainer.java` | Shared Testcontainers lifecycle and direct Cypher helper for integration tests. |
| `backend/*-service/src/test/java/.../persistence/` | Real schema/concurrency/mapping integration tests. |
| `infra/compose.yml` | Builds/runs the Java migrator instead of the Batch 0 GDS shell placeholder. |
| `scripts/{test-migrations,seed,smoke-compose}.ps1` and `Makefile` | Explicit migration, seed, integration, and empty-db verification commands. |

## Task 1: Introduce the migration module and canonical schema

**Files:**
- Create: `database/migrator/pom.xml`, `database/migrator/src/main/java/com/neo4flix/migrator/MigratorApplication.java`, `database/migrator/src/main/java/com/neo4flix/migrator/MigrationCommand.java`
- Create: `database/migrations/V001__core_node_constraints.cypher` through `V005__share_constraints.cypher`
- Modify: `pom.xml`, `infra/compose.yml`, `database/migrator/check-gds.sh`
- Test: `database/migrator/src/test/java/com/neo4flix/migrator/MigrationCommandTest.java`, `scripts/test-migrations.ps1`

**Interfaces:**
- Consumes: `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD` already supplied by Compose.
- Produces: executable `database-migrator` JAR accepting `migrate` and `verify`; a successful process exits 0 only after GDS and all schema versions verify.

- [ ] **Step 1: Write failing command-mode tests**

```java
@Test void rejectsUnknownModeWithoutConnecting() {
    assertThatThrownBy(() -> MigrationCommand.parse(new String[] {"reset"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("mode must be migrate, verify, seed-demo, seed-audit, or seed-load");
}

@Test void defaultsToMigrate() {
    assertThat(MigrationCommand.parse(new String[0]).mode()).isEqualTo("migrate");
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `./mvnw -pl database/migrator -am -Dtest=MigrationCommandTest test`

Expected: failure because the module and command type do not exist.

- [ ] **Step 3: Add the Maven module and minimal command parser**

Add `<module>database/migrator</module>` to the root reactor and pin `eu.michael-simons.neo4j:neo4j-migrations:4.1.2` in `database/migrator/pom.xml`. Implement:

```java
record MigrationCommand(String mode) {
  static MigrationCommand parse(String[] args) {
    String mode = args.length == 0 ? "migrate" : args[0];
    if (!Set.of("migrate", "verify", "seed-demo", "seed-audit", "seed-load").contains(mode)) {
      throw new IllegalArgumentException("mode must be migrate, verify, seed-demo, seed-audit, or seed-load");
    }
    return new MigrationCommand(mode);
  }
}
```

- [ ] **Step 4: Add the five Cypher migrations with exact stable names**

`V001__core_node_constraints.cypher` contains:

```cypher
CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (n:User) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT user_normalized_email_unique IF NOT EXISTS FOR (n:User) REQUIRE n.normalizedEmail IS UNIQUE;
CREATE CONSTRAINT movie_id_unique IF NOT EXISTS FOR (n:Movie) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT genre_id_unique IF NOT EXISTS FOR (n:Genre) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT genre_normalized_name_unique IF NOT EXISTS FOR (n:Genre) REQUIRE n.normalizedName IS UNIQUE;
```

`V002` creates `rated_key_unique` and `watchlisted_key_unique` relationship-property uniqueness constraints. `V003` creates explicit Movie title/year and Genre name lookup indexes. `V004` creates `auth_session_id_unique` and `auth_challenge_id_unique`. `V005` creates `recommendation_share_id_unique` and `recommendation_share_token_hash_unique`.

- [ ] **Step 5: Implement one-shot migrate/verify behavior and Compose wiring**

The application first executes the bounded GDS query, then invokes Neo4j-Migrations for classpath/filesystem `database/migrations`, then verifies each expected schema name by querying `SHOW CONSTRAINTS`/`SHOW INDEXES`. Replace the shell entrypoint in Compose with the built JAR image; preserve `depends_on: neo4j: service_healthy` and all business-service `service_completed_successfully` dependencies.

- [ ] **Step 6: Run module, config, and empty-db smoke checks**

Run:

```powershell
./mvnw -pl database/migrator -am test
pwsh -File scripts/test-migrations.ps1
docker compose --env-file .env.example -f infra/compose.yml config
```

Expected: tests pass; static migration test recognizes all versions/schema names; Compose resolves.

- [ ] **Step 7: Commit**

```bash
git add pom.xml database/migrator database/migrations infra/compose.yml scripts/test-migrations.ps1
git commit -m "feat: add Neo4j migration baseline"
```

## Task 2: Add owned persistence mappings and relationship keys

**Files:**
- Create: feature-first `persistence` packages in each service for the node/relationship models listed in the design.
- Test: one mapping metadata test per service plus `backend/rating-service/.../RatedRelationshipTest.java` and `backend/user-service/.../WatchlistedRelationshipTest.java`.

**Interfaces:**
- Consumes: Task 1 labels, properties, constraints, and graph direction.
- Produces: `RatedRelationship.keyFor(String userId, String movieId)` and `WatchlistedRelationship.keyFor(String userId, String movieId)` both return `userId + ":" + movieId`.

- [ ] **Step 1: Write failing relationship-key and ownership tests**

```java
@Test void ratingAndWatchlistKeysAreDeterministic() {
  assertThat(RatedRelationship.keyFor("u1", "m1")).isEqualTo("u1:m1");
  assertThat(WatchlistedRelationship.keyFor("u1", "m1")).isEqualTo("u1:m1");
}
```

Also assert that `@Node("User")`, `@Node("Movie")`, `@Node("Genre")`, and `@Node("RecommendationShare")` are declared only in their owning services and that relationship-property models expose `key` as a non-null persisted property.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./mvnw -pl backend/user-service,backend/movie-service,backend/rating-service,backend/recommendation-service -am test`

Expected: compile failure for absent persistence models.

- [ ] **Step 3: Implement minimal SDN persistence models**

Use `@Node`, `@Id`, and `@RelationshipProperties` with stable string UUID IDs. Keep constructor/accessor types explicit and never add controller DTO annotations. Define only properties listed in the graph spec and use `Instant`/Neo4j temporal mapping for timestamps.

- [ ] **Step 4: Run mapping tests and reactor verification**

Run: `./mvnw verify`

Expected: all Batch 0 tests still pass plus new mapping tests.

- [ ] **Step 5: Commit**

```bash
git add backend/user-service backend/movie-service backend/rating-service backend/recommendation-service
git commit -m "feat: add graph persistence mappings"
```

## Task 3: Build the real Neo4j/GDS integration harness

**Files:**
- Create: `backend/platform-common/src/test/java/com/neo4flix/platform/common/test/Neo4jGdsContainer.java`
- Create: `backend/platform-common/src/test/java/com/neo4flix/platform/common/test/Neo4jSchemaIntegrationTest.java`
- Modify: `backend/platform-common/pom.xml`, root `pom.xml`, `scripts/verify.ps1`, `Makefile`

**Interfaces:**
- Consumes: Task 1 migration JAR/files and expected schema names.
- Produces: reusable `Neo4jGdsContainer.start()` plus `runCypher(String query, Map<String,Object> parameters)` for integration tests; `make test-integration` runs these tests.

- [ ] **Step 1: Write failing empty-to-latest and rerun tests**

```java
@Test void emptyDatabaseMigratesAndRerunsSafely() {
  migrator.migrate();
  assertThat(schemaInspector.constraintNames()).contains("user_id_unique", "rated_key_unique");
  assertThat(migrator.migrate()).isEqualTo(MigrationResult.noPendingMigrations());
}

@Test void gdsVersionIsAvailable() {
  assertThat(cypher.singleString("RETURN gds.version() AS version")).matches("2026\\.07\\..+");
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./mvnw -pl backend/platform-common -Dtest=Neo4jSchemaIntegrationTest test`

Expected: failure because no Testcontainers fixture/migration runner exists.

- [ ] **Step 3: Implement the fixture with a pinned Neo4j image and GDS plugin setting**

Use `neo4j:2026.07.1-community`, configure the GDS plugin exactly as the local Compose development path does, wait for Bolt readiness, and pass the container URI/credentials only into process-local test configuration. Do not use the developer `.env` or fixed host ports.

- [ ] **Step 4: Wire explicit integration targets**

Add `test-integration` to the Makefile and a `-Integration` switch to `scripts/verify.ps1`; normal `make verify` includes integration coverage required by the architecture, while `-WhatIf` remains non-mutating and lists it.

- [ ] **Step 5: Run integration and wrapper checks**

Run:

```powershell
./mvnw -pl backend/platform-common -Dtest=Neo4jSchemaIntegrationTest test
pwsh -File scripts/verify.ps1 -WhatIf
make test-integration
```

Expected: real Neo4j/GDS migration proof passes; WhatIf lists the new stage; Make delegates without duplicating commands.

- [ ] **Step 6: Commit**

```bash
git add backend/platform-common pom.xml Makefile scripts/verify.ps1 scripts/verify.Tests.ps1
git commit -m "test: add Neo4j GDS integration harness"
```

## Task 4: Prove concurrent relationship uniqueness

**Files:**
- Create: `backend/rating-service/src/test/java/com/neo4flix/rating/persistence/RatedRelationshipConcurrencyIT.java`
- Create: `backend/user-service/src/test/java/com/neo4flix/user/persistence/WatchlistedRelationshipConcurrencyIT.java`
- Modify: shared integration helper only if Task 3 needs a transaction-scoped API.

**Interfaces:**
- Consumes: Task 1 relationship constraints and Task 3 container/Cypher helper.
- Produces: concurrency proof that exactly one edge remains for duplicate rating and watchlist attempts.

- [ ] **Step 1: Write failing concurrent-create tests**

```java
ExecutorService pool = Executors.newFixedThreadPool(2);
List<Future<WriteResult>> writes = pool.invokeAll(List.of(
    () -> createRated("u-1", "m-1", 5),
    () -> createRated("u-1", "m-1", 5)));
assertThat(writes.stream().filter(WriteResult::created).count()).isEqualTo(1);
assertThat(countRelationships("RATED", "u-1:m-1")).isEqualTo(1);
```

The companion watchlist test uses two `MERGE` writes and asserts two successful calls but exactly one `WATCHLISTED` relationship.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./mvnw -pl backend/rating-service,backend/user-service -Dtest=*ConcurrencyIT test`

Expected: failure because concurrent direct-Cypher helpers/test fixtures are absent.

- [ ] **Step 3: Implement parameterized transaction helpers and tests**

All Cypher uses `$userId`, `$movieId`, `$key`, and `$score` parameters. Rating uses `CREATE` and maps its constraint violation to a test `duplicate` result; watchlist uses `MERGE`. Do not introduce endpoints or application services in this batch.

- [ ] **Step 4: Run the real integration suite**

Run: `make test-integration`

Expected: both concurrency tests pass repeatedly; each graph has precisely one matching relationship.

- [ ] **Step 5: Commit**

```bash
git add backend/rating-service backend/user-service backend/platform-common
git commit -m "test: prove graph relationship uniqueness"
```

## Task 5: Add deterministic seed loaders and operational commands

**Files:**
- Create: `database/migrator/src/main/java/com/neo4flix/migrator/seed/{AuditSeedLoader,DemoSeedLoader,LoadSeedLoader}.java`
- Create: `database/seeds/audit/audit-fixture.json`, `database/seeds/demo/.gitkeep`, `database/seeds/load/.gitkeep`
- Create: `database/migrator/src/test/java/com/neo4flix/migrator/seed/AuditSeedLoaderIT.java`
- Modify: `Makefile`, `scripts/seed.ps1`, `docs/DEVELOPMENT.md`

**Interfaces:**
- Consumes: completed migration schema and `MigrationCommand` seed modes.
- Produces: `make seed-demo`, `make seed-audit`, `make seed-load`; audit loading returns fixed UUIDs and scores.

- [ ] **Step 1: Write a failing deterministic audit fixture test**

```java
@Test void auditSeedCreatesTheExpectedClusterExactlyOnce() {
  loader.load();
  assertThat(countNodes("User")).isEqualTo(3);
  assertThat(score("audit-alice", "audit-matrix")).isEqualTo(5);
  loader.load();
  assertThat(countRelationships("RATED")).isEqualTo(10);
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./mvnw -pl database/migrator -Dtest=AuditSeedLoaderIT test`

Expected: compilation failure for an absent loader/fixture.

- [ ] **Step 3: Implement explicit, parameterized, deterministic loaders**

Audit IDs and UTC timestamps are fixture constants. Insert nodes and relations with `MERGE` on their stable IDs, set only deterministic graph data, and create `RATED.key` with the shared `userId + ":" + movieId` convention. Demo/load commands may create only non-secret scaffolding in this batch; they never execute from normal Compose startup.

- [ ] **Step 4: Add safe operational wrappers and documentation**

`scripts/seed.ps1` requires one of `demo`, `audit`, `load`, starts no containers itself, and fails when the migrator command fails. Make targets invoke it. Document seed preconditions and that `make reset-db` is deferred until the deployment batch; do not add a destructive target early.

- [ ] **Step 5: Run loader and command checks**

Run:

```powershell
./mvnw -pl database/migrator test
pwsh -File scripts/seed.ps1 -WhatIf audit
make seed-audit
```

Expected: fixture test proves deterministic/idempotent data; WhatIf is non-mutating; audit seed loads into a migrated test/dev graph only when explicitly requested.

- [ ] **Step 6: Commit**

```bash
git add database/migrator database/seeds Makefile scripts/seed.ps1 docs/DEVELOPMENT.md
git commit -m "feat: add deterministic graph seed loaders"
```

## Task 6: Complete fresh Batch 1 acceptance evidence

**Files:**
- Create: `docs/audit/batch-1-verification.md`
- Modify: `scripts/smoke-compose.ps1`, `docs/reference/00_MASTER_EXECUTION_PLAN.md`

**Interfaces:**
- Consumes: completed migration, integration, and seed commands.
- Produces: fresh, secret-free evidence and Batch 1 `[x]` only after all gates and review pass.

- [ ] **Step 1: Add failing runtime smoke assertions for schema migration**

Extend the smoke script test to require migrator exit 0 and logs proving both GDS readiness and migration-to-latest success. It must query named constraint/index counts without printing credentials.

- [ ] **Step 2: Run the smoke test and confirm it fails before the script extension**

Run: `pwsh -File scripts/smoke-compose.ps1`

Expected: existing script lacks the migration/schema assertions.

- [ ] **Step 3: Implement and run the acceptance path**

Run:

```powershell
make test-integration
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600
pwsh -File scripts/smoke-compose.ps1
make seed-audit
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml down
```

Expected: empty schema migrates; rerun is safe; GDS and all services are healthy; named volume remains after down; deterministic audit seed succeeds.

- [ ] **Step 4: Record hygiene and full results**

Run:

```powershell
git status --short
git ls-files .env '*.pem' '*.key' '*token*'
git log --oneline 9e10b26..HEAD
```

Record exact timestamp, exit statuses, migration/schema evidence, GDS version, seed evidence, known environment limits, and all three Git outputs in `docs/audit/batch-1-verification.md`. Do not copy `.env` contents or secret values.

- [ ] **Step 5: Request final review and commit evidence**

Obtain spec-compliance and code-quality review. Resolve every defect and rerun affected checks before:

```bash
git add docs/audit/batch-1-verification.md
git commit -m "docs: record batch 1 verification"
```

- [ ] **Step 6: Update only Batch 1 after acceptance passes**

```bash
git add docs/reference/00_MASTER_EXECUTION_PLAN.md
git commit -m "docs: mark batch 1 complete"
```

Do not alter any other batch status.

## Plan self-review

- **Coverage:** Task 1 owns all versioned schema and the one-shot migration/GDS boundary; Task 2 owns persistence models; Tasks 3–4 prove actual migration and concurrency behavior; Task 5 separates deterministic seed loading; Task 6 enforces live acceptance, evidence, review, and status gating.
- **Scope:** No public endpoints, auth, recommendation scoring, or automatic production/demo seeding is introduced.
- **Contracts:** Migration modes, relationship key factory contract, shared Testcontainers helper, schema names, and seed commands are defined before consumers.
- **Placeholder scan:** No deferred implementation marker is present; every task has files, commands, expected results, and concrete test assertions.
