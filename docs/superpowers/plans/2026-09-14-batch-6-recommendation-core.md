# Batch 6 Recommendation Engine Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic Recommendation Service core that uses real Neo4j/GDS collaborative similarity, content and popularity signals, bounded candidates, cold-start strategies, exclusions, and golden-fixture evidence.

**Architecture:** A custom `Neo4jClient` repository performs parameterized graph reads and a genuine `gds.similarity.cosine` call. A pure scoring service validates weights, combines normalized signal rows, selects a strategy, and emits privacy-safe results; Batch 7 will expose the public HTTP/UI contract.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data Neo4j `Neo4jClient`, Neo4j GDS 2026.07, Testcontainers, JUnit 5, AssertJ, Jackson fixture loading.

**Spec:** `docs/superpowers/specs/2026-09-14-batch-6-recommendation-core-design.md`

## Global Constraints

- GDS must genuinely execute `gds.similarity.cosine` over deterministically aligned rating vectors.
- Default weights are collaborative `0.60`, content `0.30`, popularity `0.10`; configured weights must sum to `1.0` within the declared tolerance.
- Rating preference mapping is `1→-1.0`, `2→-0.5`, `3→0.0`, `4→+0.5`, `5→+1.0`.
- Scores and final ranking values are clamped to `[0,1]`.
- Zero ratings use `POPULARITY`; one or two ratings use `CONTENT_PLUS_POPULARITY`; at least three ratings plus a qualifying peer use `HYBRID`.
- Every candidate excludes all movies already rated by the requesting user; watchlisted movies remain eligible.
- User IDs, limits, thresholds, and filters are parameters or validated allowlisted values; no dynamic Cypher interpolation.
- Peer identities, private vectors, and watchlist data never appear in recommendation results.
- Work directly on `main`; do not create worktrees or redispatch Batches 0–5.

---

### Task 1: Define recommendation contracts, configuration, and pure scoring

**Files:**
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationDtos.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationWeights.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationScoringService.java`
- Test: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/core/RecommendationWeightsTest.java`
- Test: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/core/RecommendationScoringServiceTest.java`

**Interfaces:**
- `RecommendationDtos.Strategy` enum values: `POPULARITY`, `CONTENT_PLUS_POPULARITY`, `HYBRID`.
- `RecommendationDtos.Query(String userId, int limit, int minimumOverlap, int peerLimit, int candidateLimit, String genre, Integer fromYear, Integer toYear, Double minimumAverageRating)`.
- `RecommendationDtos.SignalRow(String movieId, String title, String overview, Integer releaseYear, String posterUrl, List<String> genres, double collaborativeScore, double contentScore, double popularityScore, int peerCount, int commonMovies, double averageRating, long ratingCount)`.
- `RecommendationDtos.Result` mirrors the movie summary and adds `double score`, `Strategy strategy`, and `String reason`.
- `RecommendationWeights(double collaborative, double content, double popularity, double popularityPriorCount)` validates non-negative weights, a positive prior count, and a sum within `1e-9` of `1.0`.
- `RecommendationScoringService.selectStrategy(int ratingCount, boolean qualifyingPeer)`, `preferenceFor(int score)`, `popularityScore(double averageRating, long ratingCount, double priorCount)`, and `score(Strategy, SignalRow, RecommendationWeights)` are pure methods.

- [x] **Step 1: Write failing tests** for invalid weight sums/negatives, all three strategy boundaries, `preferenceFor` mapping, popularity confidence, final score clamping, and reason selection.
- [x] **Step 2: Run the focused tests red** with `./mvnw.cmd -pl backend/recommendation-service -am -Dtest=RecommendationWeightsTest,RecommendationScoringServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`; confirm failures are missing contracts/services.
- [x] **Step 3: Implement the records, validation, signal normalization, strategy selection, score formula, clamp helper, and exact canonical reason strings.** Keep the service independent of Spring and Neo4j.
- [x] **Step 4: Run the focused tests green** with the same Maven command; verify helper and score outputs are within `[0,1]`.
- [x] **Step 5: Commit** `git add backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core backend/recommendation-service/src/test/java/com/neo4flix/recommendation/core && git commit -m "feat: define recommendation scoring contracts"`.

### Task 2: Add the parameterized Neo4j/GDS repository boundary

**Files:**
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence/RecommendationRepository.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence/RecommendationNeo4jRepository.java`
- Test: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/persistence/RecommendationRepositoryTest.java`

**Interfaces:**
- `RecommendationRepository.snapshot(RecommendationDtos.Query query): RecommendationRepository.Snapshot`.
- `Snapshot` contains `int ratingCount`, `boolean qualifyingPeer`, and `List<RecommendationDtos.SignalRow> rows`; it exposes no peer IDs or vectors.
- The implementation receives `Neo4jClient` through its constructor and uses separate constant Cypher statements for profile count, GDS peer similarity, genre preferences, and bounded candidate rows.

- [x] **Step 1: Write failing repository tests** asserting every query contains `$userId`, `$minimumOverlap`, `$peerLimit`, `$candidateLimit`, `$skip`/`$limit` equivalents where applicable, calls `gds.similarity.cosine`, excludes `alreadyRated` movies, and maps nullable movie fields safely.
- [x] **Step 2: Run the focused repository tests red** with `./mvnw.cmd -pl backend/recommendation-service -am -Dtest=RecommendationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [x] **Step 3: Implement the repository.** Align each peer's score vector by `movie.id` ordering before calling `gds.similarity.cosine`; require `commonMovies >= $minimumOverlap`; bound peers and candidates; compute popularity as normalized average multiplied by `count/(count+priorCount)`; aggregate mapped genre preferences; apply genre/year/min-average filters through parameters; order final rows by score inputs and movie ID, never by interpolated request text.
- [x] **Step 4: Run repository tests green** and add mapper assertions for missing overview/poster/year and deterministic empty results.
- [x] **Step 5: Commit** `git add backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence backend/recommendation-service/src/test/java/com/neo4flix/recommendation/persistence && git commit -m "feat: add recommendation neo4j gds repository"`.

### Task 3: Wire the recommendation application service and configuration

**Files:**
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationConfiguration.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationApplicationService.java`
- Modify: `backend/recommendation-service/src/main/resources/application.yml`
- Modify: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/RecommendationServiceApplication.java`
- Test: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/core/RecommendationApplicationServiceTest.java`

**Interfaces:**
- `RecommendationConfiguration` is a validated `@ConfigurationProperties(prefix = "neo4flix.recommendation")` record with defaults for weights, prior count, minimum overlap `2`, peer limit `10`, candidate limit `50`, mature threshold `3`, and max page size `50`.
- `RecommendationApplicationService.recommend(RecommendationDtos.Query query): List<RecommendationDtos.Result>` validates/bounds the query, calls `RecommendationRepository.snapshot`, selects the strategy, scores rows, assigns only evidence-backed reasons, sorts score descending then movie ID ascending, and returns at most the configured limit.

- [x] **Step 1: Write failing service tests** for default config, invalid config startup validation, query limit clamping/rejection, strategy delegation, deterministic ranking, and no peer identity leakage.
- [x] **Step 2: Run the focused service tests red** with `./mvnw.cmd -pl backend/recommendation-service -am -Dtest=RecommendationApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [x] **Step 3: Implement configuration binding, application orchestration, bounded query normalization, scoring delegation, evidence-backed reason selection, and deterministic sorting (score descending, movie ID ascending).** Do not add an HTTP controller in this task.
- [x] **Step 4: Run the focused service tests green**, then run the complete Recommendation Service unit suite.
- [x] **Step 5: Commit** `git add backend/recommendation-service/src/main backend/recommendation-service/src/test/java/com/neo4flix/recommendation/core backend/recommendation-service/src/main/resources/application.yml && git commit -m "feat: add recommendation application service"`.

### Task 4: Expand the deterministic audit fixture and prove golden behavior in Neo4j/GDS

**Files:**
- Modify: `database/seeds/audit/audit-fixture.json`
- Modify: `database/migrator/src/test/java/com/neo4flix/migrator/seed/AuditSeedLoaderIT.java`
- Modify: `backend/recommendation-service/pom.xml`
- Create: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/RecommendationGoldenFixtureIT.java`

**Interfaces:**
- The fixture keeps the existing Alice/Bob/Carol audit IDs and adds sparse/fresh users plus enough action, romance/drama, sci-fi, mixed, and candidate movies to prove every strategy without changing production seed behavior.
- `RecommendationGoldenFixtureIT` starts `Neo4jGdsContainer`, applies migrations, loads the audit JSON through the existing loader, constructs the repository/service with the container driver, and calls `recommend` directly.

- [x] **Step 1: Add failing golden tests** for Alice/Bob similarity ordering, Bob-only candidate discovery, already-rated exclusion, low-rated genre negativity, zero/sparse/mature strategy selection, filters, canonical reasons, score bounds, and deterministic repeated ranking.
- [x] **Step 2: Run the focused Testcontainers test red** with `$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.1'; ./mvnw.cmd -pl backend/recommendation-service,database/migrator -am -Dtest=RecommendationGoldenFixtureIT -Dsurefire.failIfNoSpecifiedTests=false test`; confirm failures identify missing recommendation behavior or insufficient fixture data.
- [x] **Step 3: Extend only the audit JSON data needed by the failing assertions.** Preserve existing IDs and make every new ID deterministic; do not add watchlist relationships or recommendation-specific production properties.
- [x] **Step 4: Run the golden test green** and assert the query path executes `gds.similarity.cosine` by checking its returned collaborative signal and a direct GDS smoke query against the same container.
- [x] **Step 5: Commit** `git add database/seeds/audit/audit-fixture.json database/migrator/src/test/java/com/neo4flix/migrator/seed/AuditSeedLoaderIT.java backend/recommendation-service/pom.xml backend/recommendation-service/src/test/java/com/neo4flix/recommendation/RecommendationGoldenFixtureIT.java && git commit -m "test: prove recommendation golden fixture"`.

### Task 5: Capture query plans and complete Batch 6 audit evidence

**Files:**
- Create: `docs/audit/batch-6-verification.md`
- Create: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/RecommendationQueryPlanIT.java`
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md` only after every gate passes.
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md` only after the final commit hash is known.

- [x] **Step 1: Add a focused query-plan test** using package-visible repository statements and the Neo4j driver to run `EXPLAIN` for the peer/candidate statements and `PROFILE` once against the deterministic fixture, asserting successful execution and recording only operator names, row counts, and bounded-plan observations.
- [x] **Step 2: Run the query-plan test green** with the focused integration command and ensure no secrets or full user vectors are written to output.
- [x] **Step 3: Run final verification:** full Maven reactor tests, full Recommendation Service/GDS integration selection, frontend regression suite, `npm run lint`, `npm run build`, Compose rebuild/smoke, `git diff --check`, and the existing serial Playwright suite.
- [x] **Step 4: Record exact commands, test counts, GDS evidence, fixture cleanup, query-plan notes, and any non-blocking review findings** in `docs/audit/batch-6-verification.md`.
- [x] **Step 5: Self-review for Critical/Important findings, change only Batch 6 status to `[x]`, update active context to Batch 7, commit, push `main`, and verify local `HEAD` equals `origin/main`.
