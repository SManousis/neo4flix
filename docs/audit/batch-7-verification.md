# Batch 7 Verification — Recommendation API, Filters, UI, and Movie Facade

Date: 2026-09-15  
Branch: `main`

## Automated verification

| Area | Command | Result |
| --- | --- | --- |
| Recommendation API and core | `mvn -B -pl backend/recommendation-service -am -Dtest=RecommendationControllerTest,RecommendationRepositoryTest,RecommendationApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 14 tests passed |
| Movie facade focused | `mvn -B -pl backend/movie-service -am -Dtest=MovieRecommendationClientTest,MovieRecommendationControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 5 tests passed |
| Movie/platform regression | `mvn -B -pl backend/movie-service -am test` | 22 tests passed (12 platform-common, 10 movie-service) |
| Full Maven reactor | `mvn -B test` | 114 tests passed, 0 failures, 0 errors |
| Frontend unit suite | `npm test -- --watch=false` from `frontend` | 84 tests passed |
| Frontend lint | `npm run lint` from `frontend` | Passed with `--max-warnings=0` |
| Frontend production build | `npm run build` from `frontend` | Passed; recommendation page emitted as a lazy chunk |
| Browser contract listing | `npx playwright test e2e/recommendations.spec.ts --list` | 2 tests listed |
| Browser contract execution | `npx playwright test e2e/recommendations.spec.ts --workers=1 --reporter=line` | 2 skipped because E2E credentials were not configured |
| Compose interpolation | `docker compose -f infra/compose.yml -f infra/compose.dev.yml config --quiet` with command-scoped validation placeholders | Passed; no `.env` changes |
| Diff hygiene | `git diff --check` | Passed |

## Evidence covered

- Recommendation Service exposes the authenticated `/api/v1/recommendations/me` API with bounded filters, deterministic paging, typed reasons, and privacy-safe movie summaries.
- Malformed numeric query values are parsed explicitly and become the shared validation-error path rather than framework binding failures.
- Movie Service forwards only the recommendation filter allowlist plus bearer and generated/request-provided request IDs; downstream 4xx responses pass through and outages become shared-contract 503 responses.
- `/api/v1/movies/recommended` is authenticated before the public movie GET matcher; the anonymous regression test returns 401.
- The Angular recommendation client preserves server ranking and reason text, supports all backend strategies (`HYBRID`, `CONTENT_PLUS_POPULARITY`, `POPULARITY`), and the page renders bounded movie summaries, URL-backed filters, loading, empty/cold-start, validation, outage, retry, browse fallback, details, and watchlist-add states without raw signal vectors.
- Compose config points Movie Service to `http://recommendation-service:8084` in both base and development overlays.

## Deferred environment-dependent evidence

The local `.env` contains blank required Neo4j/JWT values. It was not modified or printed. A full Compose health run and authenticated Playwright pass require the user's real environment values and credentials; the browser tests are intentionally skipped until those are supplied.
