# Batch 5 verification — watchlist vertical slice

Date: 2026-09-14  
Branch: `main` (direct-main execution; no worktree)  
Scope: Authenticated watchlist add/list/remove, Neo4j idempotency and isolation, Angular watchlist page, and movie-detail actions.

## Implementation checkpoints

- `db8857a` — watchlist DTOs, repository boundary, Cypher idempotency, and application service.
- `4073178` — authenticated watchlist HTTP API and Problem Details mapping.
- `64646ed` — real Neo4j concurrent-add and ownership/isolation integration coverage.
- `f3c989b` — typed Angular client, guarded `/watchlist` page, and loading/empty/error/populated states.
- `373b492` — movie-detail add/remove action and anonymous login prompt.
- Final checkpoint — browser contract, API-shape correction (`title` projection), audit, and Batch 5 ledger update.

## Automated evidence

- `./scripts/verify.ps1 -TestOnly` with `JAVA_HOME=C:\Program Files\Java\jdk-26.0.1`: full Maven reactor passed — platform-common 12, user-service 39, movie-service 5, rating-service 11, recommendation-service 3, and database-migrator 15 tests (85 total). Its integration selection also passed: platform-common 2, user-service 17, and rating-service 1 tests (20 total), including `WatchlistConcurrencyIT`.
- Focused watchlist concurrency command passed with one Testcontainers test and exactly one `WATCHLISTED` relationship after concurrent adds.
- Frontend unit suite: 19 files, 75 tests passed.
- `npm run lint`: passed with zero warnings.
- `npm run build`: Angular production build passed.
- `docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up -d --build`: all six application images rebuilt and services recreated; no secret values were recorded.
- `./scripts/smoke-compose.ps1 -EnvFile .env`: passed. Migrator exited 0, six migrations reached latest, 11 constraints and 3 online indexes verified, GDS `2026.07.0` ready, all four backend services and web reachable.
- `npx playwright test --workers=1 --reporter=line`: 5 passed, 1 skipped. The skipped test is the existing admin catalog contract because `NEO4FLIX_E2E_ADMIN_EMAIL`/`NEO4FLIX_E2E_ADMIN_PASSWORD` were not supplied. The watchlist flow passed against the audit fixture and verified add/list/detail/remove with POST/GET/DELETE statuses 201/200/204 and visible states.
- `git diff --check`: passed before the checkpoint commit.

## Fixture and cleanup

The running Compose stack used the explicit audit seed data, including a deterministic movie. The browser contract registered a disposable user, authenticated through the API for a browser-context bootstrap (the existing UI login contract is covered by `auth.spec.ts`), added the movie, verified the watchlist list/detail path, removed it, and deleted the disposable account in `finally`. The refresh bootstrap is locally fulfilled from that real login response to keep the test independent of the shared per-IP refresh throttle while all watchlist requests use the real JWT. `.env` was used only as local command input and was not read into this document or committed.

## Review notes

The final browser run exposed a shared refresh-rate-limit interaction when the watchlist test followed the other authenticated flows. The test now isolates only the app bootstrap refresh response from the real disposable login; no production authentication or watchlist path is mocked. The backend/frontend projection contract was also aligned on the planned `title` field. No Critical or Important findings remain for this batch, and watchlist data is not connected to recommendation signals.
