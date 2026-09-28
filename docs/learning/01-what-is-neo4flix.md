# 1. What is Neo4flix?

Neo4flix is a movie discovery and recommendation web application. A person can
create an account, browse movies, search the catalog, rate movies, keep a
watchlist, receive recommendations, and share a recommended movie through a
public link.

It was built for the 01-edu Neo4flix exercise. The project demonstrates how a
modern web frontend, several Java backend services, and a graph database can
work together.

Neo4flix is **not** a video streaming service. It stores information about
movies and user preferences, but it does not host or play films.

## A simple example

Imagine a new user named Alice:

1. Alice registers with an email address, display name, and password.
2. She signs in and browses the movie catalog.
3. She gives several movies ratings from 1 to 5.
4. Neo4flix follows connections in the graph to find movies that match her
   tastes.
5. Alice adds one recommendation to her watchlist.
6. She creates a public link for another recommendation and sends it to a
   friend.

Every technical part of the project exists to make some part of that journey
work.

## The main features

| Feature | What it means for a user |
| --- | --- |
| Registration and login | Create an account and return to it securely |
| Catalog | Browse movies and open their details |
| Search and filters | Find movies by title, genre, year, or other criteria |
| Ratings | Give a movie one rating from 1 to 5, update it, or remove it |
| Watchlist | Save movies to consider later |
| Recommendations | Receive ranked movies based on graph data and ratings |
| Explanations | See a short reason for each recommendation |
| Sharing | Create a public, expiring link for a movie recommendation |
| Profile and 2FA | Manage an account and optionally enable a second login step |
| Admin catalog | Let an administrator create, edit, and delete catalog data |

The browser routes that expose these pages are defined in
[`app.routes.ts`](../../frontend/src/app/app.routes.ts).

## The technology in one paragraph

The pages in the browser are built with **Angular** and TypeScript. The browser
sends HTTP requests to **Nginx**, which serves the Angular files and forwards
API requests to four **Spring Boot** services written in Java. Those services
read and write a shared **Neo4j** graph database. **Docker Compose** starts the
database, services, database migrator, and web container as one local system.

Do not worry if those names are unfamiliar. Later chapters introduce them one
at a time.

## A map of the repository

The top-level folders separate the major responsibilities:

```text
Neo4flix/
├── backend/        Java services and shared Java code
├── database/       Neo4j migrations, seeds, and the migrator program
├── frontend/       Angular application and browser tests
├── infra/          Docker Compose, Nginx, and Neo4j configuration
├── scripts/        Verification, seed, security, and operations helpers
└── docs/           Guides, technical references, and audit evidence
```

Important root files include:

- [`README.md`](../../README.md), the project overview and quick start;
- [`pom.xml`](../../pom.xml), the top-level Maven build definition;
- [`Makefile`](../../Makefile), short names for common commands;
- [`.env.example`](../../.env.example), the names of required local settings;
  and
- [`infra/compose.yml`](../../infra/compose.yml), the container topology.

## Four words worth learning now

**Frontend** means the part a person sees and interacts with in a browser.

**Backend** means programs that receive requests, apply rules, and work with
data.

**API** means a defined way for programs to communicate. In Neo4flix, the
frontend calls HTTP API paths such as `/api/v1/movies`.

**Database** means the persistent store. Neo4flix uses Neo4j, which represents
data as a graph of connected nodes instead of primarily as tables.

## Recap

Neo4flix is an educational movie discovery application. Angular provides the
browser interface, Spring Boot provides the backend, Neo4j stores connected
data, Nginx directs requests, and Docker Compose runs the complete system.

Next: [Chapter 2 explains how a person uses the application](02-how-a-user-uses-it.md).
