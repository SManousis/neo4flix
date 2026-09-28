# 3. How Neo4flix works at a glance

Neo4flix is a client-server application. The **client** is the Angular program
running in the browser. The **servers** are Nginx and the four Spring Boot
services. Neo4j is the database that keeps data between runs.

## The complete picture

```text
Person
  |
  v
Web browser running Angular
  |
  | HTTP request, for example GET /api/v1/movies
  v
Nginx web server and reverse proxy :8080
  |
  +----> User Service           :8081 ----+
  +----> Movie Service          :8082 ----+
  +----> Rating Service         :8083 ----+----> Neo4j :7687
  +----> Recommendation Service :8084 ----+
                                             |
                                             +----> Graph Data Science

Before the services start:
Neo4j -> database-migrator -> business services -> web container
```

The ports after each component are the local development ports. A **port** is a
number that identifies a particular network program on a computer.

## What Nginx does

Nginx has two jobs:

1. It serves the compiled Angular files for browser routes.
2. It acts as a **reverse proxy**, forwarding API requests to the correct
   backend service.

Examples from [`default.conf`](../../infra/nginx/default.conf):

| Incoming path | Destination |
| --- | --- |
| `/api/v1/auth/...` | User Service |
| `/api/v1/users/...` | User Service |
| `/api/v1/movies/...` | Movie Service |
| `/api/v1/ratings/...` | Rating Service |
| `/api/v1/recommendations/...` | Recommendation Service |
| `/api/v1/shares/...` | Recommendation Service |

The browser therefore talks to one public origin, `localhost:8080`, even
though several programs handle the requests internally.

## Following a catalog request

Suppose the browser requests:

```http
GET /api/v1/movies?title=Arrival&page=0&size=20
```

The journey is:

1. Angular's catalog component asks `CatalogApiService` for movies.
2. Angular's HTTP client sends the request to the current web origin.
3. Nginx sees the `/api/v1/movies` prefix and forwards it to Movie Service.
4. `MovieCatalogController` validates the HTTP parameters.
5. `MovieCatalogRepository` runs a parameterized Cypher query against Neo4j.
6. Neo4j returns matching movies and their genres.
7. Movie Service converts the result into JSON.
8. Angular receives the JSON and updates a signal.
9. The component template redraws the result cards.

That same basic pattern—component, API request, controller, application logic,
repository, database—appears throughout the project.

## JSON: the shared message format

The browser and backend exchange most data as JSON. A simplified movie response
might look like this:

```json
{
  "id": "movie-id",
  "title": "Arrival",
  "releaseYear": 2016,
  "genres": [{ "id": "genre-id", "name": "Science Fiction" }]
}
```

The movie-detail page requests its rating summary separately from Rating
Service; Movie Service does not calculate that summary in its catalog query.

Java response records and TypeScript interfaces define the expected fields on
each side.

## Why Docker is involved

Without containers, a developer would have to install and configure Neo4j,
Nginx, every Java service, and the frontend separately. Docker packages each
runtime in an isolated container. Docker Compose reads
[`infra/compose.yml`](../../infra/compose.yml) and starts the containers as one
networked application.

Containers are not full virtual machines. They share a host-provided kernel but
have isolated files, processes, and network names. On Windows with Docker
Desktop, Linux containers normally share the kernel of Docker Desktop's Linux
virtual machine rather than the Windows kernel directly.

Inside the Compose network, services use names such as `neo4j` and
`recommendation-service`. From the host computer, the development override
publishes ports such as `localhost:7687` and `localhost:8084`.

## Startup order matters

Compose uses health checks and dependency rules so the system starts in this
order:

1. Neo4j starts and accepts authenticated Cypher queries.
2. The one-shot database migrator creates or verifies constraints and indexes.
3. The four business services start after migration succeeds.
4. The web container starts after all four services report healthy.

This prevents services from reading a database whose required schema has not
been prepared.

## Service-to-service communication

Most browser requests go directly to the service that owns the operation. One
important internal call is personalized movie recommendations:

```text
Browser -> Nginx -> Movie Service -> Recommendation Service -> Neo4j
```

Movie Service exposes the movie-facing recommendation route but delegates the
ranking algorithm to Recommendation Service. This keeps one authoritative
recommendation implementation.

## Shared database, separate ownership

All four services use the same Neo4j graph so they can traverse connected data.
They do not all have permission to mutate everything. For example, Rating
Service owns `RATED` writes, while Recommendation Service only reads ratings.
Chapter 5 describes these boundaries.

## Recap

Angular runs in the browser, Nginx serves it and routes API calls, Spring Boot
services implement the application rules, and Neo4j stores the connected data.
Docker Compose starts those pieces in a safe order.

Next: [Chapter 4 shows how to run and inspect the system](04-how-to-run-it.md).
