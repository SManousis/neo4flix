# Neo4flix Graph and GDS Demo

This guide is a read-only walkthrough of the graph loaded by `seed-audit`. It uses the same labels, relationship properties, and GDS function used by the recommendation service.

## Graph shape

The audit graph has two core paths:

```text
(:User)-[:RATED {key, score, createdAt, updatedAt}]->(:Movie)-[:IN_GENRE]->(:Genre)
```

`RATED.key` is the deterministic internal key `<userId>:<movieId>`. The score belongs on the relationship because a movie has a different score for each user. `IN_GENRE` is a graph edge because a movie can have multiple genres and genre traversal is a content signal.

## Parameterized inspection

Open Neo4j Browser against the local database, then run these read-only queries:

```cypher
:param userId => '11111111-1111-1111-1111-111111111111';
:param movieId => 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee';
:param minimumOverlap => 2;

MATCH (u:User {id: $userId})-[r:RATED]->(m:Movie)
RETURN u.slug AS user, m.title AS movie, r.score AS score, r.key AS ratingKey
ORDER BY m.id;
```

```cypher
MATCH (m:Movie {id: $movieId})-[:IN_GENRE]->(g:Genre)
RETURN m.title AS movie, collect(g.name) AS genres;
```

```cypher
MATCH (me:User {id: $userId})-[:RATED]->(movie:Movie)<-[:RATED]-(peer:User)
WHERE peer <> me
WITH peer, count(movie) AS commonMovies
WHERE commonMovies >= $minimumOverlap
RETURN count(peer) AS qualifyingPeers, collect(commonMovies) AS overlapCounts;
```

The fixture contract is 6 users, 8 movies, 4 genres, 11 `IN_GENRE` relationships, and 14 `RATED` relationships. Alice has four ratings. Blade Runner is the deterministic peer-only candidate that lets the golden test demonstrate collaborative ranking.

## Collaborative query stages

The production `COLLABORATIVE_QUERY` in `RecommendationNeo4jRepository` performs these stages:

1. Match the current user and peers who rated the same movie.
2. Order by peer and movie IDs before collecting aligned rating vectors. This makes the vectors deterministic.
3. Require `$minimumOverlap` common movies.
4. Compute `gds.similarity.cosine(myScores, peerScores)` for each qualifying peer.
5. Keep the top `$peerLimit` peers by similarity.
6. Traverse each selected peer’s ratings, keeping scores at least 4 and excluding movies already rated by the current user.
7. Normalize the weighted peer score using `((peerRating.score - 3) / 2)` and return peer count/common overlap for explanation fields.

All values are bound through the repository parameter map (`$userId`, `$minimumOverlap`, `$peerLimit`, and `$candidateLimit`). No request value is concatenated into Cypher.

## GDS smoke proof

The recommendation golden test executes the same installed GDS function used by production:

```cypher
RETURN gds.similarity.cosine([1.0, 0.0], [1.0, 0.0]) AS similarity;
```

Expected result: `1.0`. The focused test is `RecommendationGoldenFixtureIT`; it also proves that Alice’s output is deterministic, contains Blade Runner, excludes Alice’s four already-rated movies, and keeps scores in `[0,1]`.

## Evidence rules

Capture only:

- node and relationship counts;
- movie/genre/rating rows needed to explain the graph;
- peer overlap counts without identity-bearing details;
- the GDS similarity scalar and test result summary.

Do not capture or publish email addresses, password hashes, JWT claims, refresh tokens, TOTP secrets, or peer identity data. The public recommendation contract exposes normalized signals and reason text, not private peer histories.
