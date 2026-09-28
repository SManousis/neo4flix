# Neo4flix — Graph Database Specification

> **Authority:** This document owns graph labels, relationship types/properties, persistence invariants, uniqueness/index strategy, migrations, data ownership, deletion semantics, and graph transaction rules.

## 1. Database Choice

One Neo4j Community 2026.07.x database is shared by the four required business services.

Graph Data Science 2026.07 is installed because personalized recommendation logic uses graph/similarity functions.

All application IDs are explicit stable UUID strings. Neo4j internal element IDs are never public API identifiers.

## 2. Business Graph Overview

```text
(:User)-[:RATED {key, score, createdAt, updatedAt}]->(:Movie)
(:User)-[:WATCHLISTED {key, createdAt}]->(:Movie)
(:Movie)-[:IN_GENRE]->(:Genre)

(:User)-[:CREATED_SHARE]->(:RecommendationShare)-[:SHARES]->(:Movie)
```

Security/session support:

```text
(:User)-[:HAS_SESSION]->(:AuthSession)
(:User)-[:HAS_AUTH_CHALLENGE]->(:AuthChallenge)
```

TOTP active/pending secrets are encrypted User properties, not separate public graph entities.

## 3. `User` Node

Label: `User`.

Properties:

| Property | Required | Notes |
|---|---:|---|
| `id` | yes | UUID string; unique |
| `email` | yes | display/original normalized form |
| `normalizedEmail` | yes | canonical lowercase email; unique |
| `displayName` | yes | bounded |
| `passwordHash` | yes | BCrypt hash |
| `role` | yes | `USER` or `ADMIN` |
| `enabled` | yes | boolean |
| `twoFactorEnabled` | yes | boolean |
| `totpSecretEncrypted` | conditional | only active after confirmation |
| `pendingTotpSecretEncrypted` | optional | temporary enrollment secret |
| `pendingTotpExpiresAt` | optional | expiry for pending enrollment |
| `createdAt` | yes | UTC datetime |
| `updatedAt` | yes | UTC datetime |

Persistence entities must never be serialized directly to API responses.

### Invariants

- `normalizedEmail` is unique.
- `role` is one of `USER`, `ADMIN`.
- `twoFactorEnabled=true` requires an active encrypted secret at application level.
- pending enrollment expiry is enforced by service logic.
- password/TOTP secrets never appear in API/logs.

## 4. `Movie` Node

Label: `Movie`.

Properties:

| Property | Required | Notes |
|---|---:|---|
| `id` | yes | UUID string; unique |
| `title` | yes | 1–200 chars |
| `normalizedTitle` | yes | search helper |
| `overview` | optional | bounded text |
| `releaseYear` | yes | realistic bounded integer |
| `releaseDate` | optional | Neo4j date; never fabricated |
| `runtimeMinutes` | optional | positive reasonable range |
| `posterUrl` | optional | validated URL |
| `externalSource` | optional | e.g. `MOVIELENS` |
| `externalId` | optional | source-local identifier |
| `createdAt` | yes | UTC datetime |
| `updatedAt` | yes | UTC datetime |

If `releaseDate` exists, `year(releaseDate)` must equal `releaseYear`.

Average rating and rating count are derived from `RATED` relationships and are not authoritative Movie properties in MVP.

## 5. `Genre` Node

Label: `Genre`.

Properties:

| Property | Required | Notes |
|---|---:|---|
| `id` | yes | UUID string; unique |
| `name` | yes | canonical display name |
| `normalizedName` | yes | unique canonical match key |
| `createdAt` | yes | UTC datetime |
| `updatedAt` | yes | UTC datetime |

Relationships:

```text
(Movie)-[:IN_GENRE]->(Genre)
```

A Movie must have at least one genre at application level.

## 6. `RATED` Relationship

```text
(User)-[:RATED]->(Movie)
```

Properties:

| Property | Required | Notes |
|---|---:|---|
| `key` | yes | deterministic `<userId>:<movieId>`; unique among RATED |
| `score` | yes | integer 1–5 |
| `createdAt` | yes | UTC datetime |
| `updatedAt` | yes | UTC datetime |

The deterministic key is an internal integrity mechanism. It need not be returned through public APIs.

### Create semantics

`POST /ratings` is true create.

Implementation must not use an unconditional upsert that changes an existing score.

Expected graph transaction:

1. match authenticated User by JWT subject
2. match Movie
3. create `RATED` with deterministic key and score
4. relationship uniqueness constraint prevents concurrent duplicates
5. map duplicate constraint violation to HTTP conflict

### Update semantics

Match `RATED.key`/endpoints and update `score`, `updatedAt` only.

### Delete semantics

Delete relationship only.

## 7. `WATCHLISTED` Relationship

```text
(User)-[:WATCHLISTED]->(Movie)
```

Properties:

| Property | Required | Notes |
|---|---:|---|
| `key` | yes | deterministic `<userId>:<movieId>`; unique among WATCHLISTED |
| `createdAt` | yes | UTC datetime |

Add is idempotent. `MERGE` is appropriate because repeated add means the same final state.

`WATCHLISTED` is not initially a recommendation preference signal.

## 8. `RecommendationShare` Node

Label: `RecommendationShare`.

Properties:

| Property | Required | Notes |
|---|---:|---|
| `id` | yes | UUID; unique |
| `publicTokenHash` | yes | unique one-way hash of public token |
| `createdAt` | yes | UTC datetime |
| `expiresAt` | optional | null = application-defined default/no expiry only if approved |
| `revokedAt` | optional | revoked shares resolve as unavailable |

Relationships:

```text
(User)-[:CREATED_SHARE]->(RecommendationShare)
(RecommendationShare)-[:SHARES]->(Movie)
```

The raw public token is returned only at creation and exists in the URL/client. Neo4j stores its hash, not the raw token.

The share does not persist private recommendation vectors/similar-user identities.

## 9. `AuthSession` Node

Label: `AuthSession`.

Properties:

| Property | Required | Notes |
|---|---:|---|
| `id` | yes | UUID; unique |
| `refreshTokenHash` | yes | unique hash; raw token never stored |
| `createdAt` | yes | UTC datetime |
| `expiresAt` | yes | UTC datetime |
| `revokedAt` | optional | logout/rotation revocation |
| `rotatedFromSessionId` | optional | audit/debug lineage only |

Relationship:

```text
(User)-[:HAS_SESSION]->(AuthSession)
```

On refresh rotation, the old session is revoked and a new session/token is created transactionally enough to prevent old-token reuse.

## 10. `AuthChallenge` Node

Used for login 2FA challenges.

Properties:

| Property | Required | Notes |
|---|---:|---|
| `id` | yes | UUID; unique |
| `tokenHash` | yes | unique; raw challenge token never stored |
| `purpose` | yes | currently `LOGIN_2FA` |
| `createdAt` | yes | UTC datetime |
| `expiresAt` | yes | short lifetime, e.g. 5 min |
| `usedAt` | optional | one-time use |

Relationship:

```text
(User)-[:HAS_AUTH_CHALLENGE]->(AuthChallenge)
```

Successful verification marks/consumes the challenge; replay fails.

## 11. TOTP Enrollment Persistence

During `/auth/2fa/setup`:

- generate secret
- encrypt it with AES-256-GCM using deployment key outside Neo4j
- store in `pendingTotpSecretEncrypted`
- set short `pendingTotpExpiresAt`
- return QR/manual setup data to the authenticated user

During `/auth/2fa/confirm`:

- verify pending state exists and is unexpired
- validate supplied TOTP
- move pending secret to active `totpSecretEncrypted`
- clear pending fields
- set `twoFactorEnabled=true`

Disable:

- verify password + current TOTP
- clear active/pending secret fields
- set flag false

## 12. Constraint Plan

Use named constraints through migrations.

Required node uniqueness constraints:

- `User.id`
- `User.normalizedEmail`
- `Movie.id`
- `Genre.id`
- `Genre.normalizedName`
- `RecommendationShare.id`
- `RecommendationShare.publicTokenHash`
- `AuthSession.id`
- `AuthSession.refreshTokenHash`
- `AuthChallenge.id`
- `AuthChallenge.tokenHash`

Required relationship property uniqueness constraints:

- `RATED.key`
- `WATCHLISTED.key`

Do not depend on Enterprise-only key/existence/type constraints. Required-property/type checks remain application validation where Community does not enforce them.

## 13. Index Plan

Constraint-backed indexes handle unique identifiers.

Additional indexes/full-text indexes as proven necessary:

- movie title search (`title`/`normalizedTitle` full-text strategy)
- `Movie.releaseYear`
- potentially `Movie.externalSource + externalId` lookup for import reconciliation

Avoid speculative indexes. Every non-constraint index should map to a demonstrated query path.

## 14. Movie Search Query Rules

Search combines:

- title/full-text query
- genre relationship
- release year range
- optional exact date range
- aggregate minimum rating
- pagination
- strict allowlisted sorting

All user values are Cypher parameters.

Dynamic sort choices are selected through application enum/allowlist, never string-concatenated from request input.

## 15. Rating Aggregates

Canonical aggregate:

```text
averageRating = avg(RATED.score)
ratingCount    = count(RATED)
```

Movies with zero ratings return a documented null/zero representation consistently across APIs.

No mutable cached average is authoritative in MVP.

## 16. Related Movies

Content-related movie query may rank candidates by shared `Genre` nodes and optionally popularity.

It is not the personalized recommendation engine.

## 17. Recommendation Read Model

Recommendation Service is allowed to read:

- User IDs and rating relationships necessary for scoring
- Movie properties
- Genre relationships
- aggregate rating data

It must not expose other users' private identities in API results.

Recommendation scoring details live in `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md`.

## 18. Service Mutation Ownership

| Data | User Service | Movie Service | Rating Service | Recommendation Service |
|---|:---:|:---:|:---:|:---:|
| `User` | **WRITE** | READ | READ | READ |
| `AuthSession` | **WRITE** | — | — | — |
| `AuthChallenge` | **WRITE** | — | — | — |
| User TOTP fields | **WRITE** | — | — | — |
| `Movie` | READ | **WRITE** | READ | READ |
| `Genre` / `IN_GENRE` | READ | **WRITE** | READ | READ |
| `RATED` | READ | READ | **WRITE** | READ |
| `WATCHLISTED` | **WRITE** | READ | READ | READ |
| `RecommendationShare` + relations | READ | READ | READ | **WRITE** |

Shared DB access never grants cross-domain mutation permission.

## 19. User Deletion Transaction

Self-delete must explicitly clean graph-owned nodes that `DETACH DELETE` would otherwise orphan.

Transaction outline:

1. authenticate/reauthenticate user
2. find user's `RecommendationShare` nodes and delete them
3. find/delete user's `AuthSession` nodes
4. find/delete user's `AuthChallenge` nodes
5. `DETACH DELETE` User (removes ratings/watchlist etc.)

No orphan sessions/challenges/shares remain.

## 20. Movie Deletion Transaction

ADMIN-only.

1. find share nodes pointing to Movie and delete those shares
2. `DETACH DELETE` Movie
3. `RATED`, `WATCHLISTED`, `IN_GENRE` disappear with detach
4. Genre nodes remain

Movie deletion must not delete users or genres.

## 21. Genre Deletion

Genre deletion is allowed only when no Movie references it.

If referenced, fail the application transaction with conflict.

## 22. Share Lookup

Public raw token from URL:

1. hash token using canonical token-hash function
2. match `RecommendationShare.publicTokenHash`
3. ensure not revoked/expired
4. traverse `SHARES` to Movie
5. return public-safe DTO only

Unknown/expired/revoked all resolve as unavailable (404 behavior at API layer).

## 23. Transaction Rules

Use one Neo4j transaction for any multi-step mutation whose partial completion would violate invariants, including:

- movie + genre relationship creation/update
- refresh-token rotation
- 2FA confirmation state transition
- user deletion cleanup
- movie/share cleanup deletion
- share creation with owner/movie relationships

## 24. Migration Ownership

Canonical migration directory:

```text
database/migrations/
```

Example ordering:

```text
V001__core_node_constraints.cypher
V002__relationship_uniqueness.cypher
V003__search_indexes.cypher
V004__auth_support_constraints.cypher
V005__share_constraints.cypher
```

Exact numbering may evolve during implementation but ownership/order may not fragment across business services.

Use Neo4j-Migrations from the one-shot migrator.

## 25. Empty-Database Verification

Required test/release gate:

```text
empty Neo4j volume
  ↓
run migrator to latest
  ↓
all constraints/indexes present
  ↓
run migrator again
  ↓
no destructive reapplication
```

## 26. Seed Categories

### Demo

Human-friendly application development data.

### Audit

Small deterministic graph with intentionally visible recommendation clusters.

### Load

Larger deterministic synthetic graph for k6/concurrency testing.

Seeds never contain real credentials. Demo passwords come from environment and are hashed through the same application-compatible mechanism/tooling.

## 27. Optional External Import

MovieLens import is optional tooling, never required startup.

Imported data stores:

- internal Neo4flix UUID
- external source name
- external source ID

Do not assume external IDs are universal internal IDs.

Never fabricate exact movie release dates not present in the source.

## 28. Persistence Definition of Done

The graph layer is complete only when tests prove:

- node identifier/email/genre uniqueness
- relationship-key uniqueness under concurrency
- rating CRUD semantics
- watchlist idempotency
- movie/genre transaction behavior
- user/movie deletion cleanup
- session/challenge/share token hashing
- empty-db migration
- deterministic seed loading
- recommendation queries execute against a real Neo4j+GDS environment
