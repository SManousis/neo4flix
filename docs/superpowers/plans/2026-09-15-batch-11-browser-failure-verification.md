# Full Browser E2E and Failure-Mode Verification Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify the critical user, authorization, sharing, recommendation-outage, and service-health flows against the real Docker Compose stack and record honest browser evidence.

**Architecture:** Rebuild only the changed Compose services, run the existing Playwright suite against the local Nginx entry point, and add narrowly scoped failure-mode checks only where the current suite has a documented gap. Keep credential-gated tests skipped when no disposable E2E credentials are configured; do not print `.env` values or reset the Neo4j volume.

**Tech Stack:** Docker Compose, Nginx, Angular Playwright, PowerShell, Spring Boot health endpoints.

**Spec:** `docs/reference/05_FRONTEND_SPEC.md`, `docs/reference/06_TESTING_SECURITY.md`, `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md`.

## Global Constraints

- Use the real `http://localhost:8080` Compose entry point for browser checks.
- Never print or commit `.env`, credentials, tokens, or database passwords.
- Preserve the current Docker volumes and existing seeded data; do not reset Neo4j.
- Record passed, skipped, and blocked scenarios separately; skipped credential-gated tests are not passing evidence.
- Keep controlled outage behavior generic with trace/request IDs and preserve normal catalog browsing.

---

### Task 1: Refresh the real verification stack

**Files:**
- Inspect: `infra/compose.yml`, `infra/compose.dev.yml`, `frontend/playwright.config.ts`
- Modify: `docs/audit/batch-11-browser-failure-verification.md`

- [x] **Step 1: Confirm Compose services are healthy without exposing secrets**

Run `docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml ps` and record only service names and health states.

- [x] **Step 2: Rebuild changed services and wait for health**

Run `docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up -d --build --wait --wait-timeout 600` without printing environment values.

- [x] **Step 3: Verify the public entry point**

Run `Invoke-WebRequest http://localhost:8080/ -UseBasicParsing` and record the HTTP status and security headers only.

### Task 2: Run the complete browser suite

**Files:**
- Inspect: `frontend/e2e/*.spec.ts`
- Modify: `docs/audit/batch-11-browser-failure-verification.md`

- [x] **Step 1: Run Playwright against the real stack**

Run `npm run e2e` from `frontend` with `NEO4FLIX_E2E_BASE_URL=http://localhost:8080` and capture pass/skip/fail counts.

- [x] **Step 2: Classify credential-gated skips**

Record whether `NEO4FLIX_E2E_EMAIL`, `NEO4FLIX_E2E_PASSWORD`, `NEO4FLIX_E2E_ADMIN_EMAIL`, and `NEO4FLIX_E2E_ADMIN_PASSWORD` were configured without printing their values. Do not convert skipped tests into pass claims.

- [x] **Step 3: Verify the browser 2FA flow with a disposable user.**

The scenario enrolls 2FA from the live setup URI, completes the password-only challenge with a generated RFC 6238 code, and cleans up with reauthentication.

### Task 3: Verify controlled recommendation-service failure

**Files:**
- Inspect: `frontend/e2e/recommendations.spec.ts`, `backend/movie-service/src/main/java/com/neo4flix/movie/recommendation/MovieRecommendationController.java`
- Modify: `docs/audit/batch-11-browser-failure-verification.md`

- [x] **Step 1: Run the outage scenario with a disposable self-registered browser fixture.**

The recommendation specs create and delete disposable users through the API; only the ADMIN CRUD spec remains credential-gated.

- [x] **Step 2: Verify catalog continuity during outage; the browser fallback kept the normal movie catalog available after the recommendation request returned controlled 503/problem data.**

Record that the normal catalog route remains usable and recommendation failure is represented as controlled 503/problem UI rather than a blank or fatal shell.

### Task 4: Verify service-health and handoff evidence

**Files:**
- Inspect: `infra/compose.yml`, `docs/reference/08_DEPLOYMENT_OPERATIONS.md`
- Create: `docs/audit/batch-11-browser-failure-verification.md`
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`, `docs/reference/00_MASTER_EXECUTION_PLAN.md`

- [x] **Step 1: Run health and diff gates**

Run Compose health inspection, `git diff --check`, and the existing frontend regression suite.

- [x] **Step 2: Record limitations honestly**

Document credential-gated skips, unavailable failure injection, or any browser/environment blocker without claiming unobserved flows.

- [x] **Step 3: Close Batch 11 after disposable ADMIN verification; Neo4j failure injection is evidenced.**

The standalone ADMIN contract passed with a disposable promoted fixture, the fixture was removed, and the Compose stack remained healthy. Update the master plan and active context, commit, push `main`, and verify local/remote SHA equality. k6 remains optional follow-up.
