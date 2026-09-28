# Recommendation Sharing and CRUD Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete RecommendationShare CRUD and deliver secure anonymous movie-share links with hash-only token persistence and Angular creation/public viewing flows.

**Architecture:** Recommendation Service owns a parameterized `Neo4jClient` repository, an application service that derives ownership from JWT `sub`, a token codec using `SecureRandom` plus SHA-256, and thin owner/public controllers. The shared resource-server security configuration explicitly permits only the public lookup route. Angular adds a typed share client, a Share action on recommendation cards, and an anonymous `/share/:publicToken` page.

**Tech Stack:** Spring Boot 4, Spring MVC, Spring Security resource server, Neo4j `Neo4jClient`, JUnit/Mockito/MockMvc/Testcontainers, Angular standalone components, HttpClient, RxJS, Vitest.

**Spec:** `docs/superpowers/specs/2026-09-15-batch-8-recommendation-sharing-design.md`

## Global Constraints

- Never persist or log raw public share tokens.
- Never accept arbitrary owner identity or expose another user's share metadata.
- Never expose creator email, watchlist, ratings, vectors, peer identities, or internal graph properties publicly.
- Never interpolate token, movie id, expiry, or other request data into Cypher.
- Keep public sharing separate from ranking and recommendation signal computation.
- Work directly on `main`; do not create worktrees or redispatch completed batches.
- Use red-test → minimal implementation → focused green test → broader regression verification for each behavior change.
- The raw token is returned only in the successful creation response and exists in the URL/client; Neo4j stores its hash, not the raw token.

## File map

Backend files are grouped by responsibility: token/time primitives, share persistence, application/API contracts, and shared authorization/deletion behavior. Frontend files separate transport models/client from the recommendations card action and public page. Tests sit beside each unit and extend existing controller, persistence, security, and Angular suites.

### Task 1: Establish token, expiry, and DTO contracts with red tests

**Files:**
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/share/RecommendationShareTokenService.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/share/RecommendationShareProperties.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/share/RecommendationShareModels.java`
- Create: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/share/RecommendationShareTokenServiceTest.java`
- Create: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/share/RecommendationShareModelsTest.java`
- Modify: `backend/recommendation-service/src/main/resources/application.yml`

**Interfaces:**
- `RecommendationShareTokenService.issue()` returns `IssuedToken(String rawToken, String hash)`.
- `RecommendationShareTokenService.hash(String rawToken)` returns the canonical lowercase SHA-256 hex string.
- `RecommendationShareProperties` exposes `defaultExpiryDays=30`, `minExpiryDays=1`, and `maxExpiryDays=365` with Spring configuration prefix `neo4flix.recommendation.share`.
- `RecommendationShareModels` defines records `CreateRequest(String movieId, Integer expiresInDays)`, `UpdateRequest(Integer expiresInDays, Boolean revoke)`, `OwnerView`, `CreatedView` (with raw token), `PublicView`, `PublicMovie`, and `UnavailableShareException`.

- [x] **Step 1: Write failing token tests** asserting a 32-byte URL-safe token, distinct successive tokens, deterministic hash output, and no raw token in the hash.
- [x] **Step 2: Run the focused test to verify it fails**

Run: `mvn -B -pl backend/recommendation-service -Dtest=RecommendationShareTokenServiceTest test`

Expected: FAIL because the token service and records do not exist.

- [x] **Step 3: Implement the token codec and bounded expiry properties** using `SecureRandom`, `Base64.getUrlEncoder().withoutPadding()`, `MessageDigest.getInstance("SHA-256")`, and constructor validation that rejects non-positive or inverted bounds.
- [x] **Step 4: Add request/response records and configuration defaults** without exposing persistence node types.
- [x] **Step 5: Run the focused tests**

Run: `mvn -B -pl backend/recommendation-service -Dtest=RecommendationShareTokenServiceTest,RecommendationShareModelsTest test`

Expected: PASS.

- [x] **Step 6: Commit**

```text
git add backend/recommendation-service/src/main backend/recommendation-service/src/test
git commit -m "feat: add recommendation share token contracts"
```

### Task 2: Implement parameterized RecommendationShare persistence

**Files:**
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence/RecommendationShareRepository.java`
- Create: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/persistence/RecommendationShareRepositoryTest.java`
- Modify: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/persistence/RecommendationPersistenceMappingTest.java`

**Interfaces:**
- `create(String ownerId, String movieId, String shareId, String tokenHash, Instant createdAt, Instant expiresAt)` returns `Optional<OwnerView>` and creates both relationships atomically.
- `listOwned(String ownerId)` returns `List<OwnerView>` ordered by `createdAt DESC`.
- `findOwned(String ownerId, String shareId)` returns `Optional<OwnerView>`.
- `updateOwned(String ownerId, String shareId, Instant expiresAt, boolean revoke, Instant now)` returns `Optional<OwnerView>`.
- `deleteOwned(String ownerId, String shareId)` returns a boolean.
- `findPublic(String tokenHash, Instant now)` returns `Optional<PublicView>` and filters revoked/expired shares in Cypher.

- [x] **Step 1: Write repository contract tests** with a mocked `Neo4jClient` verifying every query binds `ownerId`, `shareId`, `movieId`, `tokenHash`, `now`, and timestamps as parameters; verify public projection has no creator or token-hash field.
- [x] **Step 2: Run the focused repository test to verify it fails**

Run: `mvn -B -pl backend/recommendation-service -Dtest=RecommendationShareRepositoryTest test`

Expected: FAIL because the repository contract is absent.

- [x] **Step 3: Implement the nested `@Repository` Neo4j adapter** using explicit `MATCH`, `CREATE`, `SET`, `DETACH DELETE`, and safe projection queries. Use fixed `ORDER BY s.createdAt DESC` and no string interpolation of request values.
- [x] **Step 4: Extend mapping assertions** to retain `RecommendationShareNode` label/id/relationship-direction coverage and document that API paths use DTO projections.
- [x] **Step 5: Run repository and mapping tests**

Run: `mvn -B -pl backend/recommendation-service -Dtest=RecommendationShareRepositoryTest,RecommendationPersistenceMappingTest test`

Expected: PASS.

- [x] **Step 6: Commit**

```text
git add backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence backend/recommendation-service/src/test/java/com/neo4flix/recommendation/persistence
git commit -m "feat: add parameterized recommendation share repository"
```

### Task 3: Add owner/public application behavior and controller red tests

**Files:**
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/share/RecommendationShareApplicationService.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/share/RecommendationShareController.java`
- Create: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/share/RecommendationShareApplicationServiceTest.java`
- Create: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/share/RecommendationShareControllerTest.java`

**Interfaces:**
- `RecommendationShareApplicationService.create(String ownerId, CreateRequest request, Clock clock)` returns `CreatedView`.
- `list(String ownerId)`, `get(String ownerId, String shareId)`, `update(String ownerId, String shareId, UpdateRequest request)`, and `delete(String ownerId, String shareId)` implement owner isolation.
- `publicLookup(String rawToken, Clock clock)` returns `PublicView` or throws the shared not-found exception.
- Controller routes are `POST/GET/PATCH/DELETE /api/v1/recommendation-shares` and `GET /api/v1/shares/{publicToken}`. Owner identity is read from `JwtClaims.subject()` and never from JSON/query data.

- [x] **Step 1: Write failing service tests** for default expiry, minimum/maximum bounds, movie-not-found, owner isolation, revoke irreversibility, random-token public lookup failure, and raw-token-only-on-create behavior.
- [x] **Step 2: Write failing MockMvc tests** for `201`, owner CRUD statuses, malformed/invalid body `400`, anonymous public `200`, and public invalid/expired/revoked `404`.
- [x] **Step 3: Run the focused tests to verify they fail**

Run: `mvn -B -pl backend/recommendation-service -Dtest=RecommendationShareApplicationServiceTest,RecommendationShareControllerTest test`

Expected: FAIL because application/controller classes and routes do not exist.

- [x] **Step 4: Implement application validation and token collision retry** (up to three generated-token attempts, then a generic server failure), calculate `expiresAt` from the injected UTC clock, and map repository optionals to the shared Problem Details behavior.
- [x] **Step 5: Implement thin controllers** with `@Valid` request records, `ResponseEntity` status mapping, and no persistence types in responses.
- [x] **Step 6: Run the focused tests**

Run: `mvn -B -pl backend/recommendation-service -Dtest=RecommendationShareApplicationServiceTest,RecommendationShareControllerTest test`

Expected: PASS.

- [x] **Step 7: Commit**

```text
git add backend/recommendation-service/src/main/java/com/neo4flix/recommendation/share backend/recommendation-service/src/test/java/com/neo4flix/recommendation/share
git commit -m "feat: expose recommendation share CRUD"
```

### Task 4: Permit only public share lookup and verify cleanup

**Files:**
- Modify: `backend/platform-common/src/main/java/com/neo4flix/platform/common/security/ResourceServerSecurityConfig.java`
- Modify: `backend/platform-common/src/test/java/com/neo4flix/platform/common/security/ResourceServerSecurityConfigTest.java`
- Modify: `backend/movie-service/src/main/java/com/neo4flix/movie/catalog/MovieCatalogRepository.java`
- Modify: `backend/movie-service/src/test/java/com/neo4flix/movie/catalog/MovieCatalogRepositoryTest.java`
- Modify: `backend/user-service/src/test/java/com/neo4flix/user/auth/AuthProductionContextIT.java`

**Interfaces:**
- `GET /api/v1/shares/**` is permitted anonymously; `POST/PATCH/DELETE /api/v1/recommendation-shares` and owner `GET` remain authenticated through `.anyRequest().authenticated()`.
- Movie deletion removes `(:RecommendationShare)-[:SHARES]->(:Movie)` share nodes before detaching the Movie.
- Existing account deletion removes `(:User)-[:CREATED_SHARE]->(:RecommendationShare)` nodes; tests retain that invariant.

- [x] **Step 1: Write failing security and cleanup tests** asserting anonymous public lookup is permitted, owner CRUD is protected, and movie deletion query contains share cleanup before `DETACH DELETE m`.
- [x] **Step 2: Run focused platform/movie tests to verify failure**

Run: `mvn -B -pl backend/platform-common,backend/movie-service -am -Dtest=ResourceServerSecurityConfigTest,MovieCatalogRepositoryTest test`

Expected: FAIL on the missing public matcher and missing share cleanup.

- [x] **Step 3: Add the explicit GET matcher before authenticated fall-through** and change movie deletion to delete inbound RecommendationShare nodes in the same parameterized operation before detaching the movie.
- [x] **Step 4: Run focused tests and account deletion integration coverage**

Run: `mvn -B -pl backend/platform-common,backend/movie-service,backend/user-service -am -Dtest=ResourceServerSecurityConfigTest,MovieCatalogRepositoryTest,AuthProductionContextIT test`

Expected: PASS (integration test may be skipped only when Docker/Testcontainers is unavailable).

- [x] **Step 5: Commit**

```text
git add backend/platform-common backend/movie-service backend/user-service
git commit -m "fix: secure public shares and clean deleted movies"
```

### Task 5: Add typed Angular share client and public route/page

**Files:**
- Create: `frontend/src/app/core/recommendation-share.models.ts`
- Create: `frontend/src/app/core/recommendation-share-api.service.ts`
- Create: `frontend/src/app/features/share/share.component.ts`
- Create: `frontend/src/app/features/share/share.component.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`

**Interfaces:**
- `RecommendationShareApiService.create(movieId: string, expiresInDays?: number): Observable<CreatedRecommendationShare>`.
- `list()`, `get(id)`, `update(id, request)`, and `remove(id)` map owner CRUD routes.
- `publicLookup(publicToken: string): Observable<PublicRecommendationShare>` calls `/api/v1/shares/{encodeURIComponent(publicToken)}`.
- `ShareComponent` loads `ActivatedRoute.paramMap`, renders public movie-safe fields, and maps every `HttpErrorResponse` to one generic unavailable message.

- [x] **Step 1: Write failing client/page tests** for URL encoding, public success rendering, generic 404 rendering, no creator/private fields, and route registration.
- [x] **Step 2: Run frontend focused tests to verify failure**

Run: `npm test -- --watch=false --include='src/app/features/share/share.component.spec.ts'`

Expected: FAIL because the client, component, and route do not exist.

- [x] **Step 3: Implement typed models/client** with `HttpParams` only for optional expiry and no token logging.
- [x] **Step 4: Implement standalone anonymous share page** with loading/status/error states, movie detail link, browse/register links, and plain-text interpolation for user-visible fields.
- [x] **Step 5: Register `share/:publicToken` before the wildcard route** without `authGuard`.
- [x] **Step 6: Run focused frontend tests**

Run: `npm test -- --watch=false --include='src/app/features/share/share.component.spec.ts'`

Expected: PASS.

- [x] **Step 7: Commit**

```text
git add frontend/src/app/core frontend/src/app/features/share frontend/src/app/app.routes.ts
git commit -m "feat: add anonymous recommendation share page"
```

### Task 6: Add recommendation-card share creation and Clipboard fallback

**Files:**
- Modify: `frontend/src/app/features/recommendations/recommendations.component.ts`
- Modify: `frontend/src/app/features/recommendations/recommendations.component.spec.ts`
- Modify: `frontend/src/app/core/recommendation-share-api.service.ts`

**Interfaces:**
- `RecommendationsComponent` injects `RecommendationShareApiService`, exposes one Share button per movie card, and stores only the returned `publicPath`/status in component state.
- `copyPublicUrl(url: string): Promise<void>` uses `navigator.clipboard.writeText` when available and a temporary textarea fallback otherwise.

- [x] **Step 1: Write failing component tests** for share button delegation, displayed public URL, successful Clipboard API copy, fallback copy, and visible failure state.
- [x] **Step 2: Run the focused component test to verify failure**

Run: `npm test -- --watch=false --include='src/app/features/recommendations/recommendations.component.spec.ts'`

Expected: FAIL because the Share action and service injection do not exist.

- [x] **Step 3: Implement the minimal share state/action** with per-movie busy state, `finalize`, and generic error copy. Use `window.location.origin + publicPath` for the displayed URL and never log the raw token.
- [x] **Step 4: Run recommendation component tests**

Run: `npm test -- --watch=false --include='src/app/features/recommendations/recommendations.component.spec.ts'`

Expected: PASS.

- [x] **Step 5: Commit**

```text
git add frontend/src/app/features/recommendations frontend/src/app/core/recommendation-share-api.service.ts
git commit -m "feat: add recommendation share action"
```

### Task 7: Complete audit evidence and regression verification

**Files:**
- Create: `docs/audit/batch-8-verification.md`
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md`

- [x] **Step 1: Run backend focused and full tests**

Run:

```text
mvn -B -pl backend/recommendation-service -am test
mvn -B test
```

Expected: BUILD SUCCESS, zero failures/errors; record exact module test counts.

- [x] **Step 2: Run frontend, lint, and build verification**

Run:

```text
npm test -- --watch=false
npm run lint
npm run build
```

Expected: all tests pass, lint has zero warnings, build succeeds.

- [x] **Step 3: Run Compose interpolation and serial Playwright share contract** using command-scoped placeholders only; do not create or print `.env`. Record skipped E2E tests explicitly when credentials are absent.
- [x] **Step 4: Run `git diff --check` and confirm `git status --short` contains only intended audit/context/plan changes.**
- [x] **Step 5: Populate the audit with token non-persistence, ownership, expiry/revocation, public privacy, and deletion evidence. Mark Batch 8 complete in `docs/reference/00_MASTER_EXECUTION_PLAN.md` and update the active context to Batch 9.
- [x] **Step 6: Commit the audit/context/plan status update**

```text
git add docs/audit/batch-8-verification.md docs/superpowers/ACTIVE_BATCH_CONTEXT.md docs/reference/00_MASTER_EXECUTION_PLAN.md
git commit -m "docs: verify batch 8 recommendation sharing"
```

- [x] **Step 7: Push `main` and verify remote equality**

Run:

```text
git push origin main
git rev-parse HEAD
git ls-remote origin refs/heads/main
```

Expected: local HEAD equals the remote `main` SHA.
