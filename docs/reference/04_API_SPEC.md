# Neo4flix — HTTP API Specification

> **Authority:** This document owns external HTTP paths, methods, request/response behavior, authentication/authorization semantics, pagination/filter parameters, service facade behavior, status codes, and public error shape.

## 1. Base Path

All application APIs live under:

```text
/api/v1
```

Nginx routes to the owning service while preserving the single public origin.

## 2. Media Types

Normal JSON: `application/json`.

Errors: `application/problem+json`.

Timestamps: ISO-8601 UTC (`...Z`).

Movie exact dates: `YYYY-MM-DD`.

## 3. Authentication Header

Protected requests use:

```http
Authorization: Bearer <access-jwt>
```

User identity for personalized operations comes from JWT `sub`, never a request-supplied `userId`.

## 4. JWT Claims

Required semantics:

```json
{
  "sub": "user-uuid",
  "iss": "neo4flix-user-service",
  "aud": "neo4flix-api",
  "roles": ["USER"],
  "iat": 1788770000,
  "exp": 1788770900,
  "jti": "token-uuid"
}
```

Protected services validate signature, issuer, audience, and expiry.

## 5. Error Shape

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/ratings",
  "code": "VALIDATION_FAILED",
  "traceId": "request-id",
  "fieldErrors": {
    "score": "must be between 1 and 5"
  }
}
```

No stack trace, Cypher, credentials, or internal file paths.

## 6. Common Status Semantics

| Status | Meaning |
|---:|---|
| 200 | successful read/update |
| 201 | resource created |
| 202 | accepted; additional authentication step required |
| 204 | success without response body |
| 400 | malformed/validation failure |
| 401 | unauthenticated/invalid token |
| 403 | authenticated but unauthorized |
| 404 | resource unavailable/not found |
| 409 | resource/business conflict |
| 422 | semantically invalid where specifically documented |
| 429 | rate limited |
| 500 | unexpected server failure |
| 503 | required downstream service unavailable |

## 7. Pagination

Paged response:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

Defaults:

- `page=0`
- `size=20`
- `size` range 1–100

Unknown/invalid sort values are rejected; never interpolated into Cypher.

# Authentication — User Service

## 8. Register

`POST /api/v1/auth/register` — public.

Request:

```json
{
  "email": "alice@example.com",
  "displayName": "Alice",
  "password": "StrongPassword1!"
}
```

Success `201`:

```json
{
  "id": "uuid",
  "email": "alice@example.com",
  "displayName": "Alice",
  "role": "USER",
  "twoFactorEnabled": false,
  "createdAt": "2026-09-07T10:00:00Z"
}
```

Registration does not implicitly log the user in. Duplicate email: `409`.

## 9. Login

`POST /api/v1/auth/login` — public/rate-limited.

```json
{
  "email": "alice@example.com",
  "password": "StrongPassword1!"
}
```

### Without active 2FA

`200` + refresh cookie:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "alice@example.com",
    "displayName": "Alice",
    "role": "USER",
    "twoFactorEnabled": false
  }
}
```

### With active 2FA

No normal access token/session yet.

`202`:

```json
{
  "requiresTwoFactor": true,
  "challengeToken": "<opaque-one-time-token>",
  "expiresIn": 300
}
```

Invalid credentials use a generic authentication error.

## 10. Verify Login 2FA

`POST /api/v1/auth/2fa/verify`.

```json
{
  "challengeToken": "...",
  "code": "428193"
}
```

Success issues normal access/refresh session. Expired/used/wrong challenge/code fails.

## 11. Refresh

`POST /api/v1/auth/refresh` — HttpOnly refresh cookie.

- validate session/hash
- revoke/rotate old
- issue new refresh cookie
- return new access JWT

Old token fails after successful rotation.

## 12. Logout

`POST /api/v1/auth/logout`.

Revoke active refresh session and clear cookie. User-facing result `204`.

## 13. Current Identity

`GET /api/v1/auth/me` — authenticated.

Returns current public profile/auth state only.

## 14. TOTP Setup

`POST /api/v1/auth/2fa/setup` — authenticated.

Creates/replaces short-lived pending enrollment.

```json
{
  "otpauthUri": "otpauth://totp/...",
  "qrCodeDataUrl": "data:image/png;base64,...",
  "expiresAt": "..."
}
```

## 15. TOTP Confirm

`POST /api/v1/auth/2fa/confirm`.

```json
{ "code": "428193" }
```

Validates pending state and activates 2FA.

## 16. Disable TOTP

`POST /api/v1/auth/2fa/disable`.

```json
{
  "password": "StrongPassword1!",
  "code": "428193"
}
```

Requires strong reauthentication.

## 17. Change Password

`POST /api/v1/auth/change-password`.

```json
{
  "currentPassword": "...",
  "newPassword": "...",
  "code": "428193"
}
```

`code` required when 2FA active.

# User Service

## 18. Current User CRUD

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/users/me` | read current user |
| PATCH | `/api/v1/users/me` | update allowed profile fields |
| DELETE | `/api/v1/users/me` | delete account with reauthentication |

PATCH:

```json
{ "displayName": "Alice Smith" }
```

Email change is out of MVP.

DELETE:

```json
{
  "password": "...",
  "code": "428193"
}
```

`code` required when 2FA enabled.

## 19. Watchlist

| Method | Path | Result |
|---|---|---|
| GET | `/api/v1/users/me/watchlist` | list/paged movie summaries |
| POST | `/api/v1/users/me/watchlist/{movieId}` | idempotent add |
| DELETE | `/api/v1/users/me/watchlist/{movieId}` | idempotent remove |

## 20. User-Centric Rating History Facade

`GET /api/v1/users/me/ratings` — authenticated; User Service.

Returns the same logical current-user history as `/ratings/me` but is read-only. It must never mutate `RATED`.

# Movie Service

## 21. List/Search Movies

`GET /api/v1/movies` — public.

Parameters:

- `q`
- `genreId`
- `releaseYearFrom`
- `releaseYearTo`
- `releaseDateFrom`
- `releaseDateTo`
- `minAverageRating`
- `sort`
- `page`
- `size`

Sort allowlist:

- `relevance`
- `title_asc`
- `title_desc`
- `release_year_desc`
- `release_year_asc`
- `rating_desc`

Anonymous results use `viewer: null`.

## 22. Movie Detail

`GET /api/v1/movies/{id}` — public.

```json
{
  "id": "uuid",
  "title": "The Matrix",
  "overview": "...",
  "releaseYear": 1999,
  "releaseDate": "1999-03-31",
  "runtimeMinutes": 136,
  "posterUrl": "https://...",
  "genres": [{"id":"uuid","name":"Science Fiction"}],
  "averageRating": 4.62,
  "ratingCount": 148,
  "viewer": {
    "rating": 5,
    "watchlisted": true
  }
}
```

If exact date unknown: `releaseDate: null`.

## 23. Create Movie

`POST /api/v1/movies` — ADMIN.

```json
{
  "title": "The Matrix",
  "overview": "...",
  "releaseYear": 1999,
  "releaseDate": "1999-03-31",
  "runtimeMinutes": 136,
  "posterUrl": "https://...",
  "genreIds": ["genre-uuid"]
}
```

Rules: at least one known genre; year/date consistency; URL validation; transaction.

## 24. Update Movie

`PATCH /api/v1/movies/{id}` — ADMIN.

Partial allowed fields; genre update transactional.

## 25. Delete Movie

`DELETE /api/v1/movies/{id}` — ADMIN.

Success `204`; graph cleanup follows database spec.

## 26. Related Movies

`GET /api/v1/movies/{id}/related` — public.

Bounded `limit`, non-personalized related summaries.

## 27. Personalized Recommendation Facade

`GET /api/v1/movies/recommended` — authenticated.

Movie Service forwards end-user bearer token, request ID, and equivalent filters to Recommendation Service. It must be logically equivalent to `/recommendations/me`.

Recommendation Service unavailable → controlled `503`; normal movie operations continue.

# Genre API — Movie Service

## 28. Genres

| Method | Path | Access |
|---|---|---|
| GET | `/api/v1/genres` | public |
| POST | `/api/v1/genres` | ADMIN |
| PATCH | `/api/v1/genres/{id}` | ADMIN |
| DELETE | `/api/v1/genres/{id}` | ADMIN |

Delete returns `409` while referenced.

# Rating Service

## 29. Create Rating

`POST /api/v1/ratings`.

```json
{
  "movieId": "uuid",
  "score": 5
}
```

Success `201`. Existing rating → `409 RATING_ALREADY_EXISTS`. **No upsert.**

## 30. Read Own Movie Rating

`GET /api/v1/ratings/{movieId}`.

Own rating or `404`.

## 31. Update Rating

`PUT /api/v1/ratings/{movieId}`.

```json
{ "score": 3 }
```

Missing rating → `404`.

## 32. Delete Rating

`DELETE /api/v1/ratings/{movieId}`.

Idempotent delete: `204` even if already absent. Never touches another user.

## 33. Rating History

`GET /api/v1/ratings/me` — pageable.

## 34. Movie Rating Summary

`GET /api/v1/ratings/movies/{movieId}/summary` — public.

Zero ratings:

```json
{
  "movieId": "uuid",
  "averageRating": null,
  "ratingCount": 0
}
```

# Recommendation Service

## 35. Personalized Recommendations

`GET /api/v1/recommendations/me`.

Filters: genre, year/date bounds, minimum average rating, sort, page, size.

```json
{
  "items": [
    {
      "movie": {
        "id": "uuid",
        "title": "Blade Runner 2049",
        "releaseYear": 2017,
        "releaseDate": "2017-10-06",
        "posterUrl": "https://...",
        "genres": [{"id":"uuid","name":"Science Fiction"}],
        "averageRating": 4.41
      },
      "recommendationScore": 0.87,
      "signals": {
        "collaborative": 0.91,
        "content": 0.76,
        "popularity": 0.74
      },
      "reason": {
        "type": "SIMILAR_USERS",
        "text": "Users with similar ratings also liked this movie"
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

Strategies:

- `POPULARITY`
- `CONTENT_PLUS_POPULARITY`
- `HYBRID`

No peer identities.

## 36. Recommendation Share Create

`POST /api/v1/recommendation-shares`.

```json
{
  "movieId": "uuid",
  "expiresInDays": 30
}
```

`201`:

```json
{
  "id": "share-uuid",
  "movieId": "movie-uuid",
  "publicToken": "raw-token-returned-at-creation",
  "publicPath": "/share/raw-token-returned-at-creation",
  "createdAt": "...",
  "expiresAt": "...",
  "revoked": false
}
```

Database stores hash only.

## 37. Recommendation Share CRUD

| Method | Path | Access |
|---|---|---|
| GET | `/api/v1/recommendation-shares` | owner list |
| GET | `/api/v1/recommendation-shares/{id}` | owner |
| PATCH | `/api/v1/recommendation-shares/{id}` | owner |
| DELETE | `/api/v1/recommendation-shares/{id}` | owner |

PATCH may change expiry only in MVP; it may never change owner/movie identity.

## 38. Public Share

`GET /api/v1/shares/{publicToken}` — public.

Valid returns public-safe movie/share data. Unknown/expired/revoked/deleted → `404`.

# Cross-Cutting

## 39. ADMIN Authorization

Spring backend role authorization required for all Movie/Genre mutations. Angular hiding UI is insufficient.

## 40. Ownership

For ratings, watchlist, profile, shares, sessions:

- identity comes from JWT subject
- ownership verified server-side
- request does not accept arbitrary `userId` for personalized mutations

## 41. Input Bounds

Canonical validation includes bounded email/display/password/title/overview/search, realistic year/runtime, rating 1–5, exact TOTP format, page >= 0, size 1–100, bounded share expiry.

## 42. Cypher Safety

All data values are query parameters. Dynamic order fields come only from server allowlists/enums.

## 43. Rate Limiting

Strict for register/login/2FA/refresh; broader bounded limits for expensive search/recommendation. Limit exceed → `429`.

## 44. CORS / Cookie / CSRF

Production same-origin via Nginx. Development CORS uses explicit origin allowlist. Refresh/logout cookie endpoints validate expected Origin/Referer policy alongside SameSite behavior.

## 45. API Definition of Done

Tests prove:

- routes/methods/statuses
- USER/ADMIN authorization
- horizontal ownership
- refresh/2FA lifecycle
- true rating CRUD
- RecommendationShare CRUD
- Movie recommendation facade equivalence
- User rating-history read facade
- malicious-input parameterization
- consistent Problem Details
