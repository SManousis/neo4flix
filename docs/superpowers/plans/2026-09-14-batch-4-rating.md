# Batch 4 Rating and Rating-History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement strict Rating Service CRUD, derived movie rating summaries, the User Service rating-history facade, and the authenticated Angular rating/detail/profile flows.

**Architecture:** Rating Service uses a custom Neo4j repository and parameterized Cypher for all `RATED` reads/writes. User Service exposes a read-only facade through a typed `RestClient` call that forwards bearer identity and request ID. Angular adds a typed rating client, accessible 1–5 radio controls on movie detail and a dedicated rating route, and profile history rendering.

**Tech Stack:** Spring Boot 4.1.1, Spring Security resource server, Spring Data Neo4j/Neo4j Java Driver, Testcontainers Neo4j/GDS, Angular 22 standalone components, RxJS, Angular Material, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-14-batch-4-rating-design.md`, `docs/reference/04_API_SPEC.md` sections 29–34, `docs/reference/03_GRAPH_DATABASE_SPEC.md` sections 6/12/15, `docs/reference/05_FRONTEND_SPEC.md` sections 15–16/23, `docs/reference/06_TESTING_SECURITY.md` sections 5/9/19.

## Global Constraints

- `RATED.key` is deterministic `<userId>:<movieId>` and the existing relationship uniqueness constraint remains authoritative.
- Scores are integers 1–5; invalid values fail before database mutation.
- POST is create-only and duplicate create returns `409 RATING_ALREADY_EXISTS`; it is never an upsert.
- PUT requires an existing rating; DELETE is idempotent and deletes only the authenticated user's relationship.
- Movie summaries derive `avg(RATED.score)` and `count(RATED)`; zero ratings return `averageRating: null` and `ratingCount: 0`.
- Every user-scoped query binds the JWT subject; no endpoint accepts a user ID from the caller.
- All user-provided values are Cypher parameters; no dynamic query fragments come from request input.
- Direct-main execution is authorized; do not create worktrees or redispatch Batch 0–3 work.
- Every production behavior change follows TDD: write a failing test, run it red, implement minimally, then run it green.

---

### Task 1: Capture Batch 4 context and typed rating contracts

**Files:**
- Create: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md` (replace the completed Batch 3 handoff with Batch 4 context while preserving Batch 3 audit links)
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md:570-604` only when Batch 4 is finally complete
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/api/RatingWriteRequest.java`
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/api/RatingResponse.java`
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/api/RatingHistoryEntry.java`
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/api/RatingSummaryResponse.java`
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/api/RatingPageResponse.java`
- Modify: `backend/rating-service/pom.xml` to add `spring-boot-starter-validation` for DTO and controller validation.
- Create: `backend/rating-service/src/test/java/com/neo4flix/rating/api/RatingDtoValidationTest.java`

**Interfaces:**
- `RatingWriteRequest(String movieId, int score)` is the only mutation body and validates nonblank movie ID plus score 1–5.
- `RatingResponse` exposes `movieId`, `score`, `createdAt`, and `updatedAt`.
- `RatingHistoryEntry` exposes `movieId`, `movieTitle`, `score`, `createdAt`, and `updatedAt`.
- `RatingPageResponse` exposes `content`, `page`, `size`, `totalElements`, and `totalPages`.
- `RatingSummaryResponse` exposes `movieId`, nullable `averageRating`, and `ratingCount`.

- [ ] **Step 1: Write the failing validation tests** for scores 0 and 6, blank movie IDs, and a valid score 1/5 boundary.
- [ ] **Step 2: Run the DTO test red** with `./mvnw.cmd -pl backend/rating-service -am -Dtest=RatingDtoValidationTest test`; confirm the missing DTO/validation failure is the expected cause.
- [ ] **Step 3: Add `spring-boot-starter-validation`, then add the records and Jakarta validation annotations** exactly matching the interfaces above; use `@Min(1)`, `@Max(5)`, and `@NotBlank`.
- [ ] **Step 4: Run the DTO test green** with the same command.
- [ ] **Step 5: Write the Batch 4 active context** with current `main` head, approved design link, first unfinished task, and Batch 3 completion preserved; do not claim Batch 4 complete.
- [ ] **Step 6: Commit** with `git add docs/superpowers/ACTIVE_BATCH_CONTEXT.md backend/rating-service/src/main backend/rating-service/src/test && git commit -m "feat: define rating service contracts"`.

### Task 2: Implement Rating Service repository and application semantics

**Files:**
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/persistence/RatingRepository.java`
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/RatingApplicationService.java`
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/persistence/RatingRecord.java` as the immutable internal projection returned by custom Cypher
- Modify: `backend/rating-service/src/main/java/com/neo4flix/rating/persistence/RatedRelationship.java` only to expose existing relationship fields needed by the repository tests; do not change its key or timestamp semantics
- Create: `backend/rating-service/src/test/java/com/neo4flix/rating/RatingApplicationServiceTest.java`
- Modify: `backend/rating-service/src/test/java/com/neo4flix/rating/persistence/RatedRelationshipConcurrencyIT.java`

**Interfaces:**
- `RatingRepository.create(String userId, RatingWriteRequest request): RatingResponse`.
- `RatingRepository.findOwn(String userId, String movieId): Optional<RatingResponse>`.
- `RatingRepository.updateOwn(String userId, String movieId, int score): Optional<RatingResponse>`.
- `RatingRepository.deleteOwn(String userId, String movieId): void`.
- `RatingRepository.findHistory(String userId, int page, int size): RatingPageResponse`.
- `RatingRepository.findSummary(String movieId): RatingSummaryResponse`.
- `RatingApplicationService` validates the subject and delegates only these operations; it translates duplicate constraint errors to a typed conflict exception and never catches unrelated database failures as duplicates.

- [ ] **Step 1: Add failing Mockito/unit tests** for create success, duplicate conflict mapping, missing movie/not-found, update-missing 404 behavior, idempotent delete, history paging, and null average for zero ratings.
- [ ] **Step 2: Run `./mvnw.cmd -pl backend/rating-service -am -Dtest=RatingApplicationServiceTest test` red** and confirm failures describe missing repository/service behavior.
- [ ] **Step 3: Implement parameterized Cypher** using `MATCH (user:User {id: $userId})`, `MATCH (movie:Movie {id: $movieId})`, and `CREATE (user)-[:RATED {key: $key, score: $score, createdAt: datetime(), updatedAt: datetime()}]->(movie)`. Use `RatedRelationship.keyFor(userId, movieId)` and map only `Neo.ClientError.Schema.ConstraintValidationFailed` to duplicate conflict.
- [ ] **Step 4: Implement update/delete/history/summary queries** with user ID and movie ID parameters; history must return movie title and relationship timestamps; summary must use `avg(rated.score)` and `count(rated)` and normalize no rows to nullable average plus zero count.
- [ ] **Step 5: Run the unit tests green** with the same Maven command.
- [ ] **Step 6: Add a real Testcontainers concurrency test** that submits two same-user/movie creates through the repository/application boundary and asserts one success, one conflict, and exactly one `RATED` relationship.
- [ ] **Step 7: Run focused integration tests** with `./mvnw.cmd -pl backend/rating-service -am -Dtest=RatedRelationshipConcurrencyIT test` and record the result.
- [ ] **Step 8: Commit** with `git add backend/rating-service && git commit -m "feat: implement rating persistence semantics"`.

### Task 3: Expose and secure the Rating Service HTTP API

**Files:**
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/api/RatingController.java`
- Create: `backend/rating-service/src/test/java/com/neo4flix/rating/api/RatingControllerTest.java`
- Modify: `backend/rating-service/src/test/java/com/neo4flix/rating/RatingServiceApplicationTest.java` only to add endpoint coverage without removing existing health coverage

**Interfaces:**
- `POST /api/v1/ratings` returns `201 RatingResponse`.
- `GET /api/v1/ratings/{movieId}` returns own rating or `404`.
- `PUT /api/v1/ratings/{movieId}` accepts `{ "score": number }` and returns `200 RatingResponse` or `404`.
- `DELETE /api/v1/ratings/{movieId}` returns `204` whether present or absent.
- `GET /api/v1/ratings/me?page=0&size=24` returns `200 RatingPageResponse`; enforce page >= 0 and size 1–100.
- `GET /api/v1/ratings/movies/{movieId}/summary` is anonymous and returns `200 RatingSummaryResponse`.
- `@AuthenticationPrincipal Jwt` supplies the subject; no request body or path parameter can override it.

- [ ] **Step 1: Write MockMvc/WebMvc tests** for anonymous 401 on user-scoped endpoints, authenticated create/update/delete/history, malformed score 400, duplicate 409 problem details, missing update 404, public summary 200, and zero-rating summary.
- [ ] **Step 2: Run `./mvnw.cmd -pl backend/rating-service -am -Dtest=RatingControllerTest test` red**.
- [ ] **Step 3: Implement the controller and exception mapping** using the shared platform Problem Details conventions; keep summary `permitAll` and all other endpoints authenticated.
- [ ] **Step 4: Run controller tests green**, then run `./mvnw.cmd -pl backend/rating-service -am test`.
- [ ] **Step 5: Commit** with `git add backend/rating-service && git commit -m "feat: expose rating api"`.

### Task 4: Add the User Service rating-history facade

**Files:**
- Create: `backend/user-service/src/main/java/com/neo4flix/user/rating/RatingHistoryClient.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/rating/RatingHistoryController.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/rating/RatingHistoryDtos.java`
- Create: `backend/user-service/src/main/java/com/neo4flix/user/rating/RatingHistoryClientTest.java`
- Create: `backend/user-service/src/test/java/com/neo4flix/user/rating/RatingHistoryControllerTest.java`
- Modify: `backend/user-service/src/main/resources/application.yml` to add `NEO4FLIX_RATING_SERVICE_URL` with local default `http://localhost:8083`.
- Modify: `infra/compose.yml` to set the User Service facade URL to `http://rating-service:8083`.
- Modify: `backend/user-service/pom.xml` to add the production Spring Boot RestClient starter.

**Interfaces:**
- `GET /api/v1/users/me/ratings?page=0&size=24` returns the same logical `RatingPageResponse` as Rating Service.
- `RatingHistoryClient.findMine(String bearerToken, String requestId, int page, int size)` performs a read-only GET to `${ratingServiceUrl}/api/v1/ratings/me` and forwards `Authorization` and `X-Request-Id`.
- Downstream 404/validation responses pass through; connection/time-out failures become controlled 503 Problem Details.

- [ ] **Step 1: Write client tests** with a mock `RestClient` exchange proving bearer/request-ID forwarding and downstream error mapping.
- [ ] **Step 2: Write controller tests** proving the local JWT subject is used, pagination bounds are enforced, and no mutation method is called.
- [ ] **Step 3: Run focused User Service tests red** with `./mvnw.cmd -pl backend/user-service -am -Dtest=RatingHistoryClientTest,RatingHistoryControllerTest test`.
- [ ] **Step 4: Implement the typed client, DTOs, controller, configuration property, and Compose URL**.
- [ ] **Step 5: Run the focused tests green**, then run the existing User Service suite.
- [ ] **Step 6: Commit** with `git add backend/user-service infra/compose.yml && git commit -m "feat: add user rating history facade"`.

### Task 5: Add typed Angular rating client and movie-detail/rating route

**Files:**
- Create: `frontend/src/app/core/rating.models.ts`
- Create: `frontend/src/app/core/rating-api.service.ts`
- Create: `frontend/src/app/features/catalog/rating-page.component.ts`
- Create: `frontend/src/app/features/catalog/rating-page.component.spec.ts`
- Modify: `frontend/src/app/features/catalog/movie-detail.component.ts`
- Modify: `frontend/src/app/features/catalog/movie-detail.component.spec.ts` (create if absent)
- Modify: `frontend/src/app/app.routes.ts`

**Interfaces:**
- `RatingResponse`, `RatingHistoryEntry`, `RatingSummary`, and `RatingPage` mirror the backend JSON contracts.
- `RatingApiService.create(movieId, score)`, `.get(movieId)`, `.update(movieId, score)`, `.remove(movieId)`, `.summary(movieId)`, and `.history(page, size)` use `/api/v1/ratings` endpoints.
- `RatingPageComponent` is guarded by `authGuard`, loads the route movie ID and current rating, and uses POST when absent, PUT when present, and DELETE for removal.

- [ ] **Step 1: Write Angular service tests** for exact endpoint/method/body mapping and 404-current-rating normalization.
- [ ] **Step 2: Run the focused frontend test red** with `npm test -- --watch=false --include=src/app/core/rating-api.service.spec.ts`.
- [ ] **Step 3: Implement models/service and route registration** for `/movies/:id/rate` before the parameterized movie-detail route catches it.
- [ ] **Step 4: Write component tests** for accessible 1–5 radio/star controls, create/update/delete state transitions, loading/error/success text, and anonymous login prompt on detail.
- [ ] **Step 5: Implement the movie-detail rating summary/current rating block** and the dedicated route using Material controls and explicit `aria-label`s; do not store tokens in browser storage.
- [ ] **Step 6: Run focused component tests green**, then run the full frontend suite, lint, and build.
- [ ] **Step 7: Commit** with `git add frontend/src/app && git commit -m "feat: add angular rating flows"`.

### Task 6: Render rating history in the profile

**Files:**
- Modify: `frontend/src/app/features/profile/profile.component.ts`
- Modify: `frontend/src/app/features/profile/profile.component.html`
- Modify: `frontend/src/app/features/profile/profile.component.spec.ts`
- Modify: `frontend/src/app/features/profile/profile.component.scss`

**Interfaces:**
- Profile loads `RatingApiService.history(0, 24)` after the authenticated profile succeeds.
- The Ratings card renders loading, empty, error, and populated states; each populated entry links to `/movies/{movieId}` and exposes edit/remove actions through the shared rating API.

- [ ] **Step 1: Add failing profile tests** for history loading, empty state, populated movie links, and a failed facade response.
- [ ] **Step 2: Run the focused profile test red** with `npm test -- --watch=false --include=src/app/features/profile/profile.component.spec.ts`.
- [ ] **Step 3: Implement the history signal/load path and accessible template states**; keep existing profile/security behavior unchanged.
- [ ] **Step 4: Run profile tests green**, then run the full frontend suite, lint, and build.
- [ ] **Step 5: Commit** with `git add frontend/src/app/features/profile && git commit -m "feat: show rating history in profile"`.

### Task 7: Live browser contract, audit evidence, and Batch 4 checkpoint

**Files:**
- Create: `frontend/e2e/ratings.spec.ts`
- Create: `docs/audit/batch-4-verification.md`
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md:570-604` only after every gate passes

- [ ] **Step 1: Write the credential-gated Playwright flow**: register disposable user, seed/locate an existing movie, rate it, edit the score, remove it, verify profile history, and clean up the disposable user/relationship.
- [ ] **Step 2: Run the default browser suite** and confirm existing auth/catalog/admin contracts still pass.
- [ ] **Step 3: Run the disposable authenticated rating browser test** against rebuilt Compose; assert POST/PUT/DELETE requests and visible success/error states.
- [ ] **Step 4: Run final verification**: `./scripts/verify.ps1`, `./scripts/smoke-compose.ps1 -EnvFile .env`, full frontend tests/lint/build, focused rating concurrency tests, and `git diff --check`.
- [ ] **Step 5: Record exact counts, commands, fixture cleanup, and any non-blocking review notes** in `docs/audit/batch-4-verification.md`; never record secrets.
- [ ] **Step 6: Obtain independent review and resolve all Critical/Important findings.**
- [ ] **Step 7: Change only Batch 4 status to `[x]` in `docs/reference/00_MASTER_EXECUTION_PLAN.md`, update active context to the next unfinished Batch 5 workstream, commit, push `main`, and verify `HEAD == origin/main`.
