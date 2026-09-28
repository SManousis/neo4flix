# Batch 7 Recommendation API, Filters, UI, and Movie Facade Design

## Status

Approved design for Batch 7. This document extends the completed Batch 6 recommendation core without changing its scoring or graph-read contracts.

## Goal

Expose the deterministic recommendation engine through the public API, provide an equivalent Movie Service facade, and deliver an authenticated Angular recommendations page with bounded filters and explicit loading, empty, error, and cold-start states.

## Scope

Batch 7 owns:

- `GET /api/v1/recommendations/me` in Recommendation Service.
- `GET /api/v1/movies/recommended` in Movie Service as a forwarding facade.
- Typed Angular recommendation models/client and the guarded `/recommendations` route/page.
- Filter, sort, and page validation at the API boundary.
- Bearer and `X-Request-Id` propagation through the Movie facade.
- Controlled `503` behavior when Recommendation Service is unavailable.
- Contract, equivalence, failure, and browser-state tests.

Batch 7 does not implement RecommendationShare CRUD, public share pages, new ranking algorithms, or watchlist signal changes.

## Public Recommendation API

### Request

`GET /api/v1/recommendations/me` requires a validated bearer token. Identity comes only from the JWT `sub`; there is no `userId` query parameter.

Supported query parameters:

| Parameter | Type | Default | Bounds/meaning |
| --- | --- | --- | --- |
| `genre` | string | absent | trimmed, bounded to 80 characters |
| `fromYear` | integer | absent | 1888–2200 when present |
| `toYear` | integer | absent | 1888–2200; must be ≥ `fromYear` |
| `minimumAverageRating` | decimal | absent | 0–5 inclusive |
| `sort` | enum | `recommendation` | `recommendation`, `rating`, `newest`; unknown values return 400 |
| `page` | integer | 0 | 0–10000 |
| `size` | integer | 20 | 1–50 |

The controller converts these validated values into the existing `RecommendationDtos.Query`; strategy selection and ranking remain in `RecommendationApplicationService`.

### Response

Success is `200 application/json`:

```json
{
  "items": [
    {
      "movie": {
        "id": "uuid",
        "title": "Blade Runner",
        "overview": null,
        "releaseYear": 1982,
        "releaseDate": null,
        "posterUrl": null,
        "genres": [{"id": "genre-uuid", "name": "Science Fiction"}],
        "averageRating": 4.2,
        "ratingCount": 10
      },
      "recommendationScore": 0.87,
      "signals": {
        "collaborative": 0.91,
        "content": 0.76,
        "popularity": 0.74
      },
      "strategy": "HYBRID",
      "reason": {
        "type": "SIMILAR_USERS",
        "text": "Users with similar ratings also liked this movie."
      }
    }
  ],
  "strategy": "HYBRID",
  "page": 0,
  "size": 20,
  "totalItems": 42,
  "totalPages": 3
}
```

Canonical reason types are `SIMILAR_USERS`, `GENRE_MATCH`, and `POPULAR`. The server supplies the reason text; clients do not infer algorithm claims. The response never contains peer IDs, private vectors, watchlist relationships, or user rating history.

The API layer may sort the already-scored bounded result by recommendation score, average rating, or release year. It must preserve deterministic movie-ID tie-breaking and apply page slicing after sorting. An empty result is a normal `200` response with `items: []`.

Invalid parameters use the shared Problem Details contract with `400`; missing/invalid authentication uses `401`. No raw Cypher, stack traces, or downstream credentials are returned.

## Movie Service facade

`GET /api/v1/movies/recommended` requires authentication and accepts the same recommendation query parameters. Movie Service:

1. Reads the authenticated request's bearer header and current request ID.
2. Forwards the allowlisted query parameters to Recommendation Service at a configured internal URL.
3. Sends `Authorization: Bearer <same-token>` and `X-Request-Id: <same-id>` without logging token values.
4. Returns the recommendation response body/status unchanged for successful and validation responses.
5. Converts connection timeout, connection refusal, and downstream 5xx into a stable `503` Problem Details response with code `RECOMMENDATION_SERVICE_UNAVAILABLE`.

Movie browsing, detail, genres, related movies, and admin catalog operations do not depend on this facade and remain available when Recommendation Service is unavailable.

## Angular experience

Add a functional `authGuard` route at `/recommendations`, a typed `RecommendationApiService`, and a standalone `RecommendationsComponent`.

The page owns filters in reactive form state synchronized to URL query parameters:

- genre text/select, from/to year, minimum rating, sort, and page;
- apply/reset controls with bounded client validation;
- server-authoritative data, with no local ranking or fabricated explanations.

Each card shows the reusable movie summary fields, score, strategy, canonical reason, detail link, and authenticated watchlist action. It does not show similar-user identities or raw signal vectors. The page visibly handles:

- loading (`role=status`),
- successful results,
- empty results with strategy-specific guidance (`POPULARITY`: invite the user to rate movies; `CONTENT_PLUS_POPULARITY`/`HYBRID`: explain that more ratings improve personalization),
- validation/authorization errors,
- downstream `503` with retry and a message that browsing movies still works.

The existing auth interceptor continues to attach/refresh bearer tokens. The recommendation client sends ordinary same-origin API requests so the existing `X-Request-Id` response behavior remains intact.

## Backend design units

- Recommendation controller/request DTOs: validate query parameters, derive JWT subject, map core results to API DTOs.
- Recommendation response mapper: expose movie summary, bounded signals, strategy, and typed reason without leaking private data.
- Movie recommendation client/facade: one outbound HTTP boundary with configurable base URL, bounded timeout, header propagation, and typed downstream error mapping.
- Movie controller endpoint: thin facade only; existing `MovieCatalogRepository` remains independent.

## Verification strategy

- Red/green controller tests for identity derivation, defaults, bounds, sort allowlist, page slicing, response mapping, and Problem Details.
- Recommendation Service integration/context tests proving real route authorization and no `userId` override.
- Movie facade tests using a mock HTTP server/client for bearer/request-ID propagation, equivalent query forwarding, success passthrough, and controlled `503`.
- Frontend service/component tests for URL-backed filters, loading/empty/error/503 states, canonical reason rendering, and route guard.
- Existing full Maven, frontend, Compose, and serial Playwright suites remain required before Batch 7 completion.

## Non-negotiable constraints

- Do not alter Batch 6 scoring, GDS, candidate exclusion, or privacy rules.
- Never accept a request-supplied user ID for personalized recommendations.
- Never interpolate request text, sort values, or filters into Cypher.
- Never log or return bearer tokens, private rating history, vectors, peer identities, or watchlist relationships.
- Keep the Movie facade failure isolated from ordinary Movie Service browsing.
