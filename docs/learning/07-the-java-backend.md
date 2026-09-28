# 7. The Java and Spring Boot backend

The Neo4flix backend is written in Java 21 with Spring Boot. Java is the
programming language; Spring is a collection of libraries for web servers,
configuration, security, validation, transactions, and data access; Spring
Boot assembles those libraries into applications that are easy to start.

## Maven and modules

Maven downloads Java dependencies, compiles source code, runs tests, and builds
JAR files. The root [`pom.xml`](../../pom.xml) declares Java 21, Spring Boot,
and the child modules.

```text
neo4flix
├── backend
│   ├── platform-common
│   ├── user-service
│   ├── movie-service
│   ├── rating-service
│   └── recommendation-service
└── database/migrator
```

The Maven Wrapper (`mvnw`/`mvnw.cmd`) downloads the pinned Maven version, so a
global Maven installation is not required.

## How a Spring Boot service starts

Each service has a small main class. User Service uses:

```java
@SpringBootApplication(
    scanBasePackages = {"com.neo4flix.user", "com.neo4flix.platform.common"}
)
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

`@SpringBootApplication` enables Spring Boot configuration and asks Spring to
discover application classes. `SpringApplication.run` creates the application
context and starts the embedded web server.

## The common layer flow

Most backend requests move through three conceptual layers:

```text
Controller -> Application service -> Repository -> Neo4j
```

### Controller

A controller translates HTTP into Java. It reads path/query parameters or a
JSON request body, invokes application logic, and chooses an HTTP response.

### Application service

An application service contains business rules and coordinates work. For
example, registration validates a password, normalizes an email, hashes the
password, creates a user, and returns a public projection.

### Repository

A repository reads or writes persistent data. Neo4flix repositories use Spring
Data Neo4j's mapping infrastructure and `Neo4jClient` with parameterized Cypher.

The layers are responsibilities, not just folder names. Keeping HTTP details
out of repositories and database details out of controllers makes each part
easier to understand and test.

## Important Spring annotations

An **annotation** begins with `@` and adds metadata that a framework can inspect.
The annotation does not replace Java code; it tells Spring how to treat that
code.

| Annotation | Meaning in Neo4flix |
| --- | --- |
| `@SpringBootApplication` | Marks the class that starts a service |
| `@RestController` | Exposes methods as HTTP API handlers |
| `@RequestMapping` | Defines a shared URL prefix for a controller |
| `@GetMapping` | Handles an HTTP GET request |
| `@PostMapping` | Handles an HTTP POST request |
| `@PutMapping` | Handles an HTTP PUT request |
| `@DeleteMapping` | Handles an HTTP DELETE request |
| `@RequestBody` | Converts JSON request data into a Java value |
| `@PathVariable` | Reads a value from a URL segment |
| `@RequestParam` | Reads a query-string value |
| `@Valid` | Runs validation rules before the method continues |
| `@Service` | Marks application/business logic managed by Spring |
| `@Repository` | Marks database access managed by Spring |
| `@Transactional` | Runs the method in one database transaction |
| `@Configuration` | Declares a class that creates/configures Spring objects |
| `@Bean` | Adds an object created by a configuration method to Spring |
| `@Value` | Injects a configuration value |
| `@ConfigurationProperties` | Maps a group of settings to a Java type |
| `@Conditional` | Creates something only when a condition is true |
| `@AuthenticationPrincipal` | Provides the authenticated user's JWT identity |
| `@RestControllerAdvice` | Handles errors across controller methods |
| `@ExceptionHandler` | Converts a specific exception into an API response |
| `@Node` | Maps a Java type to a Neo4j node label |
| `@Id` | Marks the graph entity identifier |
| `@Property` | Maps a field to a Neo4j property |
| `@Relationship` | Maps connected graph entities |
| `@RelationshipProperties` | Maps properties stored on a relationship |
| `@TargetNode` | Marks the destination node of mapped relationship data |

You can see many of these together in
[`AuthController`](../../backend/user-service/src/main/java/com/neo4flix/user/auth/AuthController.java),
[`AuthApplicationService`](../../backend/user-service/src/main/java/com/neo4flix/user/auth/AuthApplicationService.java),
and
[`RatedRelationship`](../../backend/rating-service/src/main/java/com/neo4flix/rating/persistence/RatedRelationship.java).

## Dependency injection

Spring creates and connects application objects. A controller does not use
`new AuthApplicationService(...)`; instead it asks for dependencies through its
constructor:

```java
public AuthController(
        AuthApplicationService auth,
        RefreshCookieFactory cookies) {
    this.auth = auth;
    this.cookies = cookies;
}
```

Spring finds the `@Service` and other configured objects, builds them in the
correct order, and passes them to the constructor. This is **dependency
injection**. It makes dependencies explicit and allows tests to substitute
controlled versions.

## Records and DTOs

Java **records** are concise immutable data carriers. Neo4flix uses records for
request and response DTOs—Data Transfer Objects—and for many graph mappings.

A DTO is the public shape exchanged between layers or across HTTP. It prevents
the API from accidentally exposing a database entity containing internal or
sensitive fields.

## Transactions

A transaction groups database work into one all-or-nothing unit. If registration
created a session but failed before linking it to a user, partial data would be
dangerous. `@Transactional` tells Spring to commit the complete operation when
it succeeds or roll it back when it fails.

Read-only operations can use `@Transactional(readOnly = true)` to communicate
intent.

## Configuration

Each service has `src/main/resources/application.yml`. It contains the service
name, port, Neo4j connection, health endpoint, and application settings.
Environment variables override sensitive or deployment-specific values.

For example, services share the JWT public key, issuer, and audience so they
can validate access tokens. Only User Service receives the private signing key.

## Errors and HTTP status codes

Neo4flix maps expected failures to useful HTTP responses:

- `400 Bad Request`: malformed or invalid input;
- `401 Unauthorized`: no valid authentication;
- `403 Forbidden`: authenticated but missing permission;
- `404 Not Found`: requested resource does not exist;
- `409 Conflict`: operation conflicts with current state;
- `429 Too Many Requests`: rate limit reached; and
- `503 Service Unavailable`: a required dependency is unavailable.

Shared handling lives in
[`ApiExceptionHandler`](../../backend/platform-common/src/main/java/com/neo4flix/platform/common/web/ApiExceptionHandler.java)
and service-specific advice classes. Responses use a safe Problem Details
shape and a request ID.

## Recap

Spring Boot starts each Java service and wires its objects together. Controllers
handle HTTP, services apply business rules, repositories communicate with
Neo4j, transactions protect multi-step changes, and annotations tell Spring how
each class participates.

Next: [Chapter 8 explains the Angular frontend](08-the-angular-frontend.md).
