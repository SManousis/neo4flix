# Batch 2 Authentication, Profile, Refresh Sessions, and TOTP 2FA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the User Service authentication/profile vertical slice and the Angular authentication/security experience with secure JWTs, rotating refresh sessions, RFC 6238 TOTP, ownership enforcement, and deletion cleanup.

**Architecture:** User Service owns credentials, signing keys, refresh sessions, login challenges, TOTP enrollment, profile mutation, and account deletion. Protected services validate the User Service public JWT contract through shared Spring Security configuration; Angular stores access tokens only in memory and uses one coordinated refresh request through the HttpOnly cookie. Persistence uses the existing shared Neo4j graph and the Batch 1 `User`, `AuthSession`, and `AuthChallenge` mappings with parameterized custom Cypher for atomic security transitions.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring MVC, Spring Security OAuth2 Resource Server/Jose, Spring Data Neo4j, BCrypt, AES-256-GCM, RFC 6238 TOTP, ZXing QR generation, Angular 22.1.5 standalone components/signals, RxJS 7.8.2, Vitest, JUnit 5, Mockito, Testcontainers Neo4j, and Playwright-ready browser contracts.

**Spec:** `docs/reference/01_PRODUCT_SPEC.md` §§5–7, 21, 24, 26.G; `docs/reference/02_TECHNICAL_ARCHITECTURE.md` §§8–12; `docs/reference/03_GRAPH_DATABASE_SPEC.md` §§3, 9–11, 19, 23; `docs/reference/04_API_SPEC.md` §§8–17, 18, 39–45; `docs/reference/05_FRONTEND_SPEC.md` §§4–10, 22–23, 28–32; `docs/reference/06_TESTING_SECURITY.md` §§13–20, 28–32, 35–36.

## Global Constraints

- Keep exactly four business services and one shared Neo4j graph; User Service is the only credential/session/TOTP mutator.
- Use Java 21 release compatibility, Spring MVC (not WebFlux), Spring Security, Spring Data Neo4j, and Angular standalone components/signals.
- Passwords use adaptive BCrypt; raw passwords, refresh tokens, challenge tokens, TOTP secrets, and private keys never enter APIs, logs, or Neo4j.
- Access JWTs are RS256, short lived (900 seconds default), with `sub`, roles, issuer, audience, `iat`, `exp`, and JTI; only User Service holds the private key.
- Refresh and 2FA challenge values are opaque cryptographically random values persisted only as one-way hashes; refresh rotation is single-use and revokes the previous session.
- Active TOTP secrets are AES-256-GCM encrypted with a deployment key outside Neo4j; pending enrollment is separate, expiring, and inactive until confirmation.
- Browser access tokens and enrollment secrets remain memory-only; refresh uses Secure/HttpOnly/SameSite cookie and expected-origin checks.
- All authenticated mutations derive identity from JWT subject and enforce ownership server-side; ADMIN checks are backend-enforced.
- All custom Cypher values are parameters; account deletion explicitly removes sessions, challenges, and recommendation shares before deleting the user.
- Sensitive endpoints (register, login, 2FA, refresh) are bounded and rate limited, returning generic credential errors to prevent account enumeration.
- Every task ends with focused tests, a relevant full suite, and a commit; no floating dependency versions or committed secrets.

---

### Task 1: Security Dependencies, Key Material, and Cross-Service JWT Validation

**Files:**
- Modify: `backend/pom.xml`, `backend/platform-common/pom.xml`, `backend/user-service/pom.xml`, `backend/movie-service/pom.xml`, `backend/rating-service/pom.xml`, `backend/recommendation-service/pom.xml`
- Create: `backend/platform-common/src/main/java/com/neo4flix/platform/common/security/JwtClaims.java`
- Create: `backend/platform-common/src/main/java/com/neo4flix/platform/common/security/ResourceServerSecurityConfig.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/security/JwtKeyConfiguration.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/security/JwtTokenService.java`
- Modify: each service `src/main/resources/application.yml`
- Test: `backend/platform-common/src/test/java/com/neo4flix/platform/common/security/ResourceServerSecurityConfigTest.java`, `backend/user-service/src/test/java/com/neo4flix/user/security/JwtTokenServiceTest.java`

**Interfaces:**
- `JwtClaims` exposes `subject()`, `roles()`, `issuer()`, and `audience()` from an authenticated `Jwt`.
- `JwtTokenService.issue(UserNode user, Instant now)` returns an `IssuedAccessToken(String token, long expiresInSeconds)` and signs with an injected RSA private key.
- `ResourceServerSecurityConfig` exposes `SecurityFilterChain` with public auth routes, stateless JWT validation, issuer/audience checks, and role mapping from the `roles` claim.
- Key configuration reads PEM/key paths or base64 values from environment-backed properties; tests generate an ephemeral RSA pair and never write it to the repository.

- [ ] Write failing tests for JWT claims, issuer/audience rejection, role mapping, 900-second expiry, and public-route/stateless filter behavior.
- [ ] Run `./mvnw -q -pl backend/platform-common,backend/user-service -am -Dtest=ResourceServerSecurityConfigTest,JwtTokenServiceTest test`; expect RED before security classes exist.
- [ ] Add exact Spring Security resource-server/Jose dependencies and implement the shared validator plus User Service signer.
- [ ] Add configuration contracts for `NEO4FLIX_JWT_PRIVATE_KEY`, `NEO4FLIX_JWT_PUBLIC_KEY`, issuer, audience, and refresh-cookie policy without defaulting to a committed secret.
- [ ] Run focused tests, then `./mvnw -q -pl backend -am test`; confirm invalid issuer/audience/tampered signatures fail with 401 and protected service role checks are enforced.
- [ ] Commit `feat: add shared jwt security foundation`.

### Task 2: Registration, Login, Password Policy, Refresh Rotation, and Profile APIs

**Files:**
- Modify: `backend/user-service/src/main/java/com/neo4flix/user/persistence/UserNode.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/persistence/UserRepository.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/persistence/AuthSessionRepository.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/persistence/AuthChallengeRepository.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/auth/PasswordPolicy.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/auth/AuthApplicationService.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/auth/AuthController.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/user/ProfileController.java`
- Create: DTO records under `backend/user-service/src/main/java/com/neo4flix/user/api/`
- Modify: `backend/user-service/src/main/resources/application.yml`
- Test: unit tests beside password/auth services and Testcontainers integration tests under `backend/user-service/src/test/java/com/neo4flix/user/auth/`

**Interfaces:**
- `POST /api/v1/auth/register` accepts `RegisterRequest(email, displayName, password)` and returns `201 PublicUser`; duplicate normalized email returns 409.
- `POST /api/v1/auth/login` accepts `LoginRequest`; no-2FA returns `200 AuthResponse(accessToken, tokenType, expiresIn, user)` and sets a Secure/HttpOnly/SameSite refresh cookie; active-2FA returns `202 TwoFactorChallenge(challengeToken, expiresIn)` without a session.
- `POST /api/v1/auth/refresh` consumes only the cookie and returns a new access token plus rotated cookie; replay of the prior cookie fails.
- `POST /api/v1/auth/logout` revokes the current session and clears the cookie with `204`.
- `GET /api/v1/auth/me` and `GET /api/v1/users/me` return the same `PublicUser`; `PATCH /api/v1/users/me` accepts only `displayName`; `POST /api/v1/auth/change-password` requires current password and active-2FA code when applicable.
- Repositories expose parameterized methods for normalized email lookup, hash lookup with unrevoked/expiry predicates, atomic rotation, and public-user projection; controllers never return persistence records.

- [ ] Write RED tests for password policy boundaries, BCrypt non-plaintext storage, duplicate registration, generic unknown-email/wrong-password errors, cookie flags, and profile ownership.
- [ ] Write RED Testcontainers tests for login→refresh A→refresh B, replay rejection, logout revocation, and no raw token/hash leakage in graph properties.
- [ ] Implement repository queries with `Neo4jClient` parameters and transaction boundaries; use `SecureRandom` for opaque values and BCrypt for passwords.
- [ ] Implement DTO validation and `ProblemDetails` responses with generic authentication failures and bounded request fields.
- [ ] Run focused tests, then `./mvnw -q -pl backend/user-service -am verify`; inspect stored nodes to prove only hashes and UTC timestamps persist.
- [ ] Commit `feat: implement user registration login refresh and profile`.

### Task 3: TOTP Enrollment, Login Challenge, Disable, Rate Limiting, and Deletion Cleanup

**Files:**
- Modify: `backend/user-service/src/main/java/com/neo4flix/user/auth/AuthApplicationService.java`, `backend/user-service/src/main/java/com/neo4flix/user/auth/AuthController.java`, `backend/user-service/src/main/java/com/neo4flix/user/user/ProfileController.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/security/TotpService.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/security/SecretEncryptionService.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/security/RateLimitFilter.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/user/AccountDeletionService.java`
- Modify: `backend/user-service/pom.xml`, `backend/user-service/src/main/resources/application.yml`
- Test: `backend/user-service/src/test/java/com/neo4flix/user/security/TotpServiceTest.java`, `SecretEncryptionServiceTest.java`, `RateLimitFilterTest.java`, and live auth integration tests

**Interfaces:**
- `POST /api/v1/auth/2fa/setup` returns `TotpSetupResponse(otpauthUri, qrCodeDataUrl, expiresAt)` and stores only encrypted pending state.
- `POST /api/v1/auth/2fa/confirm` accepts exact six-digit `code`, atomically promotes pending to active, clears pending fields, and flips `twoFactorEnabled`.
- `POST /api/v1/auth/2fa/verify` consumes one challenge token and code once, then issues normal access/refresh credentials.
- `POST /api/v1/auth/2fa/disable` and `DELETE /api/v1/users/me` require current password plus TOTP when active; deletion removes shares, sessions, challenges, and the user in one graph transaction.
- `TotpService.generatePending()`, `verify(secret, code, Instant)`, and `SecretEncryptionService.encrypt/decrypt` use RFC 6238 six-digit/30-second defaults and AES-256-GCM with an external 256-bit key.

- [ ] Write RED tests for pending expiry, wrong/valid confirmation, encryption non-determinism, active-2FA login gating, wrong/expired/used challenge, disable reauthentication, and deletion orphan cleanup.
- [ ] Add QR generation with ZXing and strict code/token shape validation; never log setup URI, secret, challenge, or password.
- [ ] Add in-memory bounded rate limiting keyed by normalized endpoint plus client identity for register/login/2FA/refresh and return 429 with Retry-After.
- [ ] Implement atomic one-time challenge consumption and deletion Cypher using parameters; ensure no `DETACH DELETE` orphan remains for shares/sessions/challenges.
- [ ] Run focused tests, then `./mvnw -q -pl backend/user-service -am verify` and the real Neo4j auth integration suite.
- [ ] Commit `feat: add totp authentication hardening and account deletion`.

### Task 4: Angular Auth Store, Interceptor, Guards, Login, Registration, and 2FA Login

**Files:**
- Modify: `frontend/src/app/app.routes.ts`, `frontend/src/app/app.config.ts`, `frontend/src/app/core/app-state.service.ts`
- Create: `frontend/src/app/core/auth.models.ts`, `auth-api.service.ts`, `auth.store.ts`, `auth.interceptor.ts`, `auth.guards.ts`
- Create: `frontend/src/app/features/auth/login.component.ts`, `register.component.ts`, `two-factor-login.component.ts` and their templates/styles/specs
- Modify: `frontend/src/app/app.component.html`, `app.component.scss`
- Test: Vitest specs for store/interceptor/guards/forms and route behavior

**Interfaces:**
- `AuthStore` exposes signal state `{status, user, accessToken, pendingChallenge}` plus `bootstrap()`, `login()`, `register()`, `verifyTwoFactor()`, `logout()`, and `clear()`; no state is written to Web Storage.
- `AuthApiService` wraps `/auth/register`, `/auth/login`, `/auth/2fa/verify`, `/auth/refresh`, `/auth/logout`, and `/auth/me` with typed DTOs and `withCredentials` only for cookie endpoints.
- `authInterceptor` attaches bearer tokens, coordinates one refresh observable for concurrent 401s, retries each eligible request once, and excludes auth endpoints from recursive refresh.
- Functional `authGuard`, `anonymousOnlyGuard`, and `adminGuard` return `UrlTree` redirects; backend remains authoritative.

- [ ] Write RED Vitest tests proving memory-only token state, single concurrent refresh, one retry, challenge-only 2FA login, generic credential errors, and guard redirects.
- [ ] Implement standalone Material forms with client-side policy hints, loading/429/error states, and no account-existence disclosure.
- [ ] Add `/auth/login`, `/auth/register`, and `/auth/2fa` routes plus startup `POST /auth/refresh` bootstrap.
- [ ] Run `npm test`, `npm run lint`, and `npm run build`; confirm repository search finds no `localStorage` or `sessionStorage` access for auth state.
- [ ] Commit `feat: add angular authentication flows`.

### Task 5: Angular Profile/Security UI and Browser Contract Tests

**Files:**
- Create: `frontend/src/app/features/profile/profile.component.ts`, `profile.component.html`, `profile.component.scss`, `profile.component.spec.ts`
- Create: `frontend/src/app/core/profile-api.service.ts`, `totp.models.ts`
- Modify: `frontend/src/app/app.routes.ts`, `frontend/src/app/app.component.html`
- Modify/create: `frontend/e2e/auth.spec.ts`, `frontend/playwright.config.ts`, `frontend/package.json` only if browser tooling is already pinned by the repository
- Test: component tests plus browser contract tests for login, 2FA, refresh, profile, and deletion

**Interfaces:**
- Profile page calls `GET/PATCH /api/v1/users/me`, `POST /auth/change-password`, `POST /auth/2fa/setup`, `POST /auth/2fa/confirm`, `POST /auth/2fa/disable`, and `DELETE /users/me`.
- Setup QR/manual data is held in component memory, removed after confirmation, and never persisted; account deletion requires explicit confirmation and reauthentication.

- [ ] Write RED component tests for profile editing, 2FA setup/confirm/disable, password change, deletion confirmation, and ratings/watchlist links.
- [ ] Implement `/profile` route with Profile/Security/Ratings/Account sections, accessible Material controls, and bounded error mapping.
- [ ] Add browser contracts that inspect localStorage/sessionStorage, assert refresh cookie flags, verify 2FA challenge replay failure, and confirm protected-route redirects.
- [ ] Run `npm test`, `npm run lint`, `npm run build`, and the browser suite against the local stack; record any environment-only skips explicitly.
- [ ] Commit `feat: add profile and security frontend flows`.

### Task 6: Batch 2 Acceptance Evidence and Checkpoint

**Files:**
- Create: `docs/audit/batch-2-verification.md`
- Modify: `docs/DEVELOPMENT.md`, `scripts/verify.ps1`, `scripts/verify.Tests.ps1` only where auth setup/verification commands are missing
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md`, `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`

**Interfaces:**
- The evidence report records exact commands, commit SHAs, environment prerequisites, and pass/fail output for backend, frontend, integration, and browser security gates.
- The batch status changes to `[x]` only after all auth negative paths, refresh replay rejection, TOTP one-time semantics, no-secret proof, ownership checks, and deletion cleanup are freshly verified.

- [ ] Run the full backend Maven verification and frontend test/lint/build commands from a clean batch worktree.
- [ ] Run live Compose smoke with an ephemeral local `.env`, exercise registration/login/refresh/logout/TOTP/profile/delete, and verify Neo4j contains hashes/encrypted secrets only.
- [ ] Run secret scan, dependency checks, executable-jar checks, and browser storage/cookie assertions; capture outputs in `docs/audit/batch-2-verification.md` without secrets.
- [ ] Have one independent reviewer inspect the complete Batch 2 range against the listed canonical sections and record separate spec-compliance/task-quality verdicts.
- [ ] Mark Batch 2 complete only after review/fix/re-review gates pass; commit `docs: record batch 2 authentication evidence`.
