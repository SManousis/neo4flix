# Minimum Completion and README Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the smallest honest set of remaining project artifacts and run instructions without fabricating human, HTTPS, stress, or restore evidence.

**Architecture:** Documentation remains the primary deliverable. Add one bounded k6 smoke profile and PowerShell wrappers for Community Neo4j offline dump/restore; exercise those helpers only against disposable data. Keep the existing local Compose topology unchanged, and explicitly separate local HTTP development from deployment HTTPS requirements.

**Tech Stack:** Markdown, PowerShell, k6 (native or Docker fallback), Docker Compose, Neo4j Community `neo4j-admin`.

**Spec:** `docs/superpowers/specs/2026-09-15-batch-12-final-evidence-design.md`, `docs/reference/00_MASTER_EXECUTION_PLAN.md`, `docs/reference/08_DEPLOYMENT_OPERATIONS.md`.

## Global Constraints

- Do not reset, delete, or restore over the project’s existing `neo4j-data` volume.
- Never print or commit `.env`, passwords, JWTs, TOTP secrets, access tokens, refresh tokens, or TLS private keys.
- Keep the current direct-main workflow and all completed batch evidence intact.
- Mark evidence as blocked or partial when the environment lacks k6, certificates, a human participant, or deployment infrastructure.
- Use explicit backup/restore paths and refuse ambiguous or missing paths in scripts.

---

### Task 1: Finish audit evidence and create runnable README

**Files:**
- Create: `docs/audit/SECURITY_CHECKLIST.md`
- Create: `docs/audit/TEST_EVIDENCE.md`
- Create: `docs/audit/USABILITY_TEST.md`
- Modify: `README.md`
- Modify: `docs/audit/batch-12-verification.md`

- [ ] **Step 1: Write the security matrix**

Map canonical rows to existing tests/scans: password/JWT/refresh/TOTP, USER/ADMIN, ownership, injection/sort/XSS/URL/bounds, errors/logging, headers/cookies/CORS, and optional container/TLS/k6 follow-ups. Link exact audit files and commands.

- [ ] **Step 2: Write the test evidence index**

Record fresh Maven `131` tests, frontend `27/105`, Playwright default and standalone-admin results, Testcontainers golden proofs, Compose health, recommendation/Neo4j outage evidence, and explicit skips/limitations.

- [ ] **Step 3: Write truthful usability evidence**

Document the seven-step journey. Mark the automated browser journey as supporting evidence and the real human participant row as pending unless a human completes it. Do not invent confusion or satisfaction results.

- [ ] **Step 4: Replace the planning-only README entrypoint**

Add prerequisites, `.env` generation guidance, local Compose commands, seed commands, health checks, verification commands, browser URL, stop commands, data-volume warning, backup/restore commands, and a table of current limitations.

- [ ] **Step 5: Verify docs**

Run `git diff --check` and secret-like scans across all changed Markdown. Expected: no diff errors and no secret-like content.

---

### Task 2: Add and run a bounded k6 smoke profile

**Files:**
- Create: `scripts/k6/smoke.js`
- Create: `docs/audit/STRESS_TEST.md`
- Modify: `README.md`

- [ ] **Step 1: Add a low-impact profile**

Create a 1–5 VU, short-duration profile that exercises public catalog browse/search and accepts only 2xx/3xx/429 responses. Keep thresholds explicit and state that it is a smoke probe, not the full Batch 13 audit profile.

- [ ] **Step 2: Detect the runner**

Use native `k6` when available; otherwise document the Docker fallback command without printing credentials. Do not install software silently.

- [ ] **Step 3: Run or record the blocker**

Run the smoke profile against `http://localhost:8080`. Record duration, VUs, request count, HTTP failure rate, and whether k6 was unavailable. Do not claim recommendation/rating load coverage if the profile does not exercise authenticated endpoints.

- [ ] **Step 4: Verify graph continuity**

After the smoke, run a read-only Neo4j count query through the existing container and record that no relationship count changed.

---

### Task 3: Add safe Community backup/restore helpers

**Files:**
- Create: `scripts/backup-neo4j.ps1`
- Create: `scripts/restore-neo4j.ps1`
- Modify: `README.md`
- Modify: `docs/audit/batch-12-verification.md`

- [ ] **Step 1: Implement explicit-path backup**

Require a non-empty destination directory, resolve it to an absolute path, create it if needed, and run `neo4j-admin database dump neo4j` inside the named Neo4j container. Never print database credentials.

- [ ] **Step 2: Implement guarded restore**

Require an explicit dump file and `-ConfirmRestore`, verify the path exists, stop only the named disposable/target container supplied by the operator, load into a clean target, and refuse to operate on the project volume unless explicitly named.

- [ ] **Step 3: Verify helper syntax and safe refusal paths**

Run PowerShell parser checks and invoke each script with missing/ambiguous arguments to confirm it refuses safely.

- [ ] **Step 4: Run a disposable backup/restore smoke**

Use a temporary Neo4j container/volume or isolated database, insert one marker node, dump, restore into a separate disposable target, and verify the marker. Do not touch `neo4flix_neo4j-data`.

---

### Task 4: Final reconciliation and handoff

**Files:**
- Modify: `docs/audit/batch-12-verification.md`
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md`

- [ ] **Step 1: Record actual k6 and backup outcomes**

Separate pass, partial, blocked, and external-input-required rows. Keep Batch 12 `[~]` if human usability is pending; keep Batches 13–15 open when their gates are not genuinely met.

- [ ] **Step 2: Run final checks**

Run the frontend and Maven suites already recorded, `git diff --check`, README command/path checks, script parser checks, and Compose health inspection.

- [ ] **Step 3: Review, commit, and push**

Request a focused review, commit logical checkpoints directly on `main`, push, and verify local/remote SHA equality.
