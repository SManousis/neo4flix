# Batch 3 Catalog Verification

## Current status

Batch 3 is complete. Code-level, live Compose, authenticated ADMIN browser CRUD, admin form validation/loading/race-safety, final verification, and independent review gates pass.

## Verified

- Movie Service test suite: 4 tests, 0 failures.
- Testcontainers Neo4j startup checks pass during the Movie Service suite.
- Frontend build passes.
- Frontend tests: 64 tests, 0 failures.
- Current Playwright browser contracts: default suite 3/3 passed against the healthy Compose web stack (authentication, anonymous catalog browse, and USER mutation denial); credential-gated ADMIN CRUD contract passed 1/1 with a disposable local admin fixture.
- Frontend catalog browse/detail routes and typed API client are pushed to `main`.
- Angular catalog admin entry point is guarded at `/admin/catalog`; typed movie/genre mutation methods, accessible create forms, validation/loading/race-safe edit states, and movie/genre edit/delete controls are covered by route/client assertions plus 9 focused component tests.
- Fresh Compose smoke: migrator exited 0, 11 constraints, 3 ONLINE indexes, GDS `2026.07.0`, four services healthy, and web reachable.
- Live catalog probes: `GET /api/v1/movies` 200, `GET /api/v1/genres` 200, anonymous `POST /api/v1/movies` 401.
- Live non-empty catalog probes with disposable Neo4j fixtures: collection/detail/related reads returned 200; combined title, genre, year, sort, and direction filters returned the expected two rows; fixtures were removed after verification.
- Repository regression fixes verified by focused `MovieCatalogRepositoryTest`: 2 tests, 0 failures; search row mapping now uses typed Neo4j mapping and scalar bindings use value-then-parameter order.
- Unknown sort validation is now enforced: focused catalog query tests are 3/3 green and live `GET /api/v1/movies?sort=drop%20table` returns 400.
- Live authenticated mutation probes with disposable accounts: USER movie mutation returned 403; ADMIN movie create/update/delete returned 201/200/204; referenced genre deletion returned 409, then 204 after movie cleanup; deleted movie lookup returned 404. Disposable users and fixtures were removed afterward.
- Final repository verification: `scripts/verify.ps1` passed with Maven BUILD SUCCESS, selected integration suites green, frontend 14 files / 64 tests green, lint/build green, and Compose config valid.
- Final Compose/browser gate: `scripts/smoke-compose.ps1 -EnvFile .env` passed; default Playwright suite passed 3/3 with the credential-gated ADMIN CRUD contract passing 1/1 against the rebuilt web image.
- Independent review: no Critical or Important issues remain after request-id invalidation for cancelled/superseded movie detail loads; `git diff --check` is clean. Non-blocking minor: load failures retain the global error status while the list area uses its empty-state copy.

## Pending live gate

Final clean-checkout-equivalent verification and review passed; Batch 3 status is now complete in `docs/reference/00_MASTER_EXECUTION_PLAN.md`.

## Commits

- `ec15c47`, `49d4d27`, `a7737c3`, `03c5617`, `07694c1`, `97c2a7f`, `a2a59b2`, `3310206`, `5478226`, `194562c`, `9fcc551`, `ac17b83`, `d4ffb3a`, `414822c`, `92e0c80`, `4dee1d6`, `228c9f9`, `b27b657`
