# 11. Database migrations and seed data

A database changes as an application evolves. New labels, properties,
constraints, and indexes must be introduced consistently. Neo4flix handles
these changes with ordered **migrations** rather than asking each service to
modify the graph during startup.

## What a migration is

A migration is a versioned file describing one schema change. Neo4flix stores
them in [`database/migrations`](../../database/migrations/):

```text
V001__core_node_constraints.cypher
V002__relationship_uniqueness.cypher
V003__search_indexes.cypher
V004__auth_support_constraints.cypher
V005__share_constraints.cypher
V006__auth_token_hash_constraints.cypher
```

The numeric prefix defines the order. The descriptive suffix explains the
purpose.

Examples include unique movie IDs, unique normalized emails, rating/watchlist
relationship keys, catalog search indexes, and authentication/share token
constraints.

## Why services do not migrate independently

If four services attempted schema updates at the same time, startup order and
ownership would become unclear. Neo4flix gives one program—Database Migrator—
exclusive responsibility for schema evolution.

The Compose startup sequence is:

```text
Neo4j healthy
  -> database-migrator runs "migrate"
  -> migration succeeds and migrator exits with code 0
  -> business services may start
```

The Java entry point is
[`MigratorApplication`](../../database/migrator/src/main/java/com/neo4flix/migrator/MigratorApplication.java).

## Idempotency

A migration system records which versions have already been applied. Running
the migrator again should not destructively recreate the database. This
property is called **idempotency**: repeating the operation has the same final
effect as running it once.

An important release check starts from an empty database, migrates to the latest
version, verifies all schema objects, and then runs migration again.

## What seed data is

A **seed** deliberately inserts known data. It is useful for development,
demonstrations, automated tests, and load testing. A seed is different from a
migration: migrations establish schema rules, while seeds create example data.

Neo4flix defines three seed categories:

| Seed | Purpose |
| --- | --- |
| `demo` | Human-friendly data for local development |
| `audit` | Small deterministic graph for the 01-edu demonstration |
| `load` | Larger deterministic data for stress testing |

The audit fixture is stored in
[`audit-fixture.json`](../../database/seeds/audit/audit-fixture.json) and loaded
by
[`AuditSeedLoader`](../../database/migrator/src/main/java/com/neo4flix/migrator/seed/AuditSeedLoader.java).

## Why deterministic data matters

**Deterministic** means the same input produces the same known graph. A
recommendation test can therefore say that a particular user should receive a
particular candidate for a specific reason.

Random or constantly changing demo data would make failures hard to reproduce
and explanations hard to audit.

## Loading the audit fixture

With a migrated local Neo4j container running:

```powershell
$env:NEO4J_URI = 'neo4j://localhost:7687'
$env:NEO4J_USERNAME = 'neo4j'
$env:NEO4J_PASSWORD = ((Get-Content .env | Where-Object { $_ -match '^NEO4J_PASSWORD=' } | Select-Object -First 1) -replace '^NEO4J_PASSWORD=', '')

& .\scripts\seed.ps1 audit
```

[`seed.ps1`](../../scripts/seed.ps1) checks that the connection variables exist,
builds the migrator, and runs its `seed-audit` mode. It does not read `.env`
automatically and does not start Docker containers.

## Inspecting the seeded graph

After loading the audit seed, open Neo4j Browser at
<http://localhost:7474/>. The read-only queries in
[`GRAPH_DEMO.md`](../audit/GRAPH_DEMO.md) show:

- users, movies, and genres;
- rating scores stored on relationships;
- genre connections;
- shared-rating peer overlap; and
- the GDS cosine-similarity function.

Do not put real credentials or personal data into seed files. Do not run
destructive Cypher against a graph you intend to preserve.

## Recap

Versioned migrations establish the graph schema in a controlled order. The
one-shot migrator owns this process. Seeds then add deliberate datasets for
development, audit demonstrations, or load testing, with deterministic data
making behavior reproducible.

Next: [Chapter 12 explains the test and audit layers](12-testing-the-project.md).
