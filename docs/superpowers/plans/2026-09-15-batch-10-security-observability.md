# Cross-Cutting Security and Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining Batch 10 security/operations gaps with verifiable headers, scan entry points, and evidence while preserving the existing centralized request-ID, CORS, cookie-origin, bounds, and rate-limit contracts.

**Architecture:** Keep cross-cutting HTTP behavior in `platform-common`, auth-specific throttling/origin checks in `user-service`, and edge policy in the Nginx image. Add deterministic repository scripts that report unavailable external scanners explicitly rather than hiding their absence.

**Tech Stack:** Spring Boot 4, Spring Security, Servlet filters, Nginx, PowerShell, npm audit, Docker Compose.

**Spec:** `docs/reference/02_TECHNICAL_ARCHITECTURE.md`, `docs/reference/04_API_SPEC.md`, `docs/reference/06_TESTING_SECURITY.md`, `docs/reference/08_DEPLOYMENT_OPERATIONS.md`.

## Global Constraints

- Never log passwords, tokens, Authorization, TOTP material, raw share tokens, Neo4j passwords, or signing/encryption keys.
- Use explicit CORS origins with credentials; never use wildcard origins with credentials.
- Unexpected failures remain generic Problem Details with `traceId` and `X-Request-Id`.
- Nginx must preserve request IDs and add browser security headers without breaking HTTPS poster loading or Angular runtime behavior.
- Scan scripts must not print `.env` or secret values.

---

### Task 1: Harden edge security headers

**Files:**
- Modify: `infra/nginx/default.conf`
- Create: `scripts/test-security-headers.ps1`

**Interfaces:**
- Nginx emits `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, and a CSP compatible with same-origin Angular plus HTTPS poster images.
- The script accepts no secrets and exits non-zero when a required directive is absent or an unsafe wildcard policy is introduced.

- [x] **Step 1: Write a failing static-policy test** that reads `infra/nginx/default.conf` and asserts the required directives.
- [x] **Step 2: Run the script and confirm it fails** because the current config lacks the new directives.
- [x] **Step 3: Add the minimal Nginx headers** and keep `X-Request-Id` proxy propagation unchanged.
- [x] **Step 4: Run the script and `git diff --check`**.
- [x] **Step 5: Commit** `feat: harden nginx security headers`.

### Task 2: Add deterministic security scan entry points

**Files:**
- Modify: `Makefile`
- Create: `scripts/security.ps1`
- Create: `scripts/security.Tests.ps1`

**Interfaces:**
- `make security` invokes the PowerShell scan wrapper.
- The wrapper runs `npm audit --audit-level=high`, checks for tracked secret-shaped files, and runs gitleaks/Trivy when installed; missing optional scanners are reported as explicit skips.
- No command prints environment values or file contents containing secrets.

- [x] **Step 1: Write failing script tests** for WhatIf command ordering and secret-value redaction.
- [x] **Step 2: Run the script tests and confirm failure** because the wrapper/target does not exist.
- [x] **Step 3: Implement the wrapper and Make target** with strict exit handling and explicit scanner skips.
- [x] **Step 4: Run script tests, `npm audit --audit-level=high`, and the wrapper in WhatIf mode**.
- [x] **Step 5: Commit** `chore: add security scan entry points`.

### Task 3: Reconcile the security evidence matrix

**Files:**
- Modify: `docs/reference/06_TESTING_SECURITY.md`
- Modify: `docs/reference/08_DEPLOYMENT_OPERATIONS.md`
- Create: `docs/audit/batch-10-security-verification.md`

**Interfaces:**
- Evidence records identify existing passing controls (JWT/roles/ownership, CORS, cookie-origin checks, bounds, 429, request IDs) and distinguish unavailable external tools from passing scans.

- [x] **Step 1: Add failing checklist assertions** for the new Nginx and scan commands.
- [x] **Step 2: Implement the checklist/evidence updates** without claiming unavailable TLS, gitleaks, Trivy, or k6 results.
- [x] **Step 3: Run focused security tests and record exact outputs**.
- [x] **Step 4: Commit** `docs: record batch 10 security verification`.

### Task 4: Batch 10 verification and handoff

**Files:**
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md`
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`

- [x] **Step 1: Run Maven security-focused tests, frontend regression, Compose validation, header scan, and available security scans**.
- [x] **Step 2: Run `git diff --check` and review sensitive-log paths**.
- [x] **Step 3: Mark Batch 10 complete for the exercise scope, document optional deployment follow-up, update context to Batch 11, commit, and push `main`.**
