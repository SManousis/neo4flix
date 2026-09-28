# Batch 5 Watchlist Vertical Slice — Design

**Date:** 2026-09-14  
**Status:** Approved in chat; implementation pending  
**Scope:** Add/list/remove the authenticated user's personal watchlist across the User Service, shared movie projections, Angular UI, and real Neo4j/browser verification.

## Goals

- Provide `GET /api/v1/users/me/watchlist`, `POST /api/v1/users/me/watchlist/{movieId}`, and `DELETE /api/v1/users/me/watchlist/{movieId}`.
- Make add idempotent and remove idempotent.
- Enforce ownership from the JWT subject; callers never submit a user ID.
- Prove concurrent same-user/movie adds leave exactly one `WATCHLISTED` relationship.
- Add an authenticated `/watchlist` page with complete loading, empty, error, remove, and movie-detail navigation states.
- Add authenticated movie-detail/catalog watchlist actions where existing movie flows expose personalized actions.
- Keep watchlist data server-authoritative and out of recommendation scoring/signals for this batch.

## Architecture

The User Service owns `WATCHLISTED` writes and reads, matching the graph access matrix. A custom Neo4j repository uses parameterized Cypher and the deterministic key `<userId>:<movieId>`; `MERGE` makes repeated adds idempotent while the existing uniqueness constraint remains authoritative under concurrency. Queries match the authenticated user and target movie explicitly, return typed movie summaries, and never expose relationship internals.

The HTTP layer validates pagination, maps missing movies to `404`, requires authentication for all watchlist endpoints, and returns idempotent success for repeated add/remove operations. The implementation does not add a separate service or cache and does not mutate `RATED` or recommendation inputs.

## Frontend behavior

Angular receives a typed watchlist API client and an authenticated `/watchlist` route. The page renders explicit loading, empty, error, and populated states; each item links to movie detail and offers a remove action with failure recovery. Movie detail and catalog cards expose an authenticated add/remove toggle; anonymous users see the existing login prompt/guard behavior. The client keeps watchlist state local to the feature and does not store tokens in browser-accessible storage.

## Verification

- DTO/controller/repository tests cover identity, pagination bounds, missing movies, idempotency, and error mapping.
- A Testcontainers test performs concurrent same-user/movie adds and asserts one relationship.
- Frontend service/component tests cover endpoint mapping, loading/empty/error/populated states, toggle success, and failure recovery.
- A disposable Playwright flow registers a user, locates an audit movie, adds it, verifies `/watchlist`, opens detail, removes it, and cleans up the account.
- Final gates include the full backend/frontend suites, lint/build, Compose smoke, `git diff --check`, and a clean push to `origin/main`.

## Explicit non-goals

- No multiple named lists, notes, priority, reminders, sharing, or recommendation weighting.
- No new microservice or cross-service write proxy.
- No implicit seed loading during startup or Compose.
