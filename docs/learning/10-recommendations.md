# 10. How recommendations work

Recommendation Service ranks movies by combining several signals from the
graph. It does not use a hardcoded list of “movies for each genre,” and it does
not return movies the current user has already rated.

The algorithm deliberately has fallbacks because a new account has less
information than an active account.

## The three strategies

| Strategy | When it is used | Main signals |
| --- | --- | --- |
| `POPULARITY` | The user has no ratings | Average rating and rating count |
| `CONTENT_PLUS_POPULARITY` | The user has few ratings or no qualifying peers | Preferred genres plus popularity |
| `HYBRID` | The user has enough ratings and similar peers | Collaborative, content, and popularity |

The selection rules and final scoring are implemented in
[`RecommendationScoringService`](../../backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationScoringService.java).

## Signal 1: popularity

Popularity uses both average rating and rating count. A movie with one 5-star
rating should not automatically defeat a movie with hundreds of strong ratings.
Neo4flix therefore applies a confidence factor based on the count.

The result is normalized to a value between 0 and 1.

## Signal 2: content similarity

Content scoring looks at genres from movies the current user rated. High scores
become positive preferences, low scores become negative preferences, and a
neutral 3 contributes no preference.

The preference mapping is:

| Rating | Preference value |
| ---: | ---: |
| 1 | -1.0 |
| 2 | -0.5 |
| 3 | 0.0 |
| 4 | 0.5 |
| 5 | 1.0 |

Candidate movies receive a stronger content signal when their genres overlap
with positively weighted user preferences.

## Signal 3: collaborative similarity

Collaborative filtering asks: “Which other users rated some of the same movies
similarly, and what else did they like?”

The query:

1. finds peers who rated movies also rated by the current user;
2. orders the shared movies deterministically;
3. constructs aligned rating vectors;
4. requires a minimum number of shared movies;
5. compares the vectors with cosine similarity;
6. keeps the most similar peers; and
7. collects movies those peers rated highly but the current user has not rated.

### Cosine similarity in plain language

Imagine two users' shared ratings as arrows. Cosine similarity measures whether
those arrows point in a similar direction. Users can have different absolute
rating habits while still showing a similar pattern of likes and dislikes.

Neo4flix calls Neo4j GDS:

```cypher
gds.similarity.cosine(myScores, peerScores)
```

The query is in
[`RecommendationNeo4jRepository`](../../backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence/RecommendationNeo4jRepository.java).

## Combining the signals

For a hybrid result, the default weights are:

```text
60% collaborative
30% content
10% popularity
```

The weights must be finite, non-negative, and sum to 1.0. They are defined in
[`RecommendationWeights`](../../backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationWeights.java).

The final value is clamped to the range 0 through 1. Filters such as genre,
year range, minimum average rating, sort mode, page, and page size are applied
through bounded parameters.

## Why recommendations include reasons

A numeric score alone is difficult for a user or auditor to interpret. The
service uses a fixed explanation precedence: a hybrid result with any positive
collaborative signal uses the collaborative explanation; otherwise any positive
content signal uses the content explanation; otherwise it uses popularity.
The possible messages are:

- “Users with similar ratings also liked this movie.”
- “Matches genres from movies you rated highly.”
- “Highly rated by Neo4flix users.”

This makes the result explainable without exposing another user's identity or
private rating history.

## Complete recommendation flow

```text
GET /api/v1/recommendations/me
  -> identify current user from JWT
  -> count their ratings
  -> find collaborative, content, and popularity candidates
  -> select POPULARITY, CONTENT_PLUS_POPULARITY, or HYBRID
  -> remove already-rated movies
  -> apply filters and weighted scoring
  -> sort and paginate
  -> attach a human-readable reason
  -> return JSON to Angular
```

The orchestration lives in
[`RecommendationApplicationService`](../../backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationApplicationService.java).

## Sharing a recommendation

Sharing is separate from computing a ranking. When a user shares a result:

1. the service creates a `RecommendationShare` node;
2. it links the owner with `CREATED_SHARE`;
3. it links the share to the movie with `SHARES`;
4. it returns the raw public token only in the creation response; and
5. it stores only the token hash in Neo4j.

The resulting public link is reusable until it expires or is revoked. Public
lookup hashes the supplied token, rejects unknown, expired, or revoked shares,
and returns a public-safe movie projection.

## Audit fixture and proof

The audit seed creates a deliberately small graph with visible rating clusters.
That allows a reviewer to trace why a candidate was selected. The detailed
walkthrough is in
[`RECOMMENDATION_EXPLANATION.md`](../audit/RECOMMENDATION_EXPLANATION.md)
and [`GRAPH_DEMO.md`](../audit/GRAPH_DEMO.md).

## Recap

New users receive popularity results, users with limited history receive genre
plus popularity results, and established users can receive hybrid collaborative
results. Scores are normalized, already-rated movies are excluded, and every
result includes a safe explanation.

Next: [Chapter 11 explains migrations and seed data](11-data-migrations-and-audit-fixtures.md).
