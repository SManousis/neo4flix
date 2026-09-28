# Batch 4 Rating and Rating-History Design

**Status:** Approved design; implementation pending plan review.

## Goal

Deliver the Rating Service CRUD and user rating-history vertical slice while preserving `RATED` ownership, strict create/update semantics, graph uniqueness, and the existing Angular catalog experience.

## Binding contracts

- Rating Service is the sole writer of `RATED` relationships.
- `RATED.key` is deterministic `<userId>:<movieId>` and uniqueness is enforced by the existing Neo4j constraint.
- Scores are integers from 1 through 5.
- `POST /api/v1/ratings` is create-only: an existing rating returns `409 RATING_ALREADY_EXISTS` and is never silently upserted.
- `GET /api/v1/ratings/{movieId}` reads only the authenticated user's rating and returns `404` when absent.
- `PUT /api/v1/ratings/{movieId}` updates an existing rating and returns `404` when absent.
- `DELETE /api/v1/ratings/{movieId}` is idempotent and returns `204` without touching another user's rating.
- `GET /api/v1/ratings/me` is an authenticated pageable history endpoint.
- `GET /api/v1/ratings/movies/{movieId}/summary` is public; zero ratings are represented consistently as `averageRating: null` and `ratingCount: 0`.
- `GET /api/v1/users/me/ratings` is a read-only User Service facade over the current user's rating history and must never mutate `RATED`.

## Architecture

### Rating Service

Use a custom Neo4j repository for rating mutations and reads. Every query binds the JWT subject and movie ID as parameters. Create matches the authenticated `User` and target `Movie`, creates one `RATED` relationship with key, score, and UTC timestamps, and translates the relationship-constraint violation into the documented conflict response. Update matches the same user's relationship; delete removes only that relationship. History and summary queries return typed DTOs rather than exposing internal relationship keys.

Controller validation rejects malformed scores before database access. The existing shared resource-server security configuration supplies the authenticated JWT subject and request-ID/problem-details behavior.

### User Service facade

Add a small typed Rating Service client in User Service. The facade controller authenticates locally, forwards the bearer token and request ID to Rating Service, and maps the downstream pageable response to the public user-centric contract. It is read-only and returns a controlled service-unavailable response when Rating Service cannot answer.

### Frontend

Add a typed rating API client. Extend movie detail with current-user rating state and accessible keyboard/screen-reader rating controls. Add `/movies/:id/rate` for focused create/update/delete flows and add rating history to the authenticated profile view. Anonymous users see the existing login prompt for personalized actions; loading, empty, error, and mutation-success states remain explicit.

## Verification strategy

- Unit tests cover score validation, DTO mapping, conflict/error mapping, and idempotent delete behavior.
- Controller tests cover anonymous access, ownership, duplicate create, missing update, malformed score, and public zero-rating summaries.
- Testcontainers Neo4j tests prove real `RATED.key` uniqueness under concurrent same-user/movie creates and verify aggregate/history queries.
- User Service tests prove the facade forwards identity/request metadata, stays read-only, and handles downstream failures.
- Angular tests cover rating API calls, accessible controls, route states, and profile history.
- Playwright covers authenticated rating create/update/delete from movie detail and history visibility.

## Explicit non-goals

- No recommendation scoring changes; Batch 6 owns recommendation algorithms.
- No watchlist mutations; Batch 5 owns watchlist behavior.
- No mutable cached movie aggregates; summaries remain derived from `RATED` relationships.
- No new Neo4j migration unless verification proves the existing `RATED.key` constraint is missing.
