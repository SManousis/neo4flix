# Batch 6 Recommendation Engine Core — Design

**Date:** 2026-09-14  
**Status:** Approved in chat; implementation pending  
**Scope:** Deterministic recommendation computation inside Recommendation Service, with real Neo4j/GDS evidence and golden-fixture validation.

## Goals

- Produce bounded, deterministic movie recommendation results for a user from graph ratings, genres, and aggregate rating data.
- Execute genuine `gds.similarity.cosine` over aligned rating vectors for collaborative similarity.
- Combine collaborative, content, and popularity signals with validated configurable weights.
- Support deterministic cold-start strategies, already-rated exclusion, safe internal filters, score bounds, and evidence-based reasons.
- Prove behavior against a real Neo4j + GDS Testcontainers fixture and record representative `EXPLAIN`/`PROFILE` observations.

## Scope boundary

Batch 6 implements the Recommendation Service core repository, scoring configuration, domain service, typed internal result/query models, and integration evidence. HTTP endpoints, Angular pages, Movie Service facade wiring, public paging/sort contracts, and sharing remain Batch 7/8 work. Watchlist relationships are readable only and are not a recommendation signal or exclusion rule.

## Architecture

### Repository

`RecommendationRepository` owns parameterized Cypher reads. It loads the requesting user's ratings, peer overlap, candidate ratings, movie/genre properties, and aggregate rating data. Collaborative similarity uses `gds.similarity.cosine(myScores, peerScores)` on deterministically ordered vectors; no stale named projection is maintained. User IDs, limits, thresholds, and filters are parameters or validated allowlisted values.

The repository bounds minimum overlap, peer count, candidate count, and page size. Candidate queries exclude every movie already rated by the requesting user, but do not exclude watchlisted movies. Returned records contain movie summaries and normalized signal inputs only; peer identities and rating vectors never cross the service boundary.

### Scoring service

`RecommendationScoringService` combines normalized signals using validated defaults:

```text
collaborative = 0.60
content       = 0.30
popularity    = 0.10
```

The configuration rejects negative weights and any sum that is not `1.0` within a small decimal tolerance. Rating preference mapping is fixed at `1→-1.0`, `2→-0.5`, `3→0.0`, `4→+0.5`, `5→+1.0`; low ratings therefore cannot create positive affinity. Every component and final score is clamped to `[0,1]`.

Popularity uses a deterministic confidence-adjusted average: normalized average rating multiplied by `count / (count + priorCount)`, with `priorCount` configurable and defaulting to `5`. Content aggregates mapped user preferences over candidate genres, preserving negative genre affinity. Collaborative score combines peer similarity, positive peer rating, and overlap confidence, then normalizes against bounded candidates.

### Strategy selection

- zero explicit ratings → `POPULARITY`
- one or two ratings → `CONTENT_PLUS_POPULARITY`
- at least three ratings and at least one qualifying peer → `HYBRID`
- mature history without a qualifying peer falls back deterministically to `CONTENT_PLUS_POPULARITY`

The result includes the selected strategy. Reasons are generated only from observed signals:

- collaborative contribution → `Users with similar ratings also liked this movie.`
- content contribution → `Matches genres from movies you rated highly.`
- popularity-only contribution → `Highly rated by Neo4flix users.`

## Query and filter contract

The internal query accepts user identity, bounded limit, minimum overlap, peer/candidate limits, and optional genre, release-year/date, and minimum-average-rating filters. Filters are applied to candidates before final ranking; they do not select a different algorithm. Ties are resolved by score descending, then movie ID ascending for deterministic output.

## Audit fixture and verification

The deterministic fixture must contain enough sci-fi, romance/drama, action, mixed, and fresh-user data to prove:

- Alice/Bob similarity exceeds Alice/Carol by a meaningful margin.
- A Bob-only highly rated candidate ranks for Alice and already-rated movies are excluded.
- Repeated low-rated genres do not produce positive content affinity.
- zero-, sparse-, and mature-history users select the documented strategies.
- filters work, reasons match their contributing signal, and all scores remain in `[0,1]`.
- the running query genuinely invokes GDS.

Unit tests cover mapping, weight validation, strategy selection, score clamping, reasons, and tie ordering. Testcontainers integration tests execute the repository against Neo4j/GDS, assert the golden rankings and exclusions, and capture `EXPLAIN`/`PROFILE` notes without secrets.

## Non-goals

- No recommendation HTTP API or Angular recommendation UI in Batch 6.
- No watchlist weighting, social identity exposure, opaque ML model, mutable score cache, or speculative graph projection.
- No dynamic Cypher interpolation for user input, sorting, or limits.
