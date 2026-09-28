# Batch 8 Recommendation Sharing and Recommendation-Service CRUD Design

## Status

Approved design for Batch 8. This slice completes the RecommendationShare resource and adds safe public sharing without changing recommendation ranking, scoring, or graph-read semantics.

## Goal

Allow an authenticated user to create, inspect, update, revoke, and delete links for movies selected from the recommendation experience. Allow an anonymous visitor to open a valid link and see only a public-safe movie summary. Keep raw share tokens out of persistence and keep all ownership decisions server-derived from the JWT subject.

## Scope

Batch 8 owns:

- Recommendation Service RecommendationShare create/list/read/update/delete behavior.
- Cryptographically random public tokens with hash-only Neo4j persistence.
- Owner isolation, bounded expiry, revocation, and unavailable-link semantics.
- Public share lookup and a public-safe response projection.
- Existing movie/account deletion cleanup integration where the current services own those mutations.
- Angular share creation/copy affordance and anonymous `/share/:publicToken` page.
- Focused backend, frontend, security, and persistence tests plus full regression verification.

Batch 8 does not change recommendation ranking, collaborative/content/popularity weights, the social graph, or the private recommendation response contract. An optional owner share-management view is included only if it fits the existing profile surface without delaying the required create/public flow.

## Domain invariants

Each `RecommendationShare` has:

- a server-generated UUID `id`;
- a 32-byte cryptographically random, URL-safe raw token generated only at creation;
- a one-way SHA-256 `publicTokenHash` stored in Neo4j;
- an owner derived from the authenticated JWT `sub`;
- exactly one target Movie;
- `createdAt`, optional `expiresAt`, and optional `revokedAt`.

The raw token is returned only in the successful creation response and is never logged, serialized from persistence, or returned by owner list/read/update/delete responses. A share is available only when its owner/movie relationships exist, `revokedAt` is null, and `expiresAt` is null or after the current UTC instant. Unknown, expired, revoked, and deleted links intentionally share the same public `404` behavior.

Expiry input uses `expiresInDays`, defaults to the API contract's 30 days, and is validated against service-configured finite lower/upper bounds. The implementation will make the minimum, maximum, and clock injectable/testable; it will not accept arbitrary timestamps from clients. PATCH can change expiry only and cannot change owner, movie, token, or creation time. DELETE is an owner-authorized hard delete; revocation is represented by a separate owner update/action that sets `revokedAt` and is irreversible for the existing token.

## HTTP API

### Create

`POST /api/v1/recommendation-shares` requires authentication:

```json
{"movieId":"movie-uuid","expiresInDays":30}
```

The service validates the movie exists, derives the owner from JWT `sub`, creates the share and both relationships in one transaction, and returns `201`:

```json
{
  "id":"share-uuid",
  "movieId":"movie-uuid",
  "publicToken":"raw-token-returned-at-creation",
  "publicPath":"/share/raw-token-returned-at-creation",
  "createdAt":"2026-09-15T12:00:00Z",
  "expiresAt":"2026-10-15T12:00:00Z",
  "revoked":false
}
```

The token is generated before the transaction, but the raw value is held only in the request scope needed to build this response. A collision on the unique hash constraint is retried with a newly generated token rather than exposing a persistence error.

### Owner CRUD

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/v1/recommendation-shares` | List only the caller's shares, newest first, without raw tokens |
| GET | `/api/v1/recommendation-shares/{id}` | Read one caller-owned share or return the shared not-found response |
| PATCH | `/api/v1/recommendation-shares/{id}` | Change expiry and/or revoke; owner only |
| DELETE | `/api/v1/recommendation-shares/{id}` | Hard-delete caller-owned share; idempotent not-found semantics |

The PATCH body accepts only bounded expiry fields and a revoke request. It rejects attempts to change `movieId`, owner, token, or creation metadata. A client-supplied `userId` is never accepted by any share endpoint.

### Public lookup

`GET /api/v1/shares/{publicToken}` is anonymous-friendly and explicitly permitted by the service security matcher. The service hashes the path token using the canonical SHA-256 function, performs a parameterized lookup, checks availability, traverses `SHARES` to Movie, and maps only public-safe fields. The response may include movie id/title/overview/release/poster/genres/rating summary and share expiry, but never creator identity, email, watchlist, ratings, vectors, peer identities, raw token hash, or internal graph fields.

## Persistence and transactions

Use explicit parameterized `Neo4jClient` statements behind a RecommendationShare repository/application service boundary. Spring Data node mapping remains useful for the existing persistence mapping test, but request paths must use narrow DTO projections instead of serializing `RecommendationShareNode`, `User`, or `Movie` records directly.

Creation creates `(:RecommendationShare)` plus `(:User)-[:CREATED_SHARE]->share` and `share-[:SHARES]->(:Movie)` atomically. Owner reads constrain the match by the authenticated user id in the same query, preventing horizontal access. Public reads match only the token hash and availability predicates. All query values are parameters; labels, relationship types, and any sort direction come from fixed server code.

The existing `V005__share_constraints.cypher` uniqueness constraints remain authoritative. Account deletion must remove owned shares before detaching the user. Movie deletion must remove inbound share nodes before detaching the movie. Batch 8 adds or verifies the Recommendation Service-side cleanup contract without weakening either deletion transaction.

## Security and failure behavior

- JWT signature, issuer, audience, expiry, and subject validation use the existing resource-server configuration.
- Owner endpoints require authentication; public lookup is the only anonymous share route.
- Missing/invalid JWT returns the existing `401` Problem Details shape.
- Owner access to another user's share returns the same not-found behavior as a missing id, avoiding an ownership oracle.
- Invalid movie ids, malformed tokens, invalid expiry bounds, and unsupported PATCH fields use the shared `400`/`404` Problem Details contract.
- Unexpected failures return generic Problem Details with trace/request id; no Cypher, credentials, stack traces, or raw tokens are returned.
- Raw token values are excluded from logs, metrics labels, exception messages, and persisted node properties.

## Angular experience

Add a typed `RecommendationShareApiService` and share models. Recommendation cards expose a Share action that creates a link, renders the returned public URL, and copies it with `navigator.clipboard.writeText` plus a text-area fallback. Copy success/failure is visible and does not block navigation.

Add an anonymous-friendly lazy route `/share/:publicToken`. The page loads the public API, renders a reusable movie summary and “Shared with you through Neo4flix,” and links to browse, register, and movie details. It renders one generic unavailable state for invalid, expired, revoked, or deleted links and never displays creator-private information. Existing auth interceptors remain optional for this route.

If the current profile shell supports it without broad navigation churn, add an owner list with copy and revoke/delete actions. This view must use the owner CRUD endpoints and must not attempt to reconstruct or display raw tokens returned by list/read calls.

## Verification strategy

Backend red/green tests cover:

- create/list/read/update/revoke/delete and typed DTOs;
- raw token absent from Neo4j parameters/properties after creation;
- owner isolation and JWT-subject derivation;
- expiry boundaries with an injectable clock;
- public valid lookup and generic 404 for random, expired, revoked, or deleted tokens;
- public projection privacy and no creator/private recommendation data;
- security matcher behavior for authenticated and anonymous routes;
- parameterized Cypher and deletion cleanup.

Frontend tests cover share creation/copy fallback, link rendering, public success, generic unavailable state, route loading, and owner-action error states. Full Maven, Angular unit/lint/build, Compose interpolation, and serial Playwright verification remain required before marking Batch 8 complete.

## Non-negotiable constraints

- Never persist or log raw public share tokens.
- Never accept arbitrary owner identity or expose another user's share metadata.
- Never expose creator email, watchlist, ratings, vectors, peer identities, or internal graph properties publicly.
- Never interpolate token, movie id, expiry, or other request data into Cypher.
- Keep public sharing separate from ranking and recommendation signal computation.
