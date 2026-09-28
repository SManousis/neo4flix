# Batch 6 Recommendation Engine Core Verification

Date: 2026-09-14
Branch: `main`

## Scope

This audit covers the recommendation core only. The implementation owns deterministic scoring, bounded parameterized Neo4j/GDS reads, cold-start strategy selection, exclusions, filters, canonical reasons, and the deterministic audit fixture. The public HTTP/UI contract remains Batch 7.

## Focused evidence

All commands use Java 26.0.1 and the repository's Maven build. Testcontainers runs Neo4j `2026.07.1-community` with the Graph Data Science plugin.

| Gate | Command | Result |
| --- | --- | --- |
| Pure scoring contracts | `./mvnw.cmd -pl backend/recommendation-service -am -Dtest=RecommendationWeightsTest,RecommendationScoringServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS — 8 tests |
| Repository contract | `./mvnw.cmd -pl backend/recommendation-service -am -Dtest=RecommendationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS — 4 tests |
| Application orchestration | `./mvnw.cmd -pl backend/recommendation-service -am -Dtest=RecommendationApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS — 4 tests |
| Golden Neo4j/GDS fixture | `./mvnw.cmd -pl backend/recommendation-service,database/migrator -am -Dtest=RecommendationGoldenFixtureIT -Dsurefire.failIfNoSpecifiedTests=false test` | PASS — 2 tests |
| Query plans | `./mvnw.cmd -pl backend/recommendation-service,database/migrator -am -Dtest=RecommendationQueryPlanIT -Dsurefire.failIfNoSpecifiedTests=false test` | PASS — 1 test |

The golden fixture proves Alice's hybrid ranking, fresh-user popularity, sparse-user content-plus-popularity, genre filtering, already-rated exclusion, deterministic repeated ranking, bounded scores, the negative-affinity guard, and a direct `gds.similarity.cosine` smoke call. The query-plan test executes `EXPLAIN` for collaborative and candidate statements and `PROFILE` for the bounded candidate statement. It records only normalized operator names and non-negative summary counters; no peer IDs, vectors, tokens, or private watchlist data are emitted.

## Data and safety notes

- Audit seed data remains deterministic and isolated to `database/seeds/audit/audit-fixture.json`; production seed behavior is unchanged.
- All user, filter, peer, candidate, and rating-threshold inputs remain Cypher parameters. No request text is interpolated into queries.
- Existing rated movies are excluded with a relationship predicate; watchlist relationships are not read by this core.
- Nullable movie metadata is mapped safely to `null`, and recommendation results expose no peer identities or rating vectors.
- Neo4j emits non-blocking warnings for optional `overview`/`posterUrl` properties absent from the minimal audit fixture; this is expected fixture sparsity, not a query failure.

## Final verification record

| Gate | Command | Result |
| --- | --- | --- |
| Full Maven reactor | `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-26.0.1'; & 'C:\\Users\\User\\.m2\\wrapper\\dists\\apache-maven-3.9.11\\d6d3cbd4012d4c1d840e93277aca316c\\bin\\mvn.cmd' -B test` | PASS — 123 tests, 0 failures, 0 errors |
| Frontend unit/regression | `npm test` (from `frontend`) | PASS — 75 tests, plus Docker-node compatibility check |
| Frontend lint | `npm run lint` (from `frontend`) | PASS — zero warnings/errors |
| Frontend production build | `npm run build` (from `frontend`) | PASS — Angular production bundle generated |
| Compose rebuild/smoke | `docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up -d --build` followed by `docker compose ... ps` | PASS — all six services healthy; migrator exited successfully |
| Browser regression | `npx playwright test --workers=1 --reporter=line` (from `frontend`) | PASS — 5 passed, 1 existing admin test skipped by its fixture |
| Diff hygiene | `git diff --check` | PASS |

The full reactor includes the recommendation unit/application/persistence suite. The focused golden fixture and query-plan gates above remain the evidence for real Neo4j/GDS behavior and bounded `EXPLAIN`/`PROFILE` execution. The repository now injects the configured popularity prior, preserves negative genre affinity through a bounded `[0,1]` normalization, and computes qualifying-peer maturity independently from candidate availability. Compose image builds use an installed shared platform test JAR so production image compilation can skip test execution without losing reactor-resolvable test fixtures.

No secrets, peer identities, vectors, or watchlist data were emitted by the query-plan or golden tests. The only expected runtime note is Neo4j's non-blocking warning for nullable `overview`/`posterUrl` fields absent from the intentionally sparse audit fixture.
