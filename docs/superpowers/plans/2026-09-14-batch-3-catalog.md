# Batch 3 Catalog, Search, Related Movies, and Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the Movie Service catalog vertical slice: public movie/genre reads, admin-only CRUD, safe combined search, related movies, and the matching Angular browse/search/detail/admin surfaces.

**Architecture:** Keep Movie Service as the sole owner of Movie and Genre graph mutations. Use parameterized `Neo4jClient` queries for filtering, allowlisted sort/pagination, aggregate reads, related-movie traversal, and referenced-genre deletion conflicts. Angular consumes the REST API through typed services and keeps admin controls behind the existing JWT role guard; backend authorization remains authoritative.

**Tech Stack:** Spring Boot 4.1, Spring Security resource server, Spring Data Neo4j/Neo4jClient, Neo4j 2026.07.1, Angular 22, Material, Vitest, Playwright.

**Spec:** `docs/reference/01_PRODUCT_SPEC.md` §§9–12, 19, 22; `docs/reference/03_GRAPH_DATABASE_SPEC.md` §§4–5, 14, 16, 20–21; `docs/reference/04_API_SPEC.md` §§21–28; `docs/reference/05_FRONTEND_SPEC.md` §§12–15, 24; `docs/reference/06_TESTING_SECURITY.md` §§7–10, 21, 33.

## Global Constraints

- Public movie/genre reads remain anonymous; all Movie/Genre mutations require `ROLE_ADMIN`.
- Search input is always a Neo4j parameter; never concatenate user input into Cypher.
- Sort fields are an explicit allowlist; unknown fields return a validation error.
- `releaseDate` is nullable and must never be synthesized from `year`.
- Movie deletion removes its `IN_GENRE` relationships and does not delete shared Genre nodes.
- Genre deletion returns conflict while referenced by any Movie.
- Pagination has bounded page size and deterministic ordering.
- Every new behavior receives a failing test before implementation and a focused green test before commit.

---

### Task 1: Movie and Genre persistence/query contracts

**Files:**
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/catalog/MovieQuery.java`
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/catalog/MovieCatalogRepository.java`
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/catalog/CatalogModels.java`
- Modify: `backend/movie-service/src/main/java/com/neo4flix/movie/persistence/MovieNode.java`
- Modify: `backend/movie-service/src/main/java/com/neo4flix/movie/persistence/GenreNode.java`
- Test: `backend/movie-service/src/test/java/com/neo4flix/movie/catalog/MovieCatalogRepositoryTest.java`

**Interfaces:**
- `MovieCatalogRepository.search(MovieQuery query): PageResult<MovieSummary>`
- `MovieCatalogRepository.findMovie(String id): MovieDetail`
- `MovieCatalogRepository.findGenres(): List<GenreSummary>`
- `MovieCatalogRepository.createMovie(MovieWrite): MovieDetail`, `updateMovie(String, MovieWrite): MovieDetail`, `deleteMovie(String): void`
- `MovieCatalogRepository.createGenre(String): GenreSummary`, `renameGenre(String, String): GenreSummary`, `deleteGenre(String): void`

- [ ] Write tests for nullable release dates, deterministic page ordering, combined title/genre/year/date/rating filters, and parameter-only Cypher.
- [ ] Run the focused test and observe failure because query/repository contracts do not exist.
- [ ] Implement immutable query/write/result records and parameterized Cypher with explicit sort-field mapping.
- [ ] Add movie/genre relationship writes and referenced-genre conflict detection without `DETACH DELETE` on shared genres.
- [ ] Run the focused repository tests and the Movie Service module tests.
- [ ] Commit `feat: add movie catalog persistence and search contracts`.

### Task 2: Movie Service REST API and authorization

**Files:**
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/catalog/MovieCatalogController.java`
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/catalog/GenreController.java`
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/catalog/CatalogExceptionHandler.java`
- Test: `backend/movie-service/src/test/java/com/neo4flix/movie/catalog/MovieCatalogControllerTest.java`

**Interfaces:**
- `GET /api/v1/movies` returns `PageResult<MovieSummary>` for anonymous callers.
- `GET /api/v1/movies/{id}` returns `MovieDetail` or 404.
- `POST|PATCH|DELETE /api/v1/movies[/{id}]` require `ROLE_ADMIN`.
- `GET /api/v1/genres` is public; `POST|PATCH|DELETE /api/v1/genres[/{id}]` require `ROLE_ADMIN`.
- `GET /api/v1/movies/{id}/related` returns related movies from shared genre overlap.

- [ ] Write MockMvc tests for anonymous reads, USER 403 mutations, ADMIN CRUD, unknown sort rejection, malformed dates, and genre-delete conflict.
- [ ] Run tests to verify the new routes fail before controllers exist.
- [ ] Implement DTO validation, Problem Details mapping, role annotations, bounded pagination, and allowlisted query parsing.
- [ ] Add related-movie endpoint with deterministic limit and exclusion of the source movie.
- [ ] Run controller tests plus `ResourceServerSecurityConfigTest` and Movie Service verification.
- [ ] Commit `feat: expose secured movie and genre catalog api`.

### Task 3: Neo4j integration and acceptance coverage

**Files:**
- Create: `backend/movie-service/src/test/java/com/neo4flix/movie/catalog/MovieCatalogNeo4jIntegrationIT.java`
- Modify: `scripts/verify.ps1`
- Test: `scripts/test-smoke-compose.ps1`

- [ ] Write Testcontainers tests for combined filters, Cypher-looking input, nullable dates, related traversal, movie deletion cleanup, and genre conflict.
- [ ] Run the integration tests and confirm the new assertions fail before wiring the repository queries.
- [ ] Implement the smallest query fixes needed for all integration assertions.
- [ ] Add the Movie Service integration class to the verification wrapper and assert no raw query text is interpolated.
- [ ] Run the full backend integration selection and Compose smoke with ephemeral auth keys.
- [ ] Commit `test: verify movie catalog graph invariants`.

### Task 4: Angular browse, search, detail, and admin surfaces

**Files:**
- Create: `frontend/src/app/core/catalog-api.service.ts`
- Create: `frontend/src/app/core/catalog.models.ts`
- Create: `frontend/src/app/features/catalog/movies.component.ts` plus `.html`, `.scss`, `.spec.ts`
- Create: `frontend/src/app/features/catalog/movie-detail.component.ts` plus `.html`, `.scss`, `.spec.ts`
- Create: `frontend/src/app/features/admin/catalog-admin.component.ts` plus `.html`, `.scss`, `.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`
- Test: `frontend/e2e/catalog.spec.ts`

- [ ] Write Vitest tests for loading/empty/error states, filter serialization, anonymous browse, admin-only controls, and accessible form validation.
- [ ] Run the focused tests and confirm failure before components/services exist.
- [ ] Implement typed API methods, public `/home`, `/movies`, `/movies/:id`, `/search`, and guarded `/admin/catalog` routes.
- [ ] Add movie cards, filter controls, detail/related sections, and admin movie/genre forms with bounded errors.
- [ ] Add Playwright coverage for anonymous browse, USER mutation denial, ADMIN CRUD, and search filter behavior.
- [ ] Run `npm test`, `npm run lint`, `npm run build`, and `npm run e2e` against the Compose stack.
- [ ] Commit `feat: add angular catalog and admin flows`.

### Task 5: Batch 3 acceptance evidence and checkpoint

**Files:**
- Modify: `docs/audit/batch-3-verification.md`
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md`

- [ ] Run the complete backend and frontend verification commands from a clean direct-main checkout.
- [ ] Run live Compose registration/login, anonymous catalog reads, USER mutation denial, ADMIN CRUD, combined filters, related reads, and deletion cleanup.
- [ ] Run Playwright catalog/admin coverage and inspect logs for secrets or raw Cypher input.
- [ ] Request independent review of the complete Batch 3 range and resolve findings before status change.
- [ ] Record exact commands/results without secrets, then change only Batch 3 status to `[x]`.
- [ ] Commit `docs: record batch 3 catalog acceptance evidence`.
