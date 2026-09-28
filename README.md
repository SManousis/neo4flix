# Neo4flix

[![Verify](https://github.com/Br0m0r/Neo4flix/actions/workflows/verify.yml/badge.svg)](https://github.com/Br0m0r/Neo4flix/actions/workflows/verify.yml)

Neo4flix is a graph-powered movie discovery and recommendation platform.
It combines four Spring Boot services, a shared Neo4j graph,
an Angular frontend, and an Nginx edge container into one runnable
local stack.

The project is intentionally an educational MVP: it demonstrates catalog search,
ratings, watchlists, authentication with optional TOTP, graph-based
recommendations, recommendation sharing, and an admin catalog workflow. It is
not a streaming service.

## Highlights

- **Graph-first recommendations** with human-readable explanations.
- **Service boundaries** for users, movies, ratings, and recommendations over a
  shared Neo4j graph.
- **Secure auth flow** with RS256 access tokens, rotating refresh cookies, rate
  limiting, and optional two-factor authentication.
- **Real verification** through Maven tests, Angular/Vitest tests, Playwright,
  k6 smoke profiles, Docker Compose smoke checks, and security wrappers.
- **Reproducible local setup** using pinned Java, Node, Maven, Angular, Neo4j,
  and Docker image versions.

## Architecture at a glance

```text
Angular + Nginx (8080)
          |
          +--> User Service (8081) --------+
          +--> Movie Service (8082) -------+--> Neo4j (7474 / 7687)
          +--> Rating Service (8083) ------+
          +--> Recommendation Service (8084)
```

The `database-migrator` container applies and verifies graph migrations before
the business services start. Movie Service delegates personalized ranking to
Recommendation Service rather than maintaining a second algorithm.

## Run the project locally

The supported local path uses Docker Compose for Neo4j, the four Spring Boot
services, and the Angular/Nginx frontend. Run these commands from the repository
root in PowerShell 7.

### Prerequisites

- Docker Desktop with Compose v2.17 or newer, running before the stack starts.
- Java 21 JDK (`JAVA_HOME` set), Node.js 24 LTS/npm 11, Git, and PowerShell 7.
- Network access for the first Maven/npm/Docker image build.

### Configure and start

Create the ignored local environment file, then replace every `change-me` and
`replace-with-*` value with local, non-production values. Protected runtime
routes require a matching RSA JWT key pair and a base64 32-byte TOTP key; the
repository's integration tests generate ephemeral keys in memory, but a live
Compose stack must be configured explicitly. Never commit `.env`, keys, tokens,
or passwords.

```powershell
Copy-Item .env.example .env
make dev-up
pwsh -NoProfile -File scripts/smoke-compose.ps1
```

Without GNU Make, use the equivalent Compose command:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600
```

Open <http://localhost:8080/>. Backend health endpoints are exposed on
`localhost:8081` through `localhost:8084`; Neo4j Browser is at
<http://localhost:7474/>. `make dev-down` stops and removes containers while
preserving the named Neo4j data volume. Do not use `down -v` unless you have
explicitly decided to destroy local graph data.

### Seed and verify

The seed script does not read `.env`; export the connection values in the shell
that invokes it, then load only the fixture you need:

```powershell
$env:NEO4J_URI = 'neo4j://localhost:7687'
$env:NEO4J_USERNAME = 'neo4j'
$env:NEO4J_PASSWORD = ((Get-Content .env | Where-Object { $_ -match '^NEO4J_PASSWORD=' } | Select-Object -First 1) -replace '^NEO4J_PASSWORD=', '')

& .\scripts\seed.ps1 audit
# Or, when GNU Make is installed: make seed-audit
```

Run the repository checks with Docker available:

```powershell
make verify
make verify-all       # full local gate: build, Compose smoke, browser, security, k6
make security
npm.cmd --prefix frontend run e2e   # requires an auth-configured live stack
```

The Maven wrapper and frontend scripts can also be run directly (`.\mvnw.cmd
test`, `npm.cmd --prefix frontend test`, `npm.cmd --prefix frontend run lint`,
and `npm.cmd --prefix frontend run build`).

### Backup and restore helpers

Backups require an explicit destination. Restore is intentionally guarded and
requires an explicit disposable container plus `-ConfirmRestore`; it refuses
the normal project Neo4j container unless `-AllowProjectContainer` is supplied.
Never restore over the project volume during normal development.

```powershell
pwsh -NoProfile -File scripts/backup-neo4j.ps1 -Destination .\backups\neo4j
pwsh -NoProfile -File scripts/restore-neo4j.ps1 -DumpFile .\backups\neo4j\neo4j-<timestamp>.dump -ContainerName neo4j-disposable -ConfirmRestore
```

See the [beginner learning guide](docs/learning/README.md), [documentation index](docs/README.md), [local development guide](docs/DEVELOPMENT.md), the [audit runbook](docs/audit/AUDIT_RUNBOOK.md),
the [01-edu audit question checklist](docs/audit/01-EDU_AUDIT_QUESTION_CHECKLIST.md),
the [stress-test notes](docs/audit/STRESS_TEST.md), and the [final status reconciliation](docs/audit/FINAL_STATUS.md) for the complete command
contracts and current limitations. The local profile is HTTP-only; deployment
HTTPS, a human usability session, and the full k6/backup-restore audit gates
remain separate release evidence.

## Repository reference

This repository also contains the canonical planning set used to implement the
01-edu **Neo4flix** project. Those documents are intentionally kept alongside
the runnable application so the design, implementation decisions, and audit
evidence remain inspectable.

The external assignment and audit remain non-negotiable requirements. This canonical set resolves their ambiguities into one implementable architecture while keeping the required technologies and behaviors intact.

## Start Here
There are two execution modes.

For clean-checkout setup, verification, and the local stack, see
[Local development](docs/DEVELOPMENT.md).

### A. Resume an Active Batch

If `docs/superpowers/ACTIVE_BATCH_CONTEXT.md` exists and its status is `ACTIVE`:

1. read `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`
2. read the referenced active implementation plan
3. read the referenced `.superpowers/sdd/...` ledger/workspace when present
4. inspect current git state relevant to the unfinished workstream
5. resume the first unfinished workstream

Do **not** restart full repository orientation, regenerate a valid plan,
redispatch completed work, or reread complete canonical specifications by default.

The active context is a cache only. Canonical specifications remain authoritative.

Open an exact canonical section when:

- the active context or plan is insufficient
- performing critical requirement verification
- resolving ambiguity
- investigating a suspected conflict

### B. Start a New Batch

If no active batch exists:

1. read this `README.md`
2. read `docs/reference/00_MASTER_EXECUTION_PLAN.md`
3. identify the first incomplete authorized batch
4. read only that batch's required canonical material
5. invoke `superpowers:writing-plans`
6. create the implementation plan under `docs/superpowers/plans/`
7. verify the shared `main` checkout is understood and free of unresolved conflicts
8. create/populate `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`
9. execute the batch with `superpowers:executing-plans`, TDD, focused tests, and lean review gates
10. commit logical checkpoints directly on `main` and stop at the batch checkpoint

The primary controller owns cross-document context.

Implementation workers and reviewers should receive compact task-specific
context instead of independently rediscovering the entire planning system.

## External Source of Truth

Official 01-edu subject:

- <https://github.com/01-edu/public/tree/master/subjects/java/projects/neo4flix>

Official audit:

- <https://github.com/01-edu/public/tree/master/subjects/java/projects/neo4flix/audit>

If the external subject/audit changes after this planning set was generated, do **not** silently implement the changed assignment. First compare the new requirement against the canonical set and explicitly reconcile it.

## Canonical Files

```text
README.md
docs/reference/00_MASTER_EXECUTION_PLAN.md
docs/reference/01_PRODUCT_SPEC.md
docs/reference/02_TECHNICAL_ARCHITECTURE.md
docs/reference/03_GRAPH_DATABASE_SPEC.md
docs/reference/04_API_SPEC.md
docs/reference/05_FRONTEND_SPEC.md
docs/reference/06_TESTING_SECURITY.md
docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md
docs/reference/08_DEPLOYMENT_OPERATIONS.md

CODEX_BOOTSTRAP_PROMPT.md          # helper, not a specification authority
MANIFEST.json

docs/
├── superpowers/
│   └── plans/
│       └── YYYY-MM-DD-batch-N-*.md
└── audit/                         # generated during implementation
```

## Document Authority

| Need | Read |
|---|---|
| What is/isn't Neo4flix MVP? | `docs/reference/01_PRODUCT_SPEC.md` |
| What technologies/repository/runtime shape are required? | `docs/reference/02_TECHNICAL_ARCHITECTURE.md` |
| What graph data exists and how is it constrained/migrated? | `docs/reference/03_GRAPH_DATABASE_SPEC.md` |
| What HTTP contract must be implemented? | `docs/reference/04_API_SPEC.md` |
| What routes/pages/components/UX must exist? | `docs/reference/05_FRONTEND_SPEC.md` |
| What test and security proof is mandatory? | `docs/reference/06_TESTING_SECURITY.md` |
| How must the recommendation engine and 01-edu audit be demonstrated? | `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md` |
| How is the stack run, deployed, backed up, restored, and smoke-tested? | `docs/reference/08_DEPLOYMENT_OPERATIONS.md` |
| What do we implement next? | `docs/reference/00_MASTER_EXECUTION_PLAN.md` |

## Pinned MVP Choices

The assignment intentionally leaves many implementation details open. To prevent independent coding agents from making incompatible choices, this set pins:

- Java 21 LTS
- Spring Boot 4.1.1
- Spring MVC (imperative), not WebFlux
- Spring Security + OAuth2 Resource Server support for JWT validation
- Spring Data Neo4j 8.1.x / version managed compatibly by Spring Boot
- Neo4j Community 2026.07.x, deployment baseline `2026.07.1`
- Neo4j Graph Data Science 2026.07
- Maven + Maven Wrapper
- Angular 22.1.5 + Angular Material 22.1.5 + SCSS
- Node.js 24 LTS + TypeScript 6.0.x
- Angular standalone components + Signals/services; no NgRx
- JUnit 5 + Mockito + AssertJ
- Testcontainers Neo4j for real graph integration tests
- Vitest for Angular unit/component tests
- Playwright for browser E2E
- k6 for load/stress tests
- springdoc-openapi 3.1.x
- Neo4j-Migrations 4.1.x as a **one-shot migrator**, not service-owned startup migration
- Bucket4j 8.19.x for in-memory MVP rate limiting
- RFC 6238 TOTP with `java-otp` + ZXing QR generation
- RS256 short-lived access JWT + rotating opaque refresh token
- access token in Angular memory; refresh token in Secure/HttpOnly/SameSite cookie
- Nginx for Angular static hosting, TLS termination, and `/api/v1` reverse proxy
- Docker Compose; no Kubernetes
- four required business microservices only: User, Movie, Rating, Recommendation
- one shared Neo4j graph with strict service mutation ownership
- synchronous REST between Movie Service and Recommendation Service; no broker

Dependency lockfiles and Docker image tags must pin exact patch releases in the repository. Do not use floating `latest` in release paths.

## Core Reconciliation Decisions

The source assignment has overlapping responsibilities. The canonical interpretation is:

1. **Recommendation ownership:** Recommendation Service owns the personalized recommendation algorithm. Movie Service exposes a required recommendation facade (`/movies/recommended`) and related-movie queries, but delegates personalized ranking instead of implementing a second algorithm.
2. **Rating ownership:** Rating Service is the only writer of `RATED`. User Service may expose the user's rating history as a read facade to satisfy its user-centric responsibility, but does not independently mutate ratings.
3. **Recommendation CRUD wording:** Generated recommendations are computed results, not fake stored CRUD entities. Recommendation Service owns real CRUD for `RecommendationShare`, which also implements “share recommendations with friends.”
4. **Graph database:** all four services share one Neo4j graph to preserve traversability; this is an educational project-specific choice, not a claim that database-per-service is generally wrong.
5. **Movie dates:** `releaseYear` is required; `releaseDate` is optional. Never fabricate exact dates for imported datasets.
6. **Rating semantics:** one integer 1–5 rating per user/movie; `POST` creates and conflicts if one exists, `PUT` updates, `DELETE` removes. Do not silently turn POST into upsert.
7. **Concurrency:** `RATED` and `WATCHLISTED` carry deterministic relationship keys and use relationship property uniqueness constraints so concurrent requests cannot create duplicates. Application behavior is also tested under concurrency.
8. **2FA enrollment:** pending TOTP enrollment state is distinct from active TOTP state; active login 2FA cannot be enabled until confirmation succeeds.
9. **Deletion cleanup:** deleting a User or Movie must explicitly remove owned/session/share nodes that would otherwise be orphaned; `DETACH DELETE` alone is not sufficient for all graph-owned nodes.
10. **Schema ownership:** no business service independently applies graph migrations; the one-shot migrator owns schema evolution.

## Product Invariants

- Neo4flix is a movie discovery/recommendation app, not a streaming platform.
- Roles are `USER` and `ADMIN`.
- No formal friend/follower network in MVP; sharing is link-based.
- Explicit ratings are the primary preference signal; watchlisting is not treated as equivalent positive feedback.
- Personalized recommendations use graph behavior, not a hardcoded genre list.
- Already-rated movies are excluded from personalized recommendation results.
- Recommendations provide a human-readable reason.
- Admin owns catalog mutation; users own their profile, ratings, watchlist, and shares.
- Backend authorization is authoritative; Angular guards are UX only.
- Raw passwords, refresh tokens, TOTP secrets, JWT private keys, and security codes must never leak through APIs/logs.

## Superpowers Execution Policy

```text
Canonical specs
      ↓
00_MASTER_EXECUTION_PLAN batch
      ↓
superpowers:writing-plans
      ↓
docs/superpowers/plans/<batch-plan>.md
      ↓
shared main checkout + compact active context
      ↓
superpowers:executing-plans
      ↓
TDD → focused tests → lean review when needed
      ↓
one full batch verification
      ↓
Batch evidence report
      ↓
HUMAN CHECKPOINT
      ↓
Next batch
```

Current Superpowers SDD also maintains plan-scoped progress/workspace artifacts under its `.superpowers/sdd/...` workspace. Use that ledger when the installed skill provides it; do not rely on chat memory alone for task completion state.

If implementation exposes a genuinely new product/design decision, use `superpowers:brainstorming`, update the owning canonical spec after approval, and then revise downstream plans. Do not use brainstorming to reopen already-approved choices merely because an agent prefers another stack.
