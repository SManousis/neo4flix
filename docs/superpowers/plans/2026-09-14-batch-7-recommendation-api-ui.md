# Batch 7 Recommendation API, Filters, UI, and Movie Facade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the Batch 6 recommendation core through authenticated APIs, an equivalent Movie Service facade, and an Angular recommendations experience.

**Architecture:** Recommendation Service owns JWT-sub identity, query validation, response mapping, sorting, and paging around `RecommendationApplicationService`. Movie Service uses one outbound `RestClient` boundary that forwards the bearer/request ID and maps downstream outages to `503`; its normal catalog repository remains independent. Angular owns URL-backed filter state and rendering only; ranking and reason text remain server-authoritative.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC/RestClient, Spring Security JWT, Neo4j core from Batch 6, Angular 22.1.5, Angular Material, Reactive Forms, RxJS, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-14-batch-7-recommendation-api-ui-design.md`

## Global Constraints

- Identity comes only from the JWT `sub`; no request-supplied `userId` is accepted.
- Query bounds are genre 80 chars, years 1888–2200, rating 0–5, page 0–10000, size 1–50.
- Sort allowlist is `recommendation`, `rating`, `newest`; unknown values return shared Problem Details `400`.
- Do not change Batch 6 scoring, GDS, candidate exclusion, or privacy rules.
- No peer IDs, private vectors, rating history, watchlist relationships, bearer tokens, Cypher, or credentials are returned/logged.
- Movie browsing/detail/search/genres remain available when Recommendation Service is unavailable.
- Work directly on `main`; preserve `.env` and do not create worktrees.

---

### Task 1: Extend core result data and expose Recommendation Service HTTP API

**Files:**
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/api/RecommendationApiModels.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/api/RecommendationController.java`
- Modify: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationDtos.java`
- Modify: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationApplicationService.java`
- Modify: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence/RecommendationNeo4jRepository.java`
- Test: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/api/RecommendationControllerTest.java`
- Test: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/RecommendationServiceApplicationTest.java`

**Interfaces:**
- `RecommendationApiModels.QueryParams(genre, fromYear, toYear, minimumAverageRating, sort, page, size)` validates the global bounds and maps to a bounded core query with `limit=50` and `candidateLimit=50`.
- `RecommendationApiModels.Response(List<Item> items, Strategy strategy, int page, int size, long totalItems, int totalPages)` is the public envelope.
- `RecommendationController.recommend(QueryParams, Jwt)` serves `GET /api/v1/recommendations/me`; it rejects invalid input, uses `new JwtClaims(jwt).subject()`, sorts deterministically, slices the bounded result, and never accepts `userId`.
- `Item` contains `MovieSummary`, `recommendationScore`, `Signals(collaborative, content, popularity)`, `Strategy`, and typed `Reason(type,text)`.

- [x] **Step 1: Write failing MVC tests** for JWT-sub identity, `userId` rejection/ignorance, default query mapping, each invalid bound, sort allowlist, deterministic paging, response privacy, and strategy/reason mapping.
- [x] **Step 2: Run the focused tests red.** Run `mvn -B -pl backend/recommendation-service -am -Dtest=RecommendationControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected failure is missing API types/controller.
- [x] **Step 3: Implement the DTOs, core-result metadata needed by the public movie summary, controller validation, typed reason mapping (`SIMILAR_USERS`, `GENRE_MATCH`, `POPULAR`), deterministic sort/page, and bounded response mapping. Add `releaseDate`/genre IDs only through parameterized candidate projection changes.**
- [x] **Step 4: Run focused controller and existing recommendation tests green.** Verify no response field contains peer identity, vectors, or watchlist data.
- [x] **Step 5: Commit** `git add backend/recommendation-service/src/main backend/recommendation-service/src/test && git commit -m "feat: expose recommendation api"`.

### Task 2: Add the Movie Service recommendation facade

**Files:**
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/recommendation/MovieRecommendationClient.java`
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/recommendation/MovieRecommendationController.java`
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/recommendation/MovieRecommendationProperties.java`
- Modify: `backend/movie-service/pom.xml` (make `spring-boot-restclient` a runtime dependency)
- Modify: `backend/movie-service/src/main/resources/application.yml`
- Test: `backend/movie-service/src/test/java/com/neo4flix/movie/recommendation/MovieRecommendationClientTest.java`
- Test: `backend/movie-service/src/test/java/com/neo4flix/movie/recommendation/MovieRecommendationControllerTest.java`

**Interfaces:**
- `MovieRecommendationClient.fetch(HttpHeaders incomingHeaders, MultiValueMap<String,String> query): ResponseEntity<String>` forwards only allowlisted query parameters to `${neo4flix.recommendation.base-url}/api/v1/recommendations/me`, copying `Authorization` and `X-Request-Id` and using a bounded connect/read timeout.
- `MovieRecommendationProperties` binds `neo4flix.recommendation.base-url` and timeout values with local defaults suitable for Compose (`http://recommendation-service:8084`).
- `MovieRecommendationController.recommend(HttpServletRequest, query params)` serves `GET /api/v1/movies/recommended`, delegates once, passes successful/validation status and body through, and maps connection/refusal/timeout/downstream-5xx to shared `503` code `RECOMMENDATION_SERVICE_UNAVAILABLE`.

- [x] **Step 1: Write failing client/controller tests** for exact bearer/request-ID propagation, equivalent filter forwarding, no arbitrary query forwarding, success passthrough, downstream 400 passthrough, timeout/refusal/5xx `503`, and ordinary `/movies` independence.
- [x] **Step 2: Run the focused Movie Service tests red.** Run `mvn -B -pl backend/movie-service -am -Dtest=MovieRecommendationClientTest,MovieRecommendationControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [x] **Step 3: Implement properties, RestClient boundary, allowlisted query encoding, header propagation, and controlled Problem Details outage handling without logging credentials.**
- [x] **Step 4: Run the focused facade tests and existing Movie Service tests green.**
- [x] **Step 5: Commit** `git add backend/movie-service && git commit -m "feat: add movie recommendation facade"`.

### Task 3: Add typed Angular recommendation client and route-backed models

**Files:**
- Create: `frontend/src/app/core/recommendation.models.ts`
- Create: `frontend/src/app/core/recommendation-api.service.ts`
- Modify: `frontend/src/app/app.routes.ts`
- Test: `frontend/src/app/core/recommendation-api.service.spec.ts`
- Test: `frontend/src/app/app.routes.spec.ts`

**Interfaces:**
- `RecommendationFilters { genre, fromYear, toYear, minimumAverageRating, sort, page, size }` uses the API names and defaults (`sort='recommendation'`, `page=0`, `size=20`).
- `RecommendationApiService.list(filters): Observable<RecommendationResponse>` calls `/api/v1/recommendations/me` with non-empty query params; it never ranks, rewrites reason text, or stores access tokens.
- Add `{ path: 'recommendations', canActivate: [authGuard], loadComponent: ... }`.

- [x] **Step 1: Write failing service/route tests** for exact query encoding, omission of empty filters, 400/503 error propagation, and guarded route registration.
- [x] **Step 2: Run `npm test -- --runInBand` from `frontend` red or targeted Vitest files red.**
- [x] **Step 3: Implement typed models/service and lazy route.**
- [x] **Step 4: Run targeted Angular tests green.**
- [x] **Step 5: Commit** `git add frontend/src/app/core/recommendation* frontend/src/app/app.routes.ts frontend/src/app/app.routes.spec.ts && git commit -m "feat: add recommendation api client"`.

### Task 4: Build the recommendations page and navigation experience

**Files:**
- Create: `frontend/src/app/features/recommendations/recommendations.component.ts`
- Create: `frontend/src/app/features/recommendations/recommendations.component.spec.ts`
- Modify: `frontend/src/app/app.component.spec.ts` if navigation assertions require it
- Modify: `frontend/src/app/app.component.html` only to preserve/verify the existing Recommendations links

**Interfaces:**
- `RecommendationsComponent` reads `ActivatedRoute.queryParamMap`, owns a reactive filter form, writes normalized values back to query params, and calls `RecommendationApiService.list` on apply/reset/page changes.
- Render `loading`, `success`, `empty`, `error`, and `503` states with accessible `role=status`/`role=alert`; render server reason text and strategy labels only.
- Each result provides detail navigation and delegates watchlist actions to `WatchlistApiService`; it does not display raw signals or peer data.

- [x] **Step 1: Write failing component tests** for filter URL synchronization, loading, populated cards, strategy/reason rendering, empty cold-start guidance, validation error, `503` retry/browse fallback, and watchlist action delegation.
- [x] **Step 2: Run the targeted Vitest component tests red.**
- [x] **Step 3: Implement the standalone Material/semantic page with bounded controls, responsive card layout, retry/reset actions, and route query synchronization.**
- [x] **Step 4: Run component tests plus the full frontend unit suite and lint green.**
- [x] **Step 5: Commit** `git add frontend/src/app/features/recommendations frontend/src/app/app.component.html frontend/src/app/app.component.spec.ts && git commit -m "feat: add recommendations page"`.

### Task 5: Add cross-service and browser evidence

**Files:**
- Create/modify: `frontend/e2e/recommendations.spec.ts`
- Create: `docs/audit/batch-7-verification.md`
- Modify: `infra/compose.yml` and `infra/compose.dev.yml` to provide `NEO4FLIX_RECOMMENDATION_BASE_URL=http://recommendation-service:8084` for Movie Service.

- [x] **Step 1: Add Playwright coverage** for authenticated recommendation loading, filter URL persistence, strategy/reason rendering, empty/cold-start guidance, and recommendation-service outage fallback while `/movies` remains usable.
- [x] **Step 2: Run the focused browser test against Compose** with `npx playwright test e2e/recommendations.spec.ts --workers=1 --reporter=line`; keep credentials out of output. The two tests were safely skipped because credentials were not configured.
- [x] **Step 3: Run final verification:** full Maven reactor, focused recommendation and movie facade suites, `npm test`, `npm run lint`, `npm run build`, Compose interpolation validation, serial Playwright, and `git diff --check`. Compose health and authenticated browser execution remain environment-dependent.
- [x] **Step 4: Record exact commands, counts, endpoint/privacy evidence, facade equivalence, outage behavior, and any skipped fixture in `docs/audit/batch-7-verification.md`.
- [x] **Step 5: Self-review for Critical/Important findings, change only Batch 7 status to `[x]`, update `ACTIVE_BATCH_CONTEXT.md` to Batch 8, commit, push `main`, and verify local `HEAD` equals `origin/main`.
