# Neo4flix Audit Runbook

This runbook prepares the existing Neo4flix graph for an evaluator walkthrough. It uses the repository’s deterministic audit fixture and does not change application code or delete Neo4j data.

The official 01-edu audit questions are reproduced as an executable local
checklist in [01-EDU_AUDIT_QUESTION_CHECKLIST.md](01-EDU_AUDIT_QUESTION_CHECKLIST.md).
Use that checklist after the setup below; it maps each question to a live action,
supporting test, and the evidence that should be recorded.

## Prerequisites

- Windows PowerShell from the repository root.
- Docker Desktop running.
- Java 21 and the repository Maven wrapper available.
- Local secret injection for `NEO4J_URI`, `NEO4J_USERNAME`, and `NEO4J_PASSWORD`.

The seed script reads those three variables from the current process. It does not read `.env`, print credentials, or start containers. Keep the values in a local secret mechanism and never paste them into this runbook or a terminal transcript.

## Start and verify the stack

Use the existing Compose files and preserve the current volumes:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up -d --wait --wait-timeout 600
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml ps
```

Record only service names and health states. Do not print the `.env` file or container environment. The stack entry point is `http://localhost:8080`.

## Apply migrations and load the audit fixture

The migrator command is idempotent and uses parameterized `MERGE` statements:

```powershell
.\scripts\seed.ps1 audit
```

The script builds `database/migrator`, then runs the `seed-audit` mode. It must be run only after the three Neo4j variables have been injected into the current PowerShell process. It must not be given credentials as command-line arguments.

## Deterministic fixture contract

The source of truth is `database/seeds/audit/audit-fixture.json`, loaded by `AuditSeedLoader`.

| Entity or relationship | Expected count |
|---|---:|
| `User` nodes | 6 |
| `Movie` nodes | 8 |
| `Genre` nodes | 4 |
| `IN_GENRE` relationships | 11 |
| `RATED` relationships | 14 |

The stable demonstration personas are `audit-alice`, `audit-bob`, `audit-carol`, `audit-fresh`, `audit-sparse`, and `audit-negative`. Re-running `seed-audit` must preserve these counts; it must not create duplicate rating relationships.

## Read-only graph checks

Run these in Neo4j Browser or an authenticated `cypher-shell` session. The queries are read-only and use parameters:

```cypher
MATCH (u:User) RETURN count(u) AS users;
MATCH (m:Movie) RETURN count(m) AS movies;
MATCH (g:Genre) RETURN count(g) AS genres;
MATCH ()-[:IN_GENRE]->() RETURN count(*) AS movieGenres;
MATCH ()-[r:RATED]->() RETURN count(r) AS ratings;
```

```cypher
:param userId => '11111111-1111-1111-1111-111111111111';
MATCH (u:User {id: $userId})-[r:RATED]->(m:Movie)
RETURN m.id AS movieId, m.title AS title, r.score AS score
ORDER BY m.id;
```

Expected counts are the fixture contract above. The Alice query should show four ratings and must not expose password or token properties.

## Backend fixture proof

Run the focused Testcontainers proof from the repository root:

```powershell
.\mvnw.cmd -pl database/migrator -am -Dtest=AuditSeedLoaderIT test
```

The expected result is two passing integration tests with zero failures in the current migrator module. Record the actual result in the batch audit; do not replace fresh output with an earlier run.

## Walkthrough handoff

After the seed and read-only checks, continue with:

1. [GRAPH_DEMO.md](GRAPH_DEMO.md) for graph and GDS evidence.
2. [RECOMMENDATION_EXPLANATION.md](RECOMMENDATION_EXPLANATION.md) for strategy and score interpretation.
3. The running web entry point for the user-facing catalog, rating, recommendation, watchlist, and share walkthrough.

## Cleanup and safety

Keep the graph available for the walkthrough, or stop containers without deleting volumes:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml stop
```

Do not run `docker compose down -v`, remove Neo4j volumes, reset the database, print credentials, or commit `.env`. If a clean fixture is required, use an isolated Neo4j database/container rather than destroying the shared project volume.
