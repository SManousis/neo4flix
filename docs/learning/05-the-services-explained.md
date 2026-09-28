# 5. The services explained

A **service** is a program responsible for a particular area of the
application. Neo4flix has four business services instead of one large backend.
This design makes ownership visible: when a rating changes, Rating Service is
responsible; when a movie changes, Movie Service is responsible.

This style is commonly called a **microservice architecture**. Neo4flix is an
educational version: the services are separate programs but intentionally share
one Neo4j graph so recommendation traversals remain straightforward.

## The four business services

| Service | Port | Main responsibility | Data it may change |
| --- | ---: | --- | --- |
| User Service | 8081 | Accounts, profiles, sessions, 2FA, watchlists | `User`, auth nodes, `WATCHLISTED` |
| Movie Service | 8082 | Catalog, search, details, admin movie/genre work | `Movie`, `Genre`, `IN_GENRE` |
| Rating Service | 8083 | Rating creation, updates, deletion, summaries | `RATED` |
| Recommendation Service | 8084 | Ranking, explanations, recommendation shares | `RecommendationShare` and share relationships |

Every service may read graph data needed for its work, but normal feature
mutations follow the owner in this table. Shared database access is not general
permission to change another service's data.

There are two explicit lifecycle-cleanup exceptions. User Service deletes a
departing user's sessions, challenges, recommendation shares, ratings, and
watchlist connections so no owned data is orphaned. Movie Service deletes
shares that point to a movie before detaching and deleting that movie. These
bounded deletion transactions enforce whole-graph cleanup; they do not grant
either service general write access to the other domains.

## User Service

User Service handles:

- registration and login;
- access and refresh token creation;
- profile reading and updates;
- password changes and account deletion;
- TOTP two-factor enrollment and login challenges;
- user rating-history facade calls; and
- watchlist operations.

Its application entry point is
[`UserServiceApplication`](../../backend/user-service/src/main/java/com/neo4flix/user/UserServiceApplication.java).
Authentication HTTP routes start in
[`AuthController`](../../backend/user-service/src/main/java/com/neo4flix/user/auth/AuthController.java).

User Service calls Rating Service when it needs a user's rating history. It
does not write ratings itself.

## Movie Service

Movie Service handles:

- public catalog pages;
- title, genre, year, sorting, and pagination query handling;
- movie metadata and genre details;
- related movies;
- admin movie and genre mutations; and
- a movie-facing recommendation facade.

The catalog begins at
[`MovieCatalogController`](../../backend/movie-service/src/main/java/com/neo4flix/movie/catalog/MovieCatalogController.java).
The personalized recommendation facade calls Recommendation Service through
[`MovieRecommendationClient`](../../backend/movie-service/src/main/java/com/neo4flix/movie/recommendation/MovieRecommendationClient.java).

This delegation avoids creating two different recommendation algorithms.
Movie-detail rating summaries are requested separately from Rating Service by
the Angular page.

## Rating Service

Rating Service is the only writer of `RATED` relationships. It enforces these
rules:

- the user and movie must exist;
- a score is an integer from 1 through 5;
- `POST` creates a rating and conflicts if one already exists;
- `PUT` updates an existing rating;
- `DELETE` removes it; and
- concurrent requests cannot create duplicate user/movie ratings.

The layers are visible in
[`RatingController`](../../backend/rating-service/src/main/java/com/neo4flix/rating/api/RatingController.java),
[`RatingApplicationService`](../../backend/rating-service/src/main/java/com/neo4flix/rating/RatingApplicationService.java),
and
[`RatingRepository`](../../backend/rating-service/src/main/java/com/neo4flix/rating/persistence/RatingRepository.java).

## Recommendation Service

Recommendation Service reads the connected graph and combines:

- similar-user ratings;
- genres from movies the current user liked;
- aggregate rating quality; and
- a popularity fallback when personal history is sparse.

It returns a score and an explanation, not just a movie ID. It also owns
recommendation-sharing records and public share lookup.

The main flow begins at
[`RecommendationController`](../../backend/recommendation-service/src/main/java/com/neo4flix/recommendation/api/RecommendationController.java),
continues through
[`RecommendationApplicationService`](../../backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationApplicationService.java),
and reads Neo4j through
[`RecommendationNeo4jRepository`](../../backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence/RecommendationNeo4jRepository.java).

## Supporting components

### Platform Common

`backend/platform-common` is a library, not a separately running service. It
contains behavior shared by the services, including:

- JWT resource-server configuration;
- CORS rules;
- request-ID handling; and
- the common API error representation.

Keeping these rules in one library reduces accidental differences between
services.

### Database Migrator

The migrator is a one-shot Java program. It applies Neo4j migrations, verifies
the schema, or loads one of the seed datasets. It completes before the business
services start and then exits.

### Nginx/Web

The web container serves the built Angular application and routes `/api/v1`
requests. It also adds response security headers, request IDs, and rate limits
for selected expensive routes.

## A request crossing service boundaries

Loading personalized recommendations can involve several boundaries:

```text
Angular RecommendationsComponent
  -> Nginx
  -> RecommendationController
  -> RecommendationApplicationService
  -> RecommendationNeo4jRepository
  -> Neo4j and GDS
  -> JSON response with scores and reasons
  -> Angular renders recommendation cards
```

If the browser uses Movie Service's facade, Movie Service forwards the bearer
token and request ID to Recommendation Service.

## Why not put everything in one service?

Separate services make responsibility and authorization easier to discuss and
test. They also demonstrate network communication and independent application
startup. The trade-off is additional configuration, containers, health checks,
and failure modes. For a small real-world application, a modular monolith might
be simpler; this project uses services because service design is part of the
exercise.

## Recap

User, Movie, Rating, and Recommendation services each own one business area.
They share the graph for traversal and keep ordinary mutation ownership strict,
with explicit cross-domain cleanup during user or movie deletion. Nginx,
Platform Common, and the database migrator support the four business services.

Next: [Chapter 6 introduces the Neo4j graph](06-the-neo4j-graph.md).
