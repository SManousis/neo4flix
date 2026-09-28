# Neo4flix audit report

**Last reviewed:** 2026-09-24
**Scope:** repository source, documentation, automated checks, runtime
evidence, and the official 01-edu audit questions.
**Official question source:** [01-edu Neo4flix audit](https://github.com/01-edu/public/tree/master/subjects/java/projects/neo4flix/audit)

This is the working reconciliation checklist. It records what is verified,
what is only partially evidenced, and what still needs an environment or human
action. It does not replace the detailed batch documents or the runbook.

## Scope decision: local-only project

Neo4flix is intentionally staying as a local educational/showcase project. It
will not be deployed to a public host or operated as a production service.
Therefore, production deployment and release gates are **not applicable**, not
failed. The audit still covers the local Compose runtime, local security
controls, migrations and reseeding, API/browser behavior, bounded local load,
and reproducibility of the build and test commands.

The following are N/A by this scope: public-ingress HTTPS certificates and
redirects, HSTS/secure-cookie proof over a deployed HTTPS endpoint,
deployment-scale SLO and profiling evidence, and production/release-image or
production GDS-packaging evidence. The full Maven reactor classpath issue is
still in scope because it affects local reproducibility and verification.

## Status legend

- `[x]` Verified by current evidence.
- `[~]` Implemented or partly evidenced, but the acceptance gate is incomplete.
- `[!]` Blocked by the current environment or a missing external prerequisite.
- `[ ]` Not yet done.
- `[N/A]` Not applicable to the declared local-only project scope.

## Executive verdict

The repository contains a substantial, connected implementation: Angular
frontend, Spring Boot services, Neo4j migrations and graph queries, JWT/refresh
authentication, TOTP 2FA, ratings, watchlists, sharing, recommendations,
security controls, test fixtures, and operational scripts.

For the declared local-only scope, the live local runtime gates are
substantially verified. The remaining in-scope work is evidence and
reproducibility cleanup rather than an unimplemented core feature:

1. Docker/Neo4j reset, migrations, Compose health, audit seeding, browser E2E,
   bounded local k6 smoke, and targeted recommendation checks are freshly
   verified.
2. A real participant usability session has now been completed manually; its
   result should be recorded if detailed notes are desired.
3. Final document reconciliation and optional scanner review remain.
4. Production deployment, public HTTPS, release-image, and deployment-scale
   performance gates are N/A by the scope decision above.

Do not describe the N/A production gates as project failures. Close the
remaining in-scope checklist items with dated evidence before calling the
local audit fully reconciled.

## Repository and execution snapshot

- [x] Current branch is `main`; Gitea `origin/main` and GitHub `main` are
  aligned at merge commit `92ed4c7` after preserving GitHub's independent
  README update without force-pushing.
- [x] Current repository head is `92ed4c7` (merged verified audit/code update).
- [~] The worktree contains a pre-existing modified `.env.example` and
  `frontend/angular.json`; preserve them and do not push the `.env.example`
  values as project secrets. This report does not reset or discard local work.
- [x] No obvious application-code `TODO`, `FIXME`, `TBD`, or unimplemented
  markers were found. Matches for `pending` in the code are legitimate state
  names; pending/open statements in audit docs are listed below as actual
  follow-up work.
- [~] The documented prerequisite is JDK 21. Current diagnostic runs used
  JDK 26 (`C:\Program Files\Java\jdk-26.0.1`), so those runs are useful
  evidence but are not an exact JDK-21 acceptance run.
- [x] Docker Desktop's Linux engine was available for this pass: Docker Server
  29.6.1 and Testcontainers connected through the local named pipe.

## Fresh verification evidence

### Green checks

- [x] Frontend Vitest suite: **27 files, 105 tests passed**.
- [x] Frontend production build completed successfully.
- [x] Frontend lint completed successfully.
- [x] Static reactor-layout, migration, and security-header contracts passed.
- [!] `test-compose-config.ps1` is blocked by the pre-existing local
  `.env.example` edit: the contract expects placeholder key values, while that
  file currently contains local values. The live Compose configuration itself
  validated and started successfully.
- [x] Security wrapper completed successfully. `npm audit` reported **0
  vulnerabilities**.
- [x] Pester wrapper suite: **6 passed, 0 failed** across
  `scripts/verify.Tests.ps1` and `scripts/security.Tests.ps1`.
- [x] Maven wrapper repository-path test passed when run with normal network
  access (`scripts/test-wrapper-repository-paths.ps1`).
- [x] Full Maven `verify` passed after the reactor fix: all backend modules
  compiled, integration tests ran with Testcontainers, and every reported test
  completed with zero failures or errors. The run used JDK 26 because JDK 21 is
  not installed on this host.
- [x] Executable service JAR smoke passed for user, movie, rating, and
  recommendation services after the same build.
- [x] The updated database-migrator Docker image built successfully with the
  executable JAR selected explicitly, and `scripts/seed.ps1 audit` succeeded
  with the new plain-JAR packaging present.
- [x] Direct recommendation verification passed: 3 tests (golden fixture and
  query-plan integration tests) with Docker/Testcontainers, including the
  documented test-phase reactor command after the plain-JAR lifecycle fix.
- [x] User/rating runtime-driver contract passed and both packaged JARs include
  Neo4j Java Driver 6.2.0.
- [x] Fresh k6 smoke passed: 15 iterations, 30 requests, 100% checks, 0%
  request failures, and p95 HTTP duration 17.05 ms.
- [x] Compose smoke passed after a clean volume reset and audit reseed:
  Neo4j, migrations, GDS, four services, and web were healthy.
- [x] Browser E2E passed **8 tests**, with 1 intentionally skipped.

### Checks that are blocked or incomplete

- [x] The full-reactor recommendation classpath issue is fixed by attaching a
  plain `database-migrator` JAR for test consumers and selecting the executable
  JAR explicitly in the Neo4j integration harness. Full `verify` and executable
  service-JAR smoke now pass.
- [x] Compose startup, health checks, live API smoke, Neo4j audit seeding, GDS
  verification, and browser E2E were freshly verified.
- [~] OWASP Dependency-Check, Gitleaks, and Trivy were not installed; the
  security wrapper records them as skipped. Install them before claiming those
  optional scanner gates.
- [x] Bounded local load evidence is available from k6. Sustained
  deployment-scale concurrency, profiling, SLO interpretation, and release
  packaging are N/A for this local-only project; local reproducibility remains
  covered by the checks below.

## Official audit-question matrix

The statuses below distinguish implementation evidence from final acceptance
evidence. A static code answer is not promoted to a live acceptance pass when
the required runtime or human evidence is missing.

### Functional application and navigation

- [x] Core routes and page structure exist for login, registration, 2FA,
  profile, home/search, movie details, ratings, watchlist, recommendations,
  sharing, and admin flows.
- [~] Search, details, release date, genre, rating, rating-page, watchlist,
  sharing, and recommendations are represented in code and tests.
- [x] Live browser acceptance passed for the implemented journey: 8 E2E tests
  passed and one test was intentionally skipped.
- [x] The user confirmed that the manual human audit/usability checks were
  completed. Detailed participant notes can be added to
  `docs/audit/USABILITY_TEST.md` if a formal record is required.

### Graph model and Neo4j behavior

- [x] Migrations, nodes, relationships, indexes/constraints, and graph query
  code are present; migration and live Compose checks pass. The static Compose
  contract is separately blocked by the local `.env.example` edit noted above.
- [x] Recommendation logic, GDS/Cypher integration, query plans, and golden
  fixture output passed against disposable Neo4j containers.
- [x] The reset-and-reseed runtime verified six migrations, 11 named
  constraints, 3 named ONLINE indexes, and GDS `2026.07.0`.

### Services and API behavior

- [x] The service layout and shared platform module are present; Compose smoke
  and browser E2E exercised the live API path.
- [x] User, movie, rating, recommendation, authentication, sharing, and
  watchlist behavior has live/static evidence; the full Maven reactor and
  Testcontainers verification now pass.

### Security and privacy

- [x] JWT/RS256, refresh-cookie handling, password policy, TOTP 2FA,
  request IDs, rate limiting, security headers, and sensitive-data controls
  are implemented and covered by static/security checks.
- [x] `npm audit` currently reports zero vulnerabilities.
- [~] Optional Dependency-Check/Gitleaks/Trivy evidence is absent because the
  tools are not installed.
- [N/A] HTTPS certificates, HTTP-to-HTTPS redirect, secure-cookie behavior over
  a deployed HTTPS endpoint, HSTS, and public-ingress proof are not applicable
  because the project will not be deployed. Local HTTP security headers,
  JWT/refresh-cookie behavior, password policy, and 2FA remain in scope.

### Reliability, stress, and release readiness

- [x] Bounded local k6 smoke passed with 0% request failures. Sustained
  deployment-scale targets, profiling, and SLO interpretation are N/A by
  scope.
- [x] Clean/empty-volume startup, migrations, reseeding, and healthy service
  state were verified during this audit pass.
- [N/A] Production/release image path and deterministic production GDS
  packaging are not applicable to a local-only project. The local Compose
  image path remains covered by the runtime smoke checks.
- [x] Final cross-document reconciliation and definition-of-done review is
  complete for the current audit snapshot. `FINAL_STATUS.md`, this report,
  `TEST_EVIDENCE.md`, `SECURITY_CHECKLIST.md`, `STRESS_TEST.md`, and the
  active-batch context agree on the local-only scope and current evidence;
  older batch ledgers intentionally retain their historical dates.

## Remaining work checklist, in priority order

### P0 — restore a verifiable runtime

- [x] Start Docker Desktop using the Linux engine.
- [x] Confirm `docker info` succeeds and that Testcontainers can create a
  disposable Neo4j container.
- [x] Replace the temporary ignored `.env` from the example, reset only
  `neo4flix_neo4j-data`, and create a fresh database. Keep the generated key
  material local and do not commit it.
- [x] Validate Compose configuration, start the stack, and wait for all
  health checks:

  ```powershell
  docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml config -q
  docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600
  docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml ps
  ```

- [x] Seed the official audit fixture and run the smoke path:

  ```powershell
  $env:NEO4J_URI = 'neo4j://localhost:7687'
  $env:NEO4J_USERNAME = 'neo4j'
  $env:NEO4J_PASSWORD = ((Get-Content .env | Where-Object { $_ -match '^NEO4J_PASSWORD=' } | Select-Object -First 1) -replace '^NEO4J_PASSWORD=', '')
  & .\scripts\seed.ps1 audit
  pwsh -NoProfile -File scripts/smoke-compose.ps1 -EnvFile .env
  ```

- [x] Run the browser journey and record the result:

  ```powershell
  npm.cmd --prefix frontend run e2e
  ```

- [~] Full Maven/Testcontainers verification now passes with the classpath fix;
  the exact documented JDK-21 run remains an environment prerequisite because
  this host only has JDK 26:

  ```powershell
  $env:JAVA_HOME = 'C:\Path\To\jdk-21'
  .\mvnw.cmd verify
  ```

- [x] Run the targeted recommendation golden-fixture and query-plan tests:

  ```powershell
  .\mvnw.cmd -pl backend\recommendation-service -am '-Dtest=RecommendationGoldenFixtureIT,RecommendationQueryPlanIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
  ```

### P1 — close audit and release gates

- [x] The user confirmed completion of the manual human audit/usability checks.
  Add detailed task outcomes to `docs/audit/USABILITY_TEST.md` only if a
  formal participant record is needed.
- [N/A] Deployment-scale load, profiling, SLO, and public-ingress evidence are
  not required for the declared local-only scope. The bounded local k6 smoke
  is already recorded above.
- [N/A] Production/release images and production GDS packaging are not
  required because the project will not be deployed.
- [N/A] HTTPS/TLS/HSTS/public-ingress evidence is not required because there
  is no deployed endpoint.
- [x] A clean/empty Neo4j volume performed migrations and reached healthy
  service state during the fresh reset-and-reseed pass.
- [~] Dependency-Check, Gitleaks, and Trivy are not installed. The security
  wrapper was rerun successfully with npm audit at zero vulnerabilities; the
  three optional external scanners remain an explicitly documented local
  tooling gap.
- [x] Reconcile `docs/audit/FINAL_STATUS.md`: date, verified commit, local-only
  scope, current runtime evidence, and remaining in-scope gates are updated.
- [x] Current runtime command output and evidence links are consolidated in
  `TEST_EVIDENCE.md`, `FINAL_STATUS.md`, and the canonical checklist pages.
  Raw logs, screenshots, generated reports, and local secrets remain
  untracked by design.

### P2 — documentation and showcase polish

- [x] Beginner study guide exists under `docs/learning/` and is linked from
  the documentation indexes.
- [x] Root and docs indexes describe the project and local quick start.
- [x] Dated/current evidence is linked from this report and
  `FINAL_STATUS.md`; historical batch ledgers remain archival records rather
  than being rewritten retroactively.
- [ ] Keep generated reports, secrets, database volumes, and local `.env`
  values out of commits.

## Definition of done for the next audit pass

Do not close this report until all of the following are checked:

- [x] Full Maven/Testcontainers integration verification passes with the
  recommendation classpath fix; the run used JDK 26 and the JDK-21 prerequisite
  remains an environment note.
- [x] Compose starts from the documented setup, all health checks pass, and
  the audit fixture is reproducible.
- [x] Browser E2E and the manually completed official functional questions have
  dated/current evidence.
- [x] Recommendation golden-fixture, query-plan, migration, and graph-integrity
  checks pass in the local Testcontainers/Compose evidence.
- [x] Bounded local k6 smoke and empty-volume migration evidence are recorded.
- [N/A] Batch 13 deployment-scale load/performance and Batch 14
  HTTPS/TLS/HSTS/release-image evidence are not applicable to the local-only
  scope.
- [x] The user confirmed the human usability session was completed; detailed
  observations remain optional because they were not supplied for storage.
- [x] Empty-volume startup/migrations are verified.
- [x] `FINAL_STATUS.md` and this report agree on the current date, local-only
  scope, verification results, and remaining optional gaps. Older batch
  evidence ledgers retain their historical dates intentionally.

## Useful canonical references

- [Audit runbook](AUDIT_RUNBOOK.md)
- [Official question checklist](01-EDU_AUDIT_QUESTION_CHECKLIST.md)
- [Final status snapshot](FINAL_STATUS.md)
- [Test evidence](TEST_EVIDENCE.md)
- [Security checklist](SECURITY_CHECKLIST.md)
- [Stress-test notes](STRESS_TEST.md)
- [Usability test](USABILITY_TEST.md)
- [Active Batch Context](../superpowers/ACTIVE_BATCH_CONTEXT.md)
