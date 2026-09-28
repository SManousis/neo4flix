# Batch 0 verification evidence

Status: **PASS — all Batch 0 acceptance gates completed.** Batch 0 is eligible for the human checkpoint after the separately committed master-plan status update.

## Run context

- Fresh runtime acceptance completed on 2026-09-09 (Europe/Athens) from the isolated `batch-0-repository-bootstrap` worktree.
- Docker Server: 29.6.1.
- The canonical `scripts/verify.ps1` verification passed against the current source with Temurin Java 21.0.11. It completed Maven verification, `npm ci`, lint, tests, production build, and Compose configuration validation.
- Environment values are intentionally omitted. The ignored local `.env` was used only as an input to Compose; no credentials, tokens, or passwords are recorded here.

## Timestamped scope and source acceptance records

Recorded at **2026-09-09 12:23:17 +03:00** (Europe/Athens):

| Command | Exit status | Exact result |
| --- | --- | --- |
| `rg -n "spring-boot-starter-webflux|kafka|rabbitmq|redis|graphql|kubernetes|spring-cloud|spring-data-jpa|ngrx" pom.xml backend frontend infra` | 1 (expected) | No matches. |
| `rg -n "latest" infra frontend backend pom.xml` | 1 (expected) | No matches. |
| `scripts/verify.ps1` using Java 21.0.11 | 0 | Maven verification, npm install/lint/test/build, and Compose configuration all completed successfully. |
| Final whole-branch review and scoped re-review after the Docker Node pin correction | 0 | Approved. |

The Docker frontend build pin is `node:24.15.0-alpine3.22`, matching the locked Angular 22.1.5 toolchain's supported Node 24 range.

## Timestamped live Compose and GDS acceptance records

The development override is required for host-port smoke checks. On 2026-09-09 (Europe/Athens), the live stack was started with:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up -d --wait --wait-timeout 600
```

Exit status: 0.

Observed service status:

| Service | Observed state |
| --- | --- |
| `neo4j` | running healthy |
| `user-service` | running healthy |
| `movie-service` | running healthy |
| `rating-service` | running healthy |
| `recommendation-service` | running healthy |
| `web` | running healthy |
| `database-migrator` | exited with ExitCode 0 |

`scripts/smoke-compose.ps1` exited 0 (**smoke pass**) and recorded these exact successful observations:

```text
Service on ports 8081 is UP
Service on ports 8082 is UP
Service on ports 8083 is UP
Service on ports 8084 is UP
GDS readiness check succeeded.
```

The web endpoint was reachable. An explicit read-only query also succeeded:

```cypher
RETURN gds.version()
```

Result: `2026.07.0`.

The stack was stopped with:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml down
```

Exit status: 0. The shutdown command did not use a volume-removal flag, and the named `neo4flix_neo4j-data` volume remained present.

## Timestamped repository hygiene records

Recorded at **2026-09-09 12:23:17 +03:00** (Europe/Athens). The following are the exact required Git command outputs; blank code blocks are intentionally blank because the commands produced no output.

`git status --short` (exit 0):

```text

```

`git ls-files .env '*.pem' '*.key' '*token*'` (exit 0):

```text

```

`git log --oneline 72d285c..HEAD` (exit 0):

```text
583ed0a docs: mark batch 0 complete
8e9371c docs: record batch 0 verification
b5254fd docs: record batch 0 verification
0da53ac fix: align Docker Node pin with Angular requirements
20ba777 build: add baseline verification workflow
630047d infra: preserve LF for shell entrypoints
88f8d1e infra: add baseline Compose topology
8557727 fix: add signal-backed application state service
3fbc5bd feat: add Angular application baseline
4c34346 fix: package services as executable jars
3348b0a feat: add four service health baselines
2dd35ba test: make wrapper mirror proof JDK discovery portable
bb8b054 build: use standard Maven wrapper
cfd85a2 build: correct wrapper mirror paths
ca5d554 build: regenerate reliable Maven wrapper
df94d6d feat: add HTTP request and error baseline
774c2a8 build: bootstrap Maven reactor
cf8298d chore: ignore local worktrees
6387883 docs: add batch 0 implementation plan
c4cbdd3 docs: add batch 0 bootstrap design
```

No tracked `.env`, PEM, key, or token-named files were found by the Task 7 hygiene scan. No secrets were added to this evidence.

## Completion gate

All source, runtime, GDS, smoke, shutdown, hygiene, and review gates have passed. Batch 0 may remain marked complete, with no later batch status changed.
