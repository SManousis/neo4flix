# Neo4flix glossary

This glossary defines common terms as they are used in this project.

## A

**Access token** — A short-lived credential sent with protected API requests.
Neo4flix access tokens are signed JWTs.

**Actuator** — Spring Boot endpoints that report operational information such
as service health.

**Angular** — The TypeScript framework used for the browser frontend.

**Annotation** — Java metadata beginning with `@` that tells Spring or Spring
Data how a class, method, or field participates in the application.

**API** — Application Programming Interface. Neo4flix's HTTP API defines paths,
methods, request data, responses, and status codes used by the frontend and
services.

**Authentication** — Establishing who a requester is.

**Authorization** — Deciding whether an authenticated identity may perform an
operation.

## B

**Backend** — Server-side programs that implement rules and communicate with
the database.

**BCrypt** — The password-hashing algorithm used by Neo4flix.

**Bean** — An object created and managed by the Spring application context.

**Bolt** — Neo4j's binary database protocol, normally available on port 7687.

## C

**Component** — A reusable Angular UI unit made from TypeScript behavior, a
template, and styles.

**Container** — An isolated running process created from a Docker image.

**Controller** — A backend class that receives HTTP requests and returns HTTP
responses.

**CORS** — Cross-Origin Resource Sharing, browser rules controlling which
origins may call an API.

**Cypher** — Neo4j's graph query language.

## D

**Dependency injection** — Supplying an object's dependencies from a framework
instead of constructing them inside the object.

**Deterministic** — Producing the same predictable output for the same input.

**Docker Compose** — A tool that defines and starts the multi-container local
Neo4flix stack.

**Docker volume** — Persistent data managed by Docker. Neo4flix keeps Neo4j
data in a named volume so it survives normal container removal.

**DTO** — Data Transfer Object. A deliberately shaped value passed between
layers or over an API.

## E

**E2E test** — End-to-end test that exercises a complete user journey through
the browser and running backend.

**Environment variable** — A named configuration value supplied outside source
code, such as a database URL or JWT key.

## F

**Frontend** — The Angular application that runs in the user's browser.

## G

**GDS** — Neo4j Graph Data Science, the plugin that supplies algorithms such as
cosine similarity.

**Graph database** — A database centered on nodes and relationships.

**Guard** — Angular logic that decides whether navigation to a route should
continue. It improves UX but does not replace backend authorization.

## H

**Hash** — A one-way derived value used to verify data without storing the
original secret. Passwords and public share tokens are stored as hashes.

**Health check** — A probe that reports whether a container or service is ready.

**HTTP** — The protocol used for browser and service requests.

**HTTPS** — HTTP protected with TLS encryption and server identity
certificates.

## I

**Idempotent** — Safe to repeat without changing the final result beyond the
first successful application.

**Index** — A database structure that accelerates lookup.

**Interceptor** — Angular HTTP logic that can inspect or modify requests and
responses, such as attaching an access token.

## J

**JAR** — Java Archive, the packaged file used to run a Java service.

**JSON** — JavaScript Object Notation, the text data format used by the HTTP
API.

**JWT** — JSON Web Token, a signed token containing identity and role claims.

## M

**Maven** — The Java dependency, build, and test tool used by Neo4flix.

**Migration** — An ordered, versioned database schema change.

**Microservice** — A separately running backend program responsible for a
bounded business area.

## N

**Neo4j** — The graph database used by Neo4flix.

**Nginx** — The web server that serves Angular and reverse-proxies API requests.

**Node** — A graph entity such as a user, movie, or genre.

## O

**Observable** — An RxJS type representing values that can arrive over time.

**OGM** — Object-Graph Mapping, conversion between Java objects and Neo4j graph
entities. Spring Data Neo4j provides this mapping.

## P

**Playwright** — The browser automation framework used for E2E tests.

**Port** — A network number identifying a program, such as web port 8080.

**Property** — A named value stored on a Neo4j node or relationship.

**Proxy / reverse proxy** — A server that receives a client request and forwards
it to another server. Nginx routes Neo4flix API paths this way.

## R

**Reactive form** — Angular's programmatic form model using `FormControl` and
`FormGroup`.

**Record** — A concise immutable Java data-carrier type.

**Refresh token** — A longer-lived credential used to obtain a replacement
access token. Neo4flix rotates it and sends it through an HTTP-only cookie.

**Relationship** — A directed, typed connection between Neo4j nodes. It may
also contain properties.

**Repository** — A backend object responsible for persistent data access.

**Request ID** — A safe identifier carried through a request and returned in
errors to help correlate browser behavior with logs.

**Route** — A browser URL mapped to an Angular component, or an HTTP path mapped
to a backend controller method.

## S

**Seed data** — Deliberately loaded sample data for development, audit, or load
testing.

**Service** — Depending on context, either one of the running backend programs
or a class containing reusable application logic.

**Signal** — Angular reactive state that notifies templates when its value
changes.

**Spring Boot** — The Java framework foundation used to configure and run each
backend service.

## T

**Testcontainers** — A testing library that starts disposable Docker containers,
including isolated Neo4j instances.

**TOTP** — Time-based One-Time Password, the changing six-digit code used for
optional 2FA.

**Transaction** — A group of database changes that succeeds or fails as one
unit.

**TypeScript** — The typed JavaScript language used by Angular.

## V

**Vitest** — The test runner used for Angular unit and component tests.
