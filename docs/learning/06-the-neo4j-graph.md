# 6. The Neo4j graph database

Neo4j stores data as a **property graph**. A property graph contains nodes,
relationships, labels, relationship types, and properties.

Instead of beginning with rows and foreign keys, a graph emphasizes how things
are connected. That is useful for recommendations because questions such as
“which highly rated movies were liked by people with similar taste?” are
naturally questions about paths.

## Nodes, relationships, and properties

A **node** represents a thing. Neo4flix uses labels such as:

- `User`;
- `Movie`;
- `Genre`;
- `AuthSession`;
- `AuthChallenge`; and
- `RecommendationShare`.

A **relationship** connects two nodes and has a direction and type:

```text
(User)-[:RATED]->(Movie)
(User)-[:WATCHLISTED]->(Movie)
(Movie)-[:IN_GENRE]->(Genre)
(User)-[:HAS_SESSION]->(AuthSession)
(User)-[:CREATED_SHARE]->(RecommendationShare)
(RecommendationShare)-[:SHARES]->(Movie)
```

A **property** is a named value stored on a node or relationship. A `Movie`
node can have `title` and `releaseYear`. A `RATED` relationship has `score`,
`createdAt`, and `updatedAt`.

## Why the rating is a relationship

A rating does not belong to a movie alone. Alice might rate a film 5 while Bob
rates it 2. The score describes Alice's connection to that movie:

```text
(Alice:User)-[:RATED {score: 5}]->(Arrival:Movie)
(Bob:User)-[:RATED {score: 2}]->(Arrival:Movie)
```

Putting `score` on the `RATED` relationship preserves that meaning and makes
recommendation traversal natural.

## The central graph shape

```text
                +---------------------+
                |                     v
(User)-[:RATED {score}]->(Movie)-[:IN_GENRE]->(Genre)
   |                           ^
   +-----[:WATCHLISTED]--------+
```

The audit fixture builds a small deterministic version of this shape so tests
and demonstrations always produce explainable results.

## Cypher: Neo4j's query language

Neo4j uses **Cypher**, a query language whose patterns resemble the graph.

This query finds movies rated by one user:

```cypher
MATCH (user:User {id: $userId})-[rating:RATED]->(movie:Movie)
RETURN movie.title AS movie, rating.score AS score
ORDER BY movie.title;
```

Read the pattern from left to right:

1. find a `User` whose `id` matches the parameter;
2. follow outgoing `RATED` relationships;
3. reach `Movie` nodes; and
4. return the movie title and relationship score.

`$userId` is a query parameter. The application binds a value separately
instead of building Cypher by concatenating user input. Parameterization is
safer and lets Neo4j reuse query plans.

This query finds a movie's genres:

```cypher
MATCH (movie:Movie {id: $movieId})-[:IN_GENRE]->(genre:Genre)
RETURN movie.title, collect(genre.name) AS genres;
```

More read-only examples are available in
[`GRAPH_DEMO.md`](../audit/GRAPH_DEMO.md).

## Constraints and indexes

A **constraint** is a database rule. For example, movie IDs and normalized user
emails must be unique. Rating and watchlist relationships use deterministic
keys so concurrent requests cannot create duplicate relationships.

An **index** is a data structure that helps Neo4j find matching nodes more
quickly. Search indexes support catalog queries.

The actual Cypher files are in [`database/migrations`](../../database/migrations/).
For example,
[`V001__core_node_constraints.cypher`](../../database/migrations/V001__core_node_constraints.cypher)
creates core node constraints, while
[`V002__relationship_uniqueness.cypher`](../../database/migrations/V002__relationship_uniqueness.cypher)
creates relationship-key constraints.

## Java objects and graph data

Spring Data Neo4j maps Java records to graph entities. This simplified shape
appears in the real Movie Service model:

```java
@Node("Movie")
public record MovieNode(
    @Id @Property("id") String id,
    String title,
    @Relationship(type = "IN_GENRE", direction = OUTGOING)
    Set<GenreNode> genres
) {}
```

The annotations tell Spring Data Neo4j that:

- the record represents a `Movie` node;
- `id` identifies the node; and
- `genres` represents outgoing `IN_GENRE` relationships.

See the complete model in
[`MovieNode.java`](../../backend/movie-service/src/main/java/com/neo4flix/movie/persistence/MovieNode.java).

Some repositories use `Neo4jClient` and explicit Cypher instead of generated
repository methods. Explicit queries are useful when ranking, pagination, or
multi-step graph operations need precise control.

## Graph Data Science

Neo4flix uses the Neo4j Graph Data Science plugin, usually shortened to GDS.
The recommendation query calls `gds.similarity.cosine` to compare aligned
rating vectors. Chapter 10 explains the idea without requiring advanced
mathematics.

## Inspecting the graph

With the local stack running, open <http://localhost:7474/> and sign in with the
local Neo4j credentials. Use read-only `MATCH ... RETURN` queries while learning.
Avoid `DELETE`, `DETACH DELETE`, or unreviewed write queries against data you
want to keep.

## Recap

Neo4j stores things as nodes and their connections as relationships. Ratings
belong on `RATED` relationships because they describe a specific user's opinion
of a specific movie. Cypher expresses graph patterns, constraints protect
invariants, and GDS supplies graph-oriented calculations.

Next: [Chapter 7 explains the Java and Spring Boot backend](07-the-java-backend.md).
