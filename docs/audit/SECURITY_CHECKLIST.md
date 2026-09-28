# Security Checklist

Date: 2026-09-24
Scope: evidence available in the repository and the current local Compose stack

This checklist distinguishes verified controls from evidence that still needs an
external scanner, deployment certificate, or human operator. A checked row is
backed by a tracked test, scan, or audit document; it is not a claim that a
production deployment has been independently certified.

| Area | Evidence | Status |
| --- | --- | --- |
| Password hashing, JWT signing/validation, refresh rotation, TOTP enrollment and replay protection | `docs/audit/batch-2-verification.md`; `AuthProductionContextIT`, `AuthNeo4jIntegrationIT`, refresh/challenge tests | Verified |
| USER/ADMIN authorization and ownership boundaries | `docs/audit/batch-11-browser-failure-verification.md`; browser/API authorization checks | Verified |
| Parameterized Cypher, sort/filter bounds, URL validation, XSS-safe projections | `docs/audit/batch-3-verification.md`, `batch-4-verification.md`, `batch-10-verification.md`; focused backend/frontend tests | Verified |
| Generic Problem Details, request IDs, no credential/token logging | `docs/audit/batch-10-verification.md`; security wrapper tests and service tests | Verified |
| Security headers, explicit CORS, refresh-cookie origin checks, trusted-proxy handling | `docs/audit/batch-10-verification.md`; Nginx/header and auth tests | Verified for local HTTP configuration |
| Dependency and repository secret scans | `make security`; `scripts/security.ps1` | Optional scanners are unavailable in this environment; repository checks pass |
| TLS termination, HTTPS redirect, HSTS, certificate rotation | `docs/reference/08_DEPLOYMENT_OPERATIONS.md` | N/A by declared local-only scope; no deployed endpoint is planned |
| Authenticated k6 load and rate-limit profile | `docs/audit/STRESS_TEST.md`, `scripts/k6/smoke.js` | Bounded local public/authenticated profiles verified; deployment-scale capacity is N/A |
| Backup/restore recovery evidence | `scripts/backup-neo4j.ps1`, `scripts/restore-neo4j.ps1`; disposable temporary-container dump/load record | Verified for local recovery; production release sign-off is N/A for this project |

## Operator checks

For this local-only project, run `make security`, inspect the generated Compose
configuration, verify local security headers/cookies, and keep the human audit
record current. Do not place key material, passwords, refresh tokens, or TOTP
secrets in this document.
