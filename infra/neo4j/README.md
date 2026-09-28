# Neo4j and GDS bootstrap

This directory documents the Neo4j side of the local Neo4flix stack. It is
useful when diagnosing startup, schema, or graph-data-science issues; the normal
developer path remains the [local development guide](../../docs/DEVELOPMENT.md).

Batch 0 uses `neo4j:2026.07.1-community` and the development convenience setting
`NEO4J_PLUGINS=["graph-data-science"]`. Neo4j downloads the compatible GDS plugin
at startup, so this development stack needs network access. The intended
compatibility pair is Neo4j 2026.07.x and GDS 2026.07. The plugin artifact is not
yet pinned or packaged: release/audit must use a deterministic image containing
the verified GDS artifact and must not download it at startup.

The one-shot `database-migrator` uses the same Neo4j image. After Neo4j health
passes, its read-only script runs exactly `RETURN gds.version();`, with at most
30 attempts, a 10-second limit per query, and two seconds between attempts.
It reports readiness only and never prints credentials or query output. This
is a Batch 0 readiness placeholder, not a schema migration runner. Batch 1 owns
real migrations. A failed check blocks all four business services; web waits
for those services to become healthy.

The named `neo4j-data` volume persists `/data` across normal `docker compose down`.
Do not use `down -v` unless you intend to delete the development database.
Credentials come from the ignored `.env`; Neo4j's initial username must be
`neo4j`. Changing `.env` does not rotate a password in an existing data volume.

From the repository root, copy `.env.example` to `.env`, replace placeholder
values with local non-production values, then run:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600
pwsh -File scripts/test-compose-config.ps1
pwsh -File scripts/smoke-compose.ps1
```

Both Compose files are required for the local smoke checks. The base topology
publishes web on localhost:8080 only. `compose.dev.yml` adds localhost-only
service ports 8081-8084, Browser 7474, and Bolt 7687. Do not include the dev
override in deployments. Container clients use `bolt://neo4j:7687`; `.env`'s
host URI is for processes running outside the Compose network.

Backend images build the selected module with the repository's Maven 3.9.11
wrapper and pinned Temurin 21.0.11+10 JDK/JRE Alpine 3.22 stages. Runtime images
contain only the application JAR and run as UID 10001. The web image uses the
pinned Node build stage and unprivileged Nginx runtime. Docker Compose 2.17+
is required for inline backend Dockerfiles. TLS and release GDS packaging are
future deployment work; this bootstrap is for local development.
