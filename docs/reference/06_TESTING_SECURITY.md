# Neo4flix — Testing and Security Specification

> **Authority:** This document owns mandatory verification layers, security controls, negative-path tests, malicious-input tests, concurrency/load gates, dependency/secret/container checks, and release evidence requirements.

## 1. Verification Principle

Use the narrowest test that actually proves the behavior.

- Mockito does not prove Cypher.
- repository tests do not prove browser flows.
- health does not prove recommendations.
- hidden admin UI does not prove authorization.

## 2. Verification Layers

```text
STATIC / COMPILE
      ↓
UNIT
      ↓
REAL NEO4J INTEGRATION
      ↓
SERVICE/API
      ↓
CROSS-SERVICE
      ↓
ANGULAR UNIT/COMPONENT
      ↓
PLAYWRIGHT E2E
      ↓
SECURITY
      ↓
K6 LOAD/STRESS
      ↓
AUDIT RUNBOOK
```

## 3. Backend Unit Tests

Cover:

- password policy
- DTO validation
- URL validation
- recommendation weight/config validation
- genre preference mapping
- share expiry
- pagination/sort
- JWT claim conversion
- TOTP service logic with controllable time
- DTO mapping

## 4. Real Neo4j Integration

Use Testcontainers Neo4j, with GDS where recommendation tests require it.

Mandatory:

- User/Movie/Genre create
- identifier/email/genre uniqueness
- movie+genre transaction
- rating CRUD
- duplicate rating conflict
- `RATED.key` uniqueness
- watchlist idempotency
- `WATCHLISTED.key` uniqueness
- rating aggregates
- user deletion cleanup
- movie/share cleanup
- share token-hash lookup
- refresh rotation/revocation
- AuthChallenge replay/expiry
- migration from empty

## 5. Concurrency Integrity

### Rating
Concurrent same user/movie create attempts must leave exactly one `RATED`.

### Watchlist
Concurrent same user/movie adds must leave exactly one `WATCHLISTED`.

## 6. Recommendation Golden Fixture

Deterministic graph, e.g.:

```text
Alice: Matrix 5, Inception 5, Interstellar 4
Bob:   Matrix 5, Inception 4, Interstellar 5, Blade Runner 5, Arrival 5
Carol: Notebook 5, Titanic 5, La La Land 4, Matrix 1
```

Assert meaningful ranking, not merely non-empty results.

## 7. Movie Service Tests

- ADMIN create/read/update/delete
- USER mutation 403
- unknown/missing genre
- year/date consistency
- runtime
- URL validation
- missing movie
- search/filter/sort/paging
- related movies
- recommendation facade equivalence
- recommendation dependency failure

## 8. Search Tests

Individually and combined:

- title
- genre
- release year
- exact date for known dates
- minimum rating
- sorting
- pagination
- empty
- Cypher-looking literals

Every result satisfies all filters.

## 9. Rating Tests

- POST valid 1–5
- invalid 0/6/malformed
- GET own
- PUT existing
- PUT missing
- DELETE own
- duplicate POST 409
- missing movie
- anonymous 401
- cross-user impossible
- aggregate summary
- zero-rating summary

## 10. User Service Tests

- registration
- duplicate registration
- login
- profile GET/PATCH
- self-delete reauth
- watchlist
- user-centric rating-history read
- auth cleanup

## 11. Watchlist Tests

- add/repeat add
- list
- remove/repeat remove
- missing movie
- anonymous
- user isolation
- concurrent add

## 12. RecommendationShare Tests

- create/list/read/update/delete
- owner isolation
- raw token not persisted
- valid public token
- expired/revoked/random unavailable
- public DTO has no creator private data

# Authentication and Authorization

## 13. Password Storage

Assert:

- stored != plaintext
- BCrypt verifies
- plaintext absent from APIs/logs

## 14. Password Policy

Reject:

- too short
- no uppercase
- no lowercase
- no digit
- no special
- excessive length
- pathological whitespace

## 15. Account Enumeration

Login failure generic for unknown email vs wrong password.

## 16. JWT Matrix

- valid accepted
- expired 401
- malformed 401
- tampered 401
- wrong key 401
- wrong issuer 401
- wrong audience 401
- USER admin mutation 403
- ADMIN succeeds

## 17. Refresh Rotation

```text
login → A
use A → revoke A + issue B
reuse A → reject
use B → issue C
logout → active refresh rejected
```

Only hashes persist.

## 18. TOTP

Enrollment:
- pending encrypted secret
- pending expiry
- wrong confirm rejected
- valid confirm activates
- pending cleared

Login:
- password alone does not authenticate active-2FA user
- wrong TOTP rejected
- valid TOTP succeeds
- expired challenge rejected
- used challenge replay rejected
- malformed token/code rejected

Disable:
- current password+TOTP required
- success clears secret/flag

## 19. Horizontal Authorization

User A cannot access/mutate User B's:

- rating
- watchlist
- profile
- shares
- sessions/challenges

Identity from JWT.

## 20. Vertical Authorization

USER movie/genre mutation → 403 backend-side.

# Malicious Input

## 21. Cypher Injection

Parameterized custom queries. Test quotes/comments/Cypher-looking strings in search/display/admin/share inputs. They are data or rejected, never executable syntax.

## 22. Dynamic Sort Injection

Only enum/allowlist sort values. Unknown value → 400. Never interpolate request sort expression.

## 23. XSS

Test display name/title/overview payloads. Angular renders text, no unsafe bypass sanitization for untrusted content.

## 24. Poster URL

Allow HTTPS and explicit dev exceptions only. Reject dangerous schemes such as `javascript:`, `file:`, unsafe HTML data URLs.

## 25. Request Bounds

Bound body/text/search/page/recommendation candidate/peer/share-expiry sizes. No unbounded user-controlled query expansion.

## 26. Error Leakage

Unexpected failures return generic Problem Details + trace ID. Never client-return stack traces, Cypher, credentials, private paths.

## 27. Sensitive Logs

Never log:

- passwords
- access/refresh tokens
- Authorization
- TOTP secret/code
- raw auth challenge
- raw share token where avoidable
- Neo4j password
- JWT private key
- TOTP encryption key

# Browser/Transport

## 28. Refresh Cookie

Production:

- HttpOnly
- Secure
- SameSite
- bounded path/domain

Test Set-Cookie.

## 29. Access Token Storage

Playwright/browser inspection proves access JWT absent from localStorage/sessionStorage.

## 30. CORS

Explicit dev origin allowlist; hostile origin denied; no wildcard credentials.

## 31. CSRF-Sensitive Cookie Endpoints

Refresh/logout validate expected Origin/Referer policy plus SameSite.

## 32. HTTPS/Headers

- HTTP → HTTPS
- valid cert
- HSTS after HTTPS
- nosniff
- Referrer-Policy
- CSP compatible with app/posters
- no mixed content

# Failure Handling

## 33. Neo4j Unavailable

Controlled 5xx/503 + Problem Details + trace ID; health reflects dependency.

## 34. Recommendation Service Unavailable

Movie recommendation facade → controlled 503; normal movie browse/detail/search continue.

# Frontend Verification

## 35. Vitest/Component

- auth forms
- 2FA
- refresh coordination
- guards
- rating stars
- filters
- watchlist
- loading/empty/error
- admin year/date validation

## 36. Playwright

### Core user
Register → login → search → movie → rate → watchlist → recommendations.

### Recommendation change
Known recommendation → change rating → expected change.

### 2FA
Password → challenge → wrong code → correct code.

### Authorization
USER direct admin request blocked.

### Sharing
Create share → logout → anonymous open → private data absent.

### Admin
ADMIN CRUD.

### XSS
Payload does not execute.

# Load/Stress

## 37. k6 Smoke

Approx. 5 VUs for ~1 minute across browse/search/rating summary/recommendations.

## 38. Audit Stress

Baseline:

- ramp to ~25 VUs
- hold ~3 min
- ramp down

Approximate mix:

- 40% browse
- 25% search
- 20% recommendations
- 10% rating reads
- 5% auth

## 39. Load Targets

Hardware-dependent project targets:

- failure rate < 1%
- no crash
- no graph corruption/duplicates
- normal read/search p95 around <=1s where feasible
- recommendation p95 around <=2s where feasible

Report actual environment/results honestly.

# Security Tooling

## 40. Dependency Checks

Backend OWASP Dependency-Check or equivalent. Frontend `npm audit` reviewed contextually. Actionable high/critical release issues block completion unless explicitly accepted.

## 41. Secret Scan

Use gitleaks/equivalent.

## 42. Container Scan

Use Trivy/equivalent; investigate actionable high/critical findings.

# Verification Commands

## 43. Root Commands

```bash
make test
make test-integration
make test-e2e
make test-load
make security
make verify
make verify-all
```

### Batch 10 security entry points

- `pwsh -NoProfile -File scripts/test-security-headers.ps1` verifies the Nginx browser-security header contract without reading secrets.
- `pwsh -NoProfile -File scripts/security.ps1` runs the frontend high-severity dependency audit, an OWASP Dependency-Check backend entry point, tracked secret-path review, and Trivy image scans when those tools/images are available; missing external scanners are reported as explicit skips.
- `make security` invokes the same wrapper from the repository root.
- Nginx applies a bounded `30r/m` edge limit with burst handling to movie/catalog and recommendation routes; user-service auth throttling remains keyed by the configured trusted proxy's `X-Real-IP`.
- Movie writes accept HTTPS poster URLs plus explicit localhost/loopback HTTP development exceptions and reject dangerous schemes.

### make verify
- backend compile/static/style
- unit
- Neo4j integration
- frontend lint/unit/build
- Compose config validation

### make verify-all
- verify
- Docker build
- full clean stack smoke
- Playwright
- security review/scans
- k6 audit profile

Backup/restore remains required final deployment proof.

## 44. Final Security Matrix

- [x] password hashing/policy
- [x] JWT signature/issuer/audience/expiry
- [x] refresh rotation/replay/logout
- [x] TOTP enrollment/login/disable/challenge expiry
- [x] USER/ADMIN
- [x] horizontal ownership
- [x] Cypher injection
- [x] sort allowlist
- [x] XSS
- [x] URL validation
- [x] request/query bounds
- [x] rate limiting
- [x] sensitive log review
- [x] secret scan entry point and tracked-path review (gitleaks is optional follow-up)
- [x] dependency review entry points (npm audit: 0 vulnerabilities; backend scanner is optional follow-up)
- [ ] container review (optional deployment follow-up; not part of the exercise gate)
- [x] cookies/headers; production TLS/HSTS remains optional deployment follow-up
- [ ] stress/concurrency audit profile (optional k6 follow-up; focused concurrency tests pass)

The unchecked container and k6 rows are optional deployment/audit follow-up, not blockers for the Batch 10 exercise deliverables.

No completion claim without fresh verification output.
