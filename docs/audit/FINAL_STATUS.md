# Final Status Reconciliation

Date: 2026-09-24
Branch: `main`
Latest verified commit: `92ed4c7` (Gitea and GitHub `main` are aligned; the
pre-existing local `.env.example` and `frontend/angular.json` edits remain
uncommitted by design)

## Current state

The project is intentionally local-only and will not be deployed. It is
runnable through Docker Compose: the latest reset/reseed pass found Neo4j,
migrations, all four backend services, and the web entry point healthy. The
full Maven reactor and frontend suites are green after the migrator test-class
path and isolated nested-build fixes, and the repository contains a
`verify-all` gate that composes the existing checks.

The batch ledger may still show partial later batches, but production-only
Batch 13/14 deployment, HTTPS, release-image, and deployment-scale performance
rows are N/A for this declared scope rather than unfinished deployment work.

## Evidence completed

- Backend reactor: full `mvnw verify` passed with the Testcontainers integration
  tests; the module summaries reported 131 tests with 0 failures/errors/skips.
- Executable service JAR smoke passed for user, movie, rating, and
  recommendation services.
- Frontend: 27 test files and 105 tests passed.
- Browser contract: 8 passed, 1 credential-gated ADMIN test skipped; standalone
  ADMIN proof previously passed with cleanup count 0.
- Local k6 smoke: 30 requests, 0% HTTP failures, p95 17.05 ms.
- Authenticated k6 smoke: disposable account, 45/45 checks, 0% failures, p95
  131.53 ms, teardown left 0 users.
- Bounded 5-VU/30-second run: 453 requests, 450/450 checks, 0% server failures,
  115 explicit 429 rate-limit responses, p95 11.83 ms; relationships remained
  42 and disposable users were removed.
- Disposable Neo4j offline dump/load cycle passed using separate temporary
  containers and volumes; the project volume was not touched.
- Security wrapper completed; npm audit reported 0 vulnerabilities. Optional
  OWASP Dependency-Check, Gitleaks, and Trivy binaries remain unavailable.
- The user confirmed that the manual human audit/usability checks were
  completed; detailed participant notes were not reproduced in this status
  file.

## Remaining gates

1. Optionally add the user's detailed human-audit observations to
   `USABILITY_TEST.md` if a formal participant evidence record is required.
2. Run the exact documented JDK-21 verification when that JDK is available;
   the current full verification used JDK 26 and passed.
3. Install optional Dependency-Check, Gitleaks, and Trivy only if those extra
   scanner gates are desired for the showcase.
4. No further reconciliation is pending for the current snapshot; the report,
   final status, evidence index, security checklist, stress notes, and active
   batch context agree. Historical batch ledgers intentionally retain their
   original dates.

Production deployment infrastructure, certificates, release images, and
deployment-scale evidence are intentionally N/A because this project will not
be deployed.
