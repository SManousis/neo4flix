# Neo4flix — Technical Architecture

> **Authority:** This document owns the implementation stack, service boundaries, repository layout, runtime topology, configuration, build/test tooling, cross-cutting backend/frontend structure, and architectural prohibitions.

## 1. Architecture Goals

The architecture must:

- satisfy the required Neo4j + Spring Boot + Angular + microservices + Docker stack
- preserve one useful traversable graph
- keep service mutation ownership unambiguous
- be reproducible from a clean checkout
- be easy to inspect during audit
- avoid unnecessary distributed-system complexity
- make security/testing first-class

## 2. Pinned Technology Baseline

Generated/reconciled on 2026-09-07.

| Concern | Pinned choice |
|---|---|
| Java | 21 LTS |
| Spring Boot | 4.1.1 |
| Spring web | Spring MVC |
| Security | Spring Security |
| JWT validation | Spring Security OAuth2 Resource Server |
| Neo4j mapping | Spring Data Neo4j 8.1.x / compatible BOM-managed version |
| Neo4j | Community 2026.07.x; baseline image 2026.07.1 |
| Graph algorithms | GDS 2026.07 |
| Build | Maven + Maven Wrapper |
| Frontend | Angular 22.1.5 |
| UI | Angular Material 22.1.5 + SCSS |
| Node | 24 LTS |
| TypeScript | 6.0.x |
| Angular state | Signals + services |
| Backend unit | JUnit 5 + Mockito + AssertJ |
| Graph integration | Testcontainers Neo4j |
| Frontend unit | Vitest |
| Browser E2E | Playwright |
| Load | k6 |
| OpenAPI | springdoc-openapi 3.1.x |
| Graph migrations | Neo4j-Migrations 4.1.x |
| Rate limit | Bucket4j 8.19.x |
| TOTP | java-otp 1.0.x + ZXing |
| Reverse proxy | Nginx |
| Containers | Docker + Docker Compose |

Repository dependency files must pin exact patch versions. `latest` is forbidden in release images.

## 3. Required Business Services

Exactly four business applications:

```text
user-service
movie-service
rating-service
recommendation-service
```

Do not create an extra auth, watchlist, genre, or Spring Cloud gateway microservice for MVP.

### User Service

- account/profile
- password
- JWT issuance
- refresh sessions
- TOTP enrollment/challenge
- watchlist
- user-centric rating-history read facade

### Movie Service

- movie/genre catalog
- search/filter/detail
- related movies
- personalized recommendation facade

### Rating Service

- authoritative `RATED` CRUD
- rating history
- aggregate rating reads

### Recommendation Service

- authoritative personalized recommendation algorithm
- filters/explanation
- RecommendationShare CRUD/public share

## 4. Shared Neo4j Decision

All four services connect to one Neo4j database.

```text
User Service ──────────────┐
Movie Service ─────────────┤
Rating Service ────────────┼──> one Neo4j graph + GDS
Recommendation Service ────┘
```

Reason: splitting the data into four physical databases would destroy the natural user-rating-movie-genre traversal central to the assignment.

This does **not** mean every service may mutate every graph element. Mutation ownership is an architectural invariant defined in `docs/reference/03_GRAPH_DATABASE_SPEC.md`.

## 5. HTTP Topology

Production-like path:

```text
Browser
   │ HTTPS
   ▼
Nginx
├── Angular static SPA
└── /api/v1 reverse proxy
      ├── user-service
      ├── movie-service
      ├── rating-service
      └── recommendation-service
```

The browser sees one public origin and one `/api/v1` namespace.

## 6. Nginx Route Ownership

```text
/api/v1/auth/**                  → user-service
/api/v1/users/**                 → user-service
/api/v1/movies/**                → movie-service
/api/v1/genres/**                → movie-service
/api/v1/ratings/**               → rating-service
/api/v1/recommendations/**       → recommendation-service
/api/v1/recommendation-shares/** → recommendation-service
/api/v1/shares/**                → recommendation-service
```

Nginx routing is not authorization. Protected services validate JWTs themselves.

## 7. Inter-Service Calls

Primary synchronous call:

```text
Movie Service
   └── GET /movies/recommended facade
          ↓ forwards end-user bearer token + request ID
Recommendation Service
          ↓
Neo4j
```

Use Spring `RestClient` (imperative) for this call.

No Kafka, RabbitMQ, gRPC, service mesh, or retry storm framework.

Recommendation Service reads the shared graph directly rather than fan-out-calling the other three services for data already in Neo4j.

## 8. Authentication Architecture

### Access token

- RS256-signed JWT
- short-lived, default about 15 minutes
- `sub` = user UUID
- includes roles, issuer, audience, issue/expiry, JTI
- private signing key available only to User Service
- public verification key available to protected services

### Refresh token

- opaque cryptographically random token
- rotating
- persisted only as a one-way hash in Neo4j
- browser receives Secure + HttpOnly + SameSite cookie
- never available to Angular JavaScript

### Angular token storage

Access JWT: memory only.

Not allowed:

- `localStorage`
- `sessionStorage`
- JS-readable long-lived cookie

On browser reload Angular calls refresh using the HttpOnly cookie.

## 9. TOTP Architecture

- RFC 6238
- six digits
- 30-second timestep
- authenticator-app compatible
- QR code generated from standard `otpauth://totp/...`
- active TOTP secret encrypted using AES-256-GCM with a deployment key outside Neo4j
- pending enrollment secret stored separately from active secret and expires
- login uses a server-controlled one-time AuthChallenge before normal JWT issuance

## 10. Spring Programming Model

Use imperative Spring MVC + Spring Data Neo4j.

Do not use WebFlux/Reactor as the application programming model.

Typical request path:

```text
Controller
   ↓
Application Service / Use Case
   ↓
Domain rules
   ↓
Repository / Neo4jClient custom query
   ↓
DTO mapper
   ↓
HTTP response
```

Controllers must not own business logic or build Cypher strings.

## 11. Neo4j Access Style

Use Spring Data Neo4j repositories for straightforward node/relationship mapping.

Use `Neo4jClient` / custom repository implementations with parameterized Cypher for:

- search aggregations
- rating aggregates
- related movies
- recommendation candidate/scoring queries
- deletion cleanup transactions

Do not force complex graph traversal into unreadable derived-method names.

## 12. Entity vs API Separation

Neo4j entities and relationship-property classes are persistence models.

Controllers return explicit response DTOs.

Forbidden pattern:

```java
return repository.findById(id);
```

Benefits:

- no accidental sensitive User property serialization
- no unexpected graph expansion in JSON
- API contract remains independent of mapping changes

## 13. Shared Java Module

A small `platform-common` module is allowed for cross-cutting infrastructure only.

Allowed examples:

- Problem Details helpers
- request-ID filter/utilities
- JWT principal/role helpers
- pagination validation primitives
- common test helpers

Forbidden in shared common:

- User/Movie/Rating domain entities
- service use cases
- recommendation rules
- repositories
- shared mutable business model

## 14. Feature-First Packages

Example Movie Service:

```text
com.neo4flix.movie
├── movie/
│   ├── web/
│   ├── application/
│   ├── domain/
│   └── persistence/
├── genre/
│   ├── web/
│   ├── application/
│   ├── domain/
│   └── persistence/
├── recommendationfacade/
├── security/
├── config/
└── observability/
```

Do not create one global `controller/`, `service/`, `repository/`, `dto/` dump for every feature.

## 15. Angular Architecture

Use:

- standalone components
- lazy feature routes where useful
- Angular Reactive Forms
- Signals/computed/inject for client state
- Angular Material primitives
- SCSS

No NgRx for MVP.

Feature layout:

```text
frontend/src/app/
├── core/
│   ├── auth/
│   ├── api/
│   ├── guards/
│   ├── interceptors/
│   └── layout/
├── shared/
│   ├── components/
│   ├── models/
│   └── utilities/
└── features/
    ├── auth/
    ├── home/
    ├── movies/
    ├── ratings/
    ├── recommendations/
    ├── watchlist/
    ├── profile/
    ├── shares/
    └── admin/
```

## 16. Angular HTTP Layer

Typed domain clients:

```text
authApi
userApi
movieApi
ratingApi
recommendationApi
```

Feature components do not scatter raw `HttpClient` calls.

Authentication interceptor:

- attaches in-memory access JWT
- handles token-expiry refresh flow once
- coordinates concurrent refresh requests
- prevents infinite retry loops

## 17. OpenAPI

Each Spring service exposes development OpenAPI through springdoc.

Generated OpenAPI describes the current implementation; `docs/reference/04_API_SPEC.md` remains the requirement authority.

A mismatch means code/docs must be reconciled, not that generated OpenAPI silently overrides the canonical specification.

## 18. Error Handling

Use Spring `ProblemDetail` as the base for RFC 7807-style errors.

Common extensions:

- `code`
- `traceId`
- `fieldErrors` when validation fails

No stack traces or Cypher in client responses.

## 19. Request IDs and Logging

Nginx generates/forwards `X-Request-Id`.

Every service:

- validates/accepts or generates request ID
- stores it in MDC
- propagates it on service-to-service calls
- returns it in responses/error detail where appropriate

Local logs may be human-readable; deployed logs are structured JSON.

Never log secrets listed in `docs/reference/06_TESTING_SECURITY.md`.

## 20. Health/Actuator

Each service exposes restricted Actuator health.

At minimum:

- liveness/application state
- Neo4j dependency state where applicable

Do not expose sensitive actuator endpoints publicly.

## 21. Graph Migrations

Use Neo4j-Migrations through a one-shot `database-migrator` infrastructure module/container.

Startup order:

```text
Neo4j healthy
  ↓
database-migrator applies canonical migrations
  ↓
migrator exits 0
  ↓
business services start
```

No business service independently executes schema migrations.

Canonical directory:

```text
database/migrations/
```

## 22. Seed Separation

```text
database/seeds/
├── demo/
├── audit/
└── load/
```

Seeds are not migrations.

Production-like startup never automatically loads demo users/ratings.

## 23. GDS Packaging

Development/test may use Neo4j Docker's plugin convenience mechanism.

Deployment must use a deterministic/pinned Neo4j + compatible GDS plugin image/build. Do not depend on runtime internet download of GDS for release startup.

No APOC initially. Add only if an approved requirement demonstrates a need.

## 24. Docker Services

Canonical Compose includes:

```text
neo4j
database-migrator
user-service
movie-service
rating-service
recommendation-service
web (Angular + Nginx)
```

`database-migrator` is infrastructure, not a fifth business microservice.

## 25. Development Ports

Recommended:

```text
web/Nginx                 8080
Angular dev               4200
User Service              8081
Movie Service             8082
Rating Service            8083
Recommendation Service    8084
Neo4j Browser             7474
Neo4j Bolt                7687
```

Inside Docker use Compose DNS names, never `localhost` for another container.

## 26. Configuration

Spring uses `application.yml` + environment/profile-specific configuration.

Sensitive values come from environment/mounted secrets, including:

- Neo4j credentials
- JWT private key
- JWT public key path/material
- TOTP encryption key
- demo seed passwords

Recommendation configuration includes:

- collaborative weight
- content weight
- popularity weight
- minimum overlap
- peer/candidate limits

Weights must validate to sum to 1.0.

## 27. Repository Layout

```text
neo4flix/
├── README.md
├── docs/reference/
│   ├── 00_MASTER_EXECUTION_PLAN.md
│   ├── 01_PRODUCT_SPEC.md
│   ├── 02_TECHNICAL_ARCHITECTURE.md
│   ├── 03_GRAPH_DATABASE_SPEC.md
│   ├── 04_API_SPEC.md
│   ├── 05_FRONTEND_SPEC.md
│   ├── 06_TESTING_SECURITY.md
│   ├── 07_RECOMMENDATION_AUDIT_VALIDATION.md
│   └── 08_DEPLOYMENT_OPERATIONS.md
├── pom.xml
├── mvnw
├── mvnw.cmd
├── Makefile
├── .editorconfig
├── .gitignore
├── .env.example
│
├── backend/
│   ├── pom.xml
│   ├── platform-common/
│   ├── user-service/
│   ├── movie-service/
│   ├── rating-service/
│   └── recommendation-service/
│
├── frontend/
│   ├── package.json
│   ├── package-lock.json
│   ├── angular.json
│   ├── src/
│   ├── e2e/
│   └── Dockerfile
│
├── database/
│   ├── migrator/
│   ├── migrations/
│   └── seeds/
│       ├── demo/
│       ├── audit/
│       └── load/
│
├── infra/
│   ├── compose.yml
│   ├── compose.dev.yml
│   ├── nginx/
│   └── neo4j/
│
├── tests/
│   └── load/
├── scripts/
└── docs/
    ├── audit/
    └── superpowers/plans/
```

## 28. Root Build/Verification

Backend reactor:

```bash
./mvnw verify
```

Frontend uses committed `package-lock.json` and `npm ci`.

Root Makefile eventually provides:

```text
make dev-up
make dev-down
make reset-db
make seed-demo
make seed-audit
make seed-load
make test
make test-integration
make test-e2e
make test-load
make security
make verify
make verify-all
make backup
make restore BACKUP=...
```

Exact definitions are owned by testing/deployment specs.

## 29. CI

CI vendor is not architectural authority.

The repo's scripts/Makefile/Maven/npm commands are canonical. A GitHub Actions workflow may call them.

Avoid implementing a second verification pipeline only in YAML.

## 30. Architectural Prohibitions

Unless a canonical spec is explicitly revised:

- NO WebFlux
- NO Kafka
- NO RabbitMQ
- NO Redis
- NO GraphQL
- NO Kubernetes
- NO service mesh
- NO Spring Cloud Gateway
- NO extra business microservices
- NO NgRx
- NO Tailwind/Bootstrap as a second UI system
- NO JPA/SQL persistence
- NO second business database
- NO duplicate recommendation engine
- NO raw string interpolation into Cypher
- NO direct persistence entities from controllers
- NO business logic in controllers
- NO cross-service mutation ownership violations
- NO service-owned schema startup migrations
- NO production secrets in Git/images
- NO floating release dependency/image tags
