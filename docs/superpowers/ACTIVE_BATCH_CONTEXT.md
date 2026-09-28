# Active Batch Context — Batch 12 Partial

> **Status:** Batches 10 and 11 exercise deliverables are complete and pushed. Batch 12 evidence foundation plus minimum audit/readme artifacts are present. The user has completed the manual human audit; full local Maven/Testcontainers verification, executable-JAR smoke, reset/reseed, Compose, browser, and bounded k6 checks now pass. Production deployment, HTTPS, release-image, and deployment-scale performance gates are N/A because the project is intentionally local-only. Execution remains direct on `main`.
>
> **Purpose:** Compact handoff cache generated from the Batch 10 plan, SDD ledger, current git state, and canonical requirements. It does not replace the master plan, product/API specs, or approved batch plans.

## Repository state

- Worktree: `C:\Users\User\Desktop\Neo4flix`
- Branch: `main` (direct-main execution; do not create worktrees)
- Context snapshot base: current `main` after the Batch 12 minimum-completion checkpoint.
- Local `.env` is ignored and must never be committed or printed.

## Completed ledger

- Batches 0–7 remain complete and preserved.
- Batch 8 recommendation sharing and CRUD is complete:
  - Recommendation Service owns parameterized share persistence and owner/public CRUD.
  - Tokens are cryptographically random; only SHA-256 hashes persist.
  - Owner identity is derived from JWT `sub`; expiry, revocation, collision retry, and not-found behavior are enforced.
  - Public lookup is anonymous but exposes only safe movie/genre projections.
  - Public-share security is explicit and movie deletion removes inbound share nodes.
  - Angular has typed share APIs, recommendation-card creation/copy flow, and anonymous `/share/:publicToken` page.

## Batch 8 verification evidence

- Full Maven reactor: 129 tests, 0 failures/errors/skips.
- Recommendation Service reactor: 39 tests, 0 failures/errors/skips.
- Frontend unit suite: 90 tests across 24 files; lint and production build passed.
- Compose interpolation passed using `.env.example`; no `.env` was changed or printed.
- `git diff --check` passed.
- Playwright share-specific coverage is not present; existing authenticated specs skip without configured E2E credentials. This is recorded explicitly in `docs/audit/batch-8-verification.md`.
- Audit record: `docs/audit/batch-8-verification.md`.

## Batch 9 completion

- Plan: `docs/superpowers/plans/2026-09-15-batch-9-frontend-completion.md`.
- `e99dd23` closes `/`, `/home`, `/search`, and canonical admin aliases with focused route/home/search coverage.
- `18ad9f3` adds URL-backed catalog title state, explicit loading/empty/error UI, retry behavior, and typed catalog filters.
- `4e09100` removes the duplicate shell heading and adds visible focus/responsive shell styling.
- `27bec5a` renders movie poster and genre summary fields on movie detail.
- `c8fb101` associates recommendation filters with explicit label/control IDs.
- `04070ac` adds detail/watchlist retry states and removes the dead `/ratings` path.
- `e0e5e39` adds responsive card/form/admin reflow and stronger navigation/rating semantics.
- `bfefd49` adds admin catalog retry and controlled recommendation status messaging without rendering private signals.
- `ce467e4` preserves a user-selected rating when the existing-rating lookup completes later.
- Final verification: Maven 129 tests; frontend 105 tests; lint/build; Compose interpolation; Playwright 5 passed and 3 credential-gated skips.

## Batch 10 completion

- Plan: `docs/superpowers/plans/2026-09-15-batch-10-security-observability.md`.
- `6dbd6bf` adds Nginx `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, and CSP headers while preserving request-ID propagation.
- `ef8b6a8` adds deterministic `make security`/PowerShell scan entry points with tracked secret-path checks and explicit optional-scanner skips.
- `1dcdfe1` records the security evidence matrix and fresh verification results.
- Focused Maven security tests passed; frontend regression passed (105 tests/27 files), lint and production build passed, Compose config passed, header contract passed, Pester security wrapper tests passed (2/2), npm audit reported 0 vulnerabilities, and `git diff --check` passed.
- OWASP Dependency-Check, Gitleaks, and Trivy are not installed in the current environment and are explicitly reported as optional follow-up skips; production TLS/HSTS and k6 are likewise optional deployment follow-up, not Batch 10 exercise blockers.
- Review follow-up added bounded Nginx limits for expensive movie/recommendation routes, Compose trusted-proxy configuration, and HTTPS/loopback poster URL validation with focused tests.

## Batch 11 verification

- Plan: `docs/superpowers/plans/2026-09-15-batch-11-browser-failure-verification.md`.
- Rebuilt the full Compose stack with the existing `.env` and preserved Neo4j data.
- Playwright against `http://localhost:8080`: 9 tests discovered, 8 passed, 1 skipped for missing disposable ADMIN credentials (ADMIN CRUD), 0 failed.
- Added a real sharing regression and fixed Neo4j share timestamp binding by converting `Instant` values to UTC `ZonedDateTime`; focused repository tests and live API smoke pass.
- Added and passed a disposable-user 2FA browser flow covering enrollment, password-only challenge, TOTP verification, and cleanup.
- Controlled authenticated Neo4j outage returned HTTP 500 with a request ID; Neo4j was restored healthy and the disposable probe user was removed without resetting the volume.
- Public entry point returned HTTP 200 with request ID and configured browser security headers.
- Controlled outage check: catalog remained HTTP 200 while recommendation-service was stopped; the service was restored and all six Compose services returned healthy.
- Standalone ADMIN CRUD contract passed with a freshly registered/promoted disposable fixture (`1 passed`); cleanup confirmed `remaining 0` and all six services remained healthy.
- Audit record: `docs/audit/batch-11-browser-failure-verification.md`.

## Batch 12 evidence foundation

- Design: `docs/superpowers/specs/2026-09-15-batch-12-audit-evidence-design.md`.
- Plan: `docs/superpowers/plans/2026-09-15-batch-12-audit-evidence-foundation.md`.
- Added `docs/audit/AUDIT_RUNBOOK.md`, `GRAPH_DEMO.md`, and `RECOMMENDATION_EXPLANATION.md` from the existing fixture, loader, repository queries, scoring service, and golden tests.
- Fresh Testcontainers proofs passed: `AuditSeedLoaderIT` 2/2 and `RecommendationGoldenFixtureIT` 2/2; no production behavior or Neo4j volume changed.
- Verification record: `docs/audit/batch-12-verification.md`.

## Next unfinished workstream

- Current local audit metadata is reconciled; adding detailed human
  observations to `docs/audit/USABILITY_TEST.md` remains optional because the
  user confirmed the audit without supplying a participant record.
- The focused recommendation Testcontainers suite now passes in a fresh
  test-phase reactor; the migrator plain classifier is attached at
  `process-classes` and nested executable builds use an isolated target.
- The exact documented JDK-21 run remains an environment prerequisite; current
  full verification passed with JDK 26.
- Optional Dependency-Check, Gitleaks, and Trivy scans are not installed.
- Batch 13 deployment-scale load and Batch 14 deployment HTTPS/release-ingress
  evidence are N/A for the declared local-only scope.
- Preserve Batch 10 controls: explicit edge headers and limits, request-ID propagation, generic Problem Details, explicit CORS/cookie-origin checks, bounded inputs, proxy-aware auth throttling, poster URL validation, and deterministic scan entry points.

## Execution policy

- Work directly on `main`; do not create worktrees or redispatch completed batches.
- Use red-test → minimal implementation → focused green test → broader regression verification for each behavior change.
- Keep identity server-derived, Cypher parameterized, credentials out of logs, and exact commands/counts in the current batch audit.
- Do not mark a batch complete without fresh verification evidence and remote equality after push.
