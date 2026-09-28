# Batch 5 Watchlist Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task on the direct `main` checkout.

**Goal:** Deliver authenticated, idempotent watchlist add/list/remove behavior across User Service, Neo4j, and Angular, with ownership, concurrency, and browser evidence.

**Architecture:** User Service owns `WATCHLISTED` writes and reads through a custom `Neo4jClient` repository. The repository binds JWT-derived user IDs and movie IDs, uses `MERGE` with deterministic keys, and returns typed movie-summary projections. Angular uses a typed watchlist client and an authenticated page; movie detail/catalog actions call the same API and keep server state authoritative.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security JWT, Spring Data Neo4j `Neo4jClient`, Neo4j Testcontainers, Angular standalone components, RxJS, Angular Material, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-14-batch-5-watchlist-design.md`

## Global Constraints

- `WATCHLISTED.key` is deterministic `<userId>:<movieId>` and unique among `WATCHLISTED`.
- Add uses `MERGE`; repeated add is idempotent and concurrent adds leave one relationship.
- Every personalized mutation derives identity from the JWT subject; no request accepts an arbitrary `userId`.
- All Cypher values are parameters; no dynamic user/movie values are interpolated.
- Watchlist is not a recommendation preference signal in this batch.
- Angular must expose loading, empty, error, and populated states and must not store long-lived auth secrets in browser storage.
- Work directly on `main`; do not create worktrees or redispatch completed Batches 0–4.

---

### Task 1: Define watchlist contracts and repository boundary

**Files:**
- Create: `backend/user-service/src/main/java/com/neo4flix/user/watchlist/WatchlistDtos.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/watchlist/WatchlistRepository.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/watchlist/WatchlistApplicationService.java`
- Test: `backend/user-service/src/test/java/com/neo4flix/user/watchlist/WatchlistApplicationServiceTest.java`
- Test: `backend/user-service/src/test/java/com/neo4flix/user/watchlist/WatchlistRepositoryTest.java`

**Interfaces:**
- `WatchlistDtos.MovieEntry(String movieId, String title, String overview, Integer releaseYear, String posterUrl, Instant createdAt)`.
- `WatchlistDtos.PageResponse(List<MovieEntry> content, int page, int size, long totalElements, int totalPages)`.
- `WatchlistRepository.add(String userId, String movieId): boolean` returns whether the relationship was newly created; `remove(String userId, String movieId): boolean`; `findMine(String userId, int page, int size): PageResponse`.
- `WatchlistApplicationService` delegates the three operations and translates missing movie targets to the documented not-found exception without accepting a caller-supplied user ID.

- [ ] **Step 1: Write failing tests** for add-created versus repeated-add idempotency, missing movie, own paged history projection, idempotent remove, and page/size bounds.
- [ ] **Step 2: Run the focused User Service tests red** with `./mvnw.cmd -pl backend/user-service -am -Dtest=WatchlistApplicationServiceTest,WatchlistRepositoryTest test`; confirm failures are missing watchlist contracts/behavior.
- [ ] **Step 3: Implement DTOs, repository Cypher, and application service.** Use `MERGE (user)-[w:WATCHLISTED {key: $key}]->(movie) ON CREATE SET w.createdAt = datetime()` for add, match the same user/movie/key for remove, and use a parameterized paged query returning movie summary fields and `createdAt`.
- [ ] **Step 4: Run the focused tests green** with the same Maven command.
- [ ] **Step 5: Commit** `git add backend/user-service/src/main/java/com/neo4flix/user/watchlist backend/user-service/src/test/java/com/neo4flix/user/watchlist && git commit -m "feat: add watchlist repository contracts"`.

### Task 2: Expose and secure the watchlist HTTP API

**Files:**
- Create: `backend/user-service/src/main/java/com/neo4flix/user/watchlist/WatchlistController.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/watchlist/WatchlistApiExceptionHandler.java`
- Test: `backend/user-service/src/test/java/com/neo4flix/user/watchlist/WatchlistControllerTest.java`
- Test: `backend/user-service/src/test/java/com/neo4flix/user/watchlist/WatchlistIsolationIT.java`

**Interfaces:**
- `GET /api/v1/users/me/watchlist?page=0&size=24` returns `200 WatchlistDtos.PageResponse`.
- `POST /api/v1/users/me/watchlist/{movieId}` returns `201` on first add and `204` on a repeated add; both leave the desired final state.
- `DELETE /api/v1/users/me/watchlist/{movieId}` returns `204` whether present or absent.
- All endpoints use `@AuthenticationPrincipal Jwt jwt` and pass `jwt.getSubject()` to the application service.

- [ ] **Step 1: Write failing MockMvc tests** for anonymous `401`, subject forwarding, valid paging, invalid page/size `400`, first/repeated add status, idempotent delete, and missing movie `404` Problem Details.
- [ ] **Step 2: Run `./mvnw.cmd -pl backend/user-service -am -Dtest=WatchlistControllerTest test` red.**
- [ ] **Step 3: Implement controller and exception mapping** using the existing Problem Details conventions; do not accept a user ID or expose relationship keys.
- [ ] **Step 4: Run controller tests green**, then run the existing User Service suite.
- [ ] **Step 5: Commit** `git add backend/user-service/src/main/java/com/neo4flix/user/watchlist backend/user-service/src/test/java/com/neo4flix/user/watchlist && git commit -m "feat: expose watchlist api"`.

### Task 3: Prove real Neo4j idempotency, concurrency, and isolation

**Files:**
- Modify: `backend/user-service/src/test/java/com/neo4flix/user/persistence/WatchlistedRelationshipConcurrencyIT.java` to call the repository/application boundary instead of raw Cypher while retaining a final relationship-count query.
- Create: `backend/user-service/src/test/java/com/neo4flix/user/watchlist/WatchlistConcurrencyIT.java` if the existing fixture cannot inject the repository cleanly.
- Test: `backend/user-service/src/test/java/com/neo4flix/user/watchlist/WatchlistIsolationIT.java`

- [ ] **Step 1: Add a failing real-container test** that creates two users and one movie, concurrently adds the same user/movie through `WatchlistRepository.add`, and asserts two successful final-state calls but exactly one `WATCHLISTED` relationship.
- [ ] **Step 2: Add isolation assertions** that user A’s page excludes user B’s movie and user A’s remove does not remove user B’s relationship.
- [ ] **Step 3: Run the focused integration command** `./mvnw.cmd -pl backend/user-service -am -Dtest=WatchlistedRelationshipConcurrencyIT,WatchlistIsolationIT test` and fix only implementation/test-boundary defects.
- [ ] **Step 4: Commit** `git add backend/user-service/src/test/java/com/neo4flix/user/persistence/WatchlistedRelationshipConcurrencyIT.java backend/user-service/src/test/java/com/neo4flix/user/watchlist && git commit -m "test: verify watchlist concurrency and isolation"`.

### Task 4: Add the typed Angular watchlist client and page

**Files:**
- Create: `frontend/src/app/core/watchlist.models.ts`
- Create: `frontend/src/app/core/watchlist-api.service.ts`
- Create: `frontend/src/app/core/watchlist-api.service.spec.ts`
- Create: `frontend/src/app/features/watchlist/watchlist.component.ts`
- Create: `frontend/src/app/features/watchlist/watchlist.component.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`

**Interfaces:**
- `WatchlistEntry` mirrors the backend movie entry and includes `movieId`, `title`, `overview`, `releaseYear`, `posterUrl`, and `createdAt`.
- `WatchlistApiService.list(page = 0, size = 24)`, `.add(movieId)`, and `.remove(movieId)` map exactly to the three API paths and methods.
- `WatchlistComponent` renders `Loading watchlist…`, `Your watchlist is empty.`, an error alert with retry, or a `data-testid="watchlist"` list; every item has a detail link and a `Remove` button.

- [ ] **Step 1: Write failing service/component tests** for exact HTTP mapping, loading/empty/error/populated states, successful remove, and failed remove restoring the item.
- [ ] **Step 2: Run the focused frontend tests red** with `npm test -- --watch=false --include=src/app/core/watchlist-api.service.spec.ts --include=src/app/features/watchlist/watchlist.component.spec.ts`.
- [ ] **Step 3: Implement models, service, standalone page, and `authGuard` route** at `/watchlist`; keep all tokens in the existing in-memory AuthStore/interceptor path.
- [ ] **Step 4: Run focused tests green**, then run the full frontend suite, lint, and build.
- [ ] **Step 5: Commit** `git add frontend/src/app/core/watchlist* frontend/src/app/features/watchlist frontend/src/app/app.routes.ts && git commit -m "feat: add angular watchlist page"`.

### Task 5: Integrate movie detail/catalog actions and profile navigation

**Files:**
- Modify: `frontend/src/app/core/catalog.models.ts` only if the movie projection needs an optional `watchlisted` field.
- Modify: `frontend/src/app/core/catalog-api.service.ts` only if the existing movie summary/detail endpoint contract needs typed watchlist state.
- Modify: `frontend/src/app/features/catalog/movie-detail.component.ts` and its spec.
- Modify: `frontend/src/app/features/catalog/catalog.component.ts` and its spec.
- Modify: `frontend/src/app/features/profile/profile.component.html` only to make the existing watchlist shortcut route valid and accessible.

- [ ] **Step 1: Add failing component tests** for anonymous login prompt, authenticated add/remove action, success status, and mutation failure recovery on movie detail; add catalog-card watchlist action coverage only if the current catalog response supplies viewer state.
- [ ] **Step 2: Run focused component tests red.**
- [ ] **Step 3: Implement the smallest shared toggle behavior** using `WatchlistApiService`; do not duplicate a permanent global store or add recommendation signals.
- [ ] **Step 4: Run focused and full frontend tests green**, lint, and build.
- [ ] **Step 5: Commit** `git add frontend/src/app/core frontend/src/app/features/catalog frontend/src/app/features/profile && git commit -m "feat: add watchlist movie actions"`.

### Task 6: Browser contract, audit, and Batch 5 checkpoint

**Files:**
- Create: `frontend/e2e/watchlist.spec.ts`
- Create: `docs/audit/batch-5-verification.md`
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md` only after every gate passes

- [ ] **Step 1: Write the credential-gated Playwright flow**: register a disposable user, locate an audit movie, add it from detail, verify the watchlist page and movie link, remove it, assert POST/GET/DELETE statuses and visible states, and delete the user in `finally`.
- [ ] **Step 2: Rebuild Compose and run the browser flow** against explicit audit seed data without recording `.env` values.
- [ ] **Step 3: Run final verification:** `./scripts/verify.ps1 -TestOnly` with configured `JAVA_HOME`, full frontend tests/lint/build, focused watchlist concurrency, `./scripts/smoke-compose.ps1 -EnvFile .env`, browser suite, and `git diff --check`.
- [ ] **Step 4: Record exact commands/counts, fixture cleanup, and any non-blocking review notes** in `docs/audit/batch-5-verification.md`.
- [ ] **Step 5: Self-review for Critical/Important issues**, resolve them, mark only Batch 5 `[x]`, update active context to Batch 6, commit, push `main`, and verify local `HEAD` equals `origin/main`.
