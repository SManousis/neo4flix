# Batch 0 Repository Bootstrap and Reproducible Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a reproducible Neo4flix baseline with a Maven reactor, four healthy Spring MVC services, Angular shell, Compose topology with Neo4j/GDS, and a single verification entry point.

**Architecture:** Build the repository manually around a root Maven reactor and a backend aggregator so service boundaries and pinned dependencies remain explicit. Put only HTTP infrastructure in `platform-common`; each of the four business services is a separate Spring Boot executable. Use Angular's official standalone scaffold for the frontend, and Compose to model the production-like startup order: Neo4j/GDS, one-shot migrator placeholder, services, then Nginx web.

**Tech Stack:** Java 21; Spring Boot 4.1.1; Spring MVC; Spring Security; Spring Data Neo4j; Maven Wrapper; Angular 22.1.5; Angular Material 22.1.5; TypeScript 6.0.x; SCSS; Vitest; Docker Compose; Neo4j Community 2026.07.1 with GDS 2026.07; Nginx.

**Spec:** `docs/superpowers/specs/2026-09-07-batch-0-bootstrap-design.md`

## Global Constraints

- Implement exactly four business services: `user-service`, `movie-service`, `rating-service`, and `recommendation-service`; `database-migrator` is infrastructure, not a business service.
- Use Java 21, Spring Boot 4.1.1, Spring MVC, Maven Wrapper, Angular 22.1.5, Angular Material 22.1.5, TypeScript 6.0.x, Signals/services, SCSS, Vitest, Docker Compose, Neo4j Community 2026.07.1, and GDS 2026.07.
- Pin every explicit dependency and container image tag; never use a floating `latest` tag in a release path.
- Share one configured Neo4j graph, but do not add graph entities, schema migration execution, business use cases, or future-batch HTTP resources in this batch.
- `platform-common` may contain only cross-cutting infrastructure; it must not contain User/Movie/Rating entities, repositories, recommendation logic, or a shared mutable domain model.
- Use parameterized Neo4j access only when graph queries are introduced in a later batch; no controller may build Cypher or own business logic.
- Do not introduce WebFlux, Kafka, RabbitMQ, Redis, GraphQL, Kubernetes, Spring Cloud Gateway, a service mesh, JPA/SQL persistence, NgRx, Tailwind, Bootstrap, or another business service.
- Do not commit `.env`, private keys, TOTP keys, tokens, passwords other than explicit non-production placeholders, or generated build output.
- Services must not perform schema migrations at startup. The one-shot `database-migrator` placeholder must check GDS and finish successfully before services start.
- Backend error responses use `ProblemDetail`; errors and logs must not disclose exception stack traces, Cypher, credentials, tokens, passwords, or TOTP values.

---

## Target file structure

| Path | Responsibility |
|---|---|
| `pom.xml`, `mvnw`, `.mvn/wrapper/*` | Root Java build, reproducible Maven wrapper, reactor module list. |
| `backend/pom.xml` | Backend aggregator and shared Java version/plugin management. |
| `backend/platform-common/**` | Request-ID and safe Problem Detail infrastructure only. |
| `backend/*-service/**` | One boot application, local configuration, and focused baseline tests per business service. |
| `frontend/**` | Angular standalone/Material application shell, locked packages, Vitest baseline. |
| `infra/compose.yml`, `infra/compose.dev.yml` | Pinned local runtime topology and development overrides. |
| `infra/nginx/default.conf` | SPA hosting and canonical `/api/v1` reverse-proxy routes. |
| `database/migrator/check-gds.sh` | One-shot readiness/GDS check; intentionally not a schema migration engine. |
| `.env.example`, `.gitignore`, `.editorconfig` | Safe configuration contract and repository hygiene. |
| `Makefile`, `scripts/*`, `.github/workflows/verify.yml` | Canonical local verification and CI wrapper path. |
| `docs/DEVELOPMENT.md` | Exact bootstrap, test, and runtime commands for a clean checkout. |

## Task 1: Establish reproducible repository hygiene and Maven reactor

**Files:**

- Create: `.editorconfig`
- Create: `.gitignore`
- Create: `.env.example`
- Create: `pom.xml`
- Create: `backend/pom.xml`
- Create: `backend/platform-common/pom.xml`
- Create: `backend/user-service/pom.xml`
- Create: `backend/movie-service/pom.xml`
- Create: `backend/rating-service/pom.xml`
- Create: `backend/recommendation-service/pom.xml`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`

**Interfaces:**

- Consumes: canonical Batch 0 layout and pinned technology constraints.
- Produces: a root `pom.xml` with `<modules><module>backend</module></modules>` and a backend `pom.xml` whose modules are `platform-common`, `user-service`, `movie-service`, `rating-service`, and `recommendation-service`.

- [ ] **Step 1: Write the reactor-shape test before module implementation**

Create `scripts/test-reactor-layout.ps1` that parses the root and backend POM XML and fails unless the exact expected module names occur once and `backend` uses `packaging` `pom`.

```powershell
[xml]$root = Get-Content -Raw "$PSScriptRoot/../pom.xml"
if ($root.project.modules.module -ne 'backend') { throw 'Root reactor must contain backend only.' }
[xml]$backend = Get-Content -Raw "$PSScriptRoot/../backend/pom.xml"
$expected = @('platform-common','user-service','movie-service','rating-service','recommendation-service')
if (@($backend.project.modules.module) -join ',' -ne $expected -join ',') { throw 'Backend modules differ from the four-service baseline.' }
```

- [ ] **Step 2: Run the shape test and confirm it fails because the POM files do not yet exist**

Run: `pwsh -File scripts/test-reactor-layout.ps1`

Expected: failure reporting that `pom.xml` is absent.

- [ ] **Step 3: Generate a Maven Wrapper pinned to Maven 3.9.11**

Run from the repository root:

```powershell
mvn -N wrapper:wrapper -Dmaven=3.9.11
```

Verify `.mvn/wrapper/maven-wrapper.properties` contains `apache-maven/3.9.11` and make no manual changes to wrapper binaries/scripts generated by Maven.

- [ ] **Step 4: Create the root and backend aggregators**

Use `com.neo4flix:neo4flix` as the root group/artifact and define these properties in the root POM:

```xml
<java.version>21</java.version>
<spring-boot.version>4.1.1</spring-boot.version>
<maven.compiler.release>21</maven.compiler.release>
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

Import `org.springframework.boot:spring-boot-dependencies:${spring-boot.version}` in root dependency management. Set each parent relationship explicitly, use `packaging` `pom` for aggregators, and configure `maven-compiler-plugin` with `<release>${maven.compiler.release}</release>`. Do not add domain dependencies to the root POM.

- [ ] **Step 5: Create service/module POM contracts**

Give every business service a JAR POM depending on `com.neo4flix:platform-common:${project.version}`, `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`, and the Neo4j starter required for later shared-graph connectivity. Add `spring-boot-starter-test`, AssertJ, and Mockito in test scope. Add `spring-boot-maven-plugin` in every executable service only; `platform-common` remains a library.

- [ ] **Step 6: Add safe environment and ignore contracts**

Make `.env.example` contain exactly non-secret placeholders for `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH`, `TOTP_ENCRYPTION_KEY`, `FRONTEND_BASE_URL`, `DEMO_ADMIN_PASSWORD`, and `DEMO_USER_PASSWORD`. Ignore `.env`, key material, `target/`, `node_modules/`, Angular cache, Playwright output, coverage, and runtime data. Configure `.editorconfig` for UTF-8, LF, final newline, 2-space YAML/JSON/SCSS/TypeScript indentation, and 4-space Java indentation.

- [ ] **Step 7: Run the reactor-shape test and Maven reactor validation**

Run:

```powershell
pwsh -File scripts/test-reactor-layout.ps1
.\mvnw.cmd -q -DskipTests validate
```

Expected: the shape script and Maven validation both pass.

- [ ] **Step 8: Commit the reactor/hygiene baseline**

```powershell
git add .editorconfig .gitignore .env.example pom.xml backend .mvn mvnw mvnw.cmd scripts/test-reactor-layout.ps1
git commit -m "build: bootstrap Maven reactor"
```

## Task 2: Build and test the cross-cutting HTTP baseline

**Files:**

- Create: `backend/platform-common/src/main/java/com/neo4flix/platform/common/web/RequestId.java`
- Create: `backend/platform-common/src/main/java/com/neo4flix/platform/common/web/RequestIdFilter.java`
- Create: `backend/platform-common/src/main/java/com/neo4flix/platform/common/web/ProblemDetails.java`
- Create: `backend/platform-common/src/main/java/com/neo4flix/platform/common/web/ApiExceptionHandler.java`
- Create: `backend/platform-common/src/test/java/com/neo4flix/platform/common/web/RequestIdFilterTest.java`
- Create: `backend/platform-common/src/test/java/com/neo4flix/platform/common/web/ApiExceptionHandlerTest.java`

**Interfaces:**

- Consumes: Java 21, Spring MVC, Spring `ProblemDetail`, and the `platform-common` POM from Task 1.
- Produces: `RequestId.resolve(String candidate): String`, `RequestIdFilter` as a `OncePerRequestFilter`, `ProblemDetails.badRequest(String code, String requestId): ProblemDetail`, and an exception handler suitable for service imports.

- [ ] **Step 1: Write failing request-ID tests**

Test these observable contracts with `MockHttpServletRequest`/`MockHttpServletResponse`:

```java
assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-42");
assertThat(RequestId.resolve("bad id with spaces")).matches("[0-9a-f-]{36}");
assertThat(RequestId.resolve(null)).matches("[0-9a-f-]{36}");
```

Use a valid header of at most 64 characters matching `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`. Assert the filter clears `MDC.get("requestId")` after the chain returns.

- [ ] **Step 2: Write the failing Problem Detail tests**

Assert `ProblemDetails.badRequest("VALIDATION_FAILED", "request-42")` has status 400, title `Bad Request`, `code` property `VALIDATION_FAILED`, `traceId` property `request-42`, and no `detail` property carrying an exception message. Assert the exception handler returns this safe shape for an `IllegalArgumentException`.

- [ ] **Step 3: Run the focused tests and confirm compilation fails**

Run:

```powershell
.\mvnw.cmd -pl backend/platform-common -Dtest=RequestIdFilterTest,ApiExceptionHandlerTest test
```

Expected: compilation failure because the classes under test do not exist.

- [ ] **Step 4: Implement the smallest safe HTTP infrastructure**

Implement `RequestId.resolve` to preserve only valid IDs and otherwise return `UUID.randomUUID().toString()`. In `RequestIdFilter`, place the ID in MDC, response header, and request attribute `neo4flix.requestId`; call the chain in `try`; remove the MDC key in `finally`. Build `ProblemDetail` with only status/title and the two extension properties. Implement `ApiExceptionHandler` with `@RestControllerAdvice` and a single `IllegalArgumentException` mapping that obtains the request ID from the request attribute, falling back to `RequestId.resolve(null)`.

- [ ] **Step 5: Run focused and module tests**

Run:

```powershell
.\mvnw.cmd -pl backend/platform-common -Dtest=RequestIdFilterTest,ApiExceptionHandlerTest test
.\mvnw.cmd -pl backend/platform-common test
```

Expected: all focused and module tests pass.

- [ ] **Step 6: Commit the cross-cutting baseline**

```powershell
git add backend/platform-common
git commit -m "feat: add HTTP request and error baseline"
```

## Task 3: Create four independent healthy service applications

**Files:**

- Create: `backend/user-service/src/main/java/com/neo4flix/user/UserServiceApplication.java`
- Create: `backend/movie-service/src/main/java/com/neo4flix/movie/MovieServiceApplication.java`
- Create: `backend/rating-service/src/main/java/com/neo4flix/rating/RatingServiceApplication.java`
- Create: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/RecommendationServiceApplication.java`
- Create: `backend/user-service/src/main/resources/application.yml`
- Create: `backend/movie-service/src/main/resources/application.yml`
- Create: `backend/rating-service/src/main/resources/application.yml`
- Create: `backend/recommendation-service/src/main/resources/application.yml`
- Create: `backend/user-service/src/test/java/com/neo4flix/user/UserServiceApplicationTest.java`
- Create: `backend/movie-service/src/test/java/com/neo4flix/movie/MovieServiceApplicationTest.java`
- Create: `backend/rating-service/src/test/java/com/neo4flix/rating/RatingServiceApplicationTest.java`
- Create: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/RecommendationServiceApplicationTest.java`

**Interfaces:**

- Consumes: Task 2 `RequestIdFilter` and `ApiExceptionHandler` through the `platform-common` dependency.
- Produces: four independently executable applications with `GET /actuator/health` available for Compose health checks, `spring.application.name` values matching the canonical service names, and ports 8081 through 8084.

- [ ] **Step 1: Write one failing context/health test per service**

For each service, use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"management.health.neo4j.enabled=false"})` and `TestRestTemplate`. Assert:

```java
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
assertThat(response.getBody()).contains("UP");
assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
```

The test property only prevents an external graph dependency in a focused baseline test; it does not disable the real Compose runtime health indicator.

- [ ] **Step 2: Run the four tests and confirm they fail before application classes exist**

Run:

```powershell
.\mvnw.cmd -pl backend/user-service,backend/movie-service,backend/rating-service,backend/recommendation-service test
```

Expected: test compilation fails because application classes/configuration are absent.

- [ ] **Step 3: Implement minimal application entry points and configuration**

Each application class contains only:

```java
@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

Substitute the corresponding service class/name. Each `application.yml` must set the canonical application name, the distinct `server.port`, `spring.neo4j.uri: ${NEO4J_URI:bolt://localhost:7687}`, username/password environment placeholders, and expose only `health` under `management.endpoints.web.exposure.include`. Keep Neo4j health enabled by default. Import the Task 2 advice/filter through component scanning; do not create controller endpoints or domain packages.

- [ ] **Step 4: Run focused service tests and full Java verification**

Run:

```powershell
.\mvnw.cmd -pl backend/user-service,backend/movie-service,backend/rating-service,backend/recommendation-service test
.\mvnw.cmd verify
```

Expected: four focused context/health tests and the reactor verification pass.

- [ ] **Step 5: Commit the four-service baseline**

```powershell
git add backend/user-service backend/movie-service backend/rating-service backend/recommendation-service
git commit -m "feat: add four service health baselines"
```

## Task 4: Scaffold the Angular standalone and Material baseline

**Files:**

- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/angular.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/app/app.config.ts`
- Create: `frontend/src/app/app.routes.ts`
- Create: `frontend/src/app/app.component.ts`
- Create: `frontend/src/app/app.component.html`
- Create: `frontend/src/app/app.component.scss`
- Create: `frontend/src/app/app.component.spec.ts`
- Create: `frontend/src/styles.scss`

**Interfaces:**

- Consumes: Angular 22.1.5, Angular Material 22.1.5, TypeScript 6.0.x, and the `/api/v1` browser contract reserved by the architecture.
- Produces: an Angular standalone root component that renders `Neo4flix`, an empty route list, Material theming, a committed lockfile, and `npm` scripts `lint`, `test`, and `build`.

- [ ] **Step 1: Generate the exact Angular baseline**

From the repository root, run:

```powershell
npx @angular/cli@22.1.5 new frontend --directory frontend --style scss --standalone --routing --ssr=false --skip-git --package-manager npm --skip-tests=false
Set-Location frontend
npm install @angular/material@22.1.5 @angular/cdk@22.1.5 @angular/animations@22.1.5
npm install --save-dev angular-eslint@22.1.0 eslint@9.35.0
```

Set every `@angular/*` dependency and TypeScript dependency in `package.json` to the canonical exact version rather than a range, then regenerate and commit `package-lock.json` with `npm install`.

- [ ] **Step 2: Write the failing root-component test for the product shell**

Replace the generated app test with a test that creates `AppComponent` and asserts the rendered text includes `Neo4flix` and the primary landmark exists:

```typescript
expect(fixture.nativeElement.querySelector('main')).not.toBeNull();
expect(fixture.nativeElement.textContent).toContain('Neo4flix');
```

- [ ] **Step 3: Run the focused test and confirm it fails against the generated starter copy**

Run: `npm test -- --run`

Expected: failure because the generated starter copy does not render the Neo4flix shell.

- [ ] **Step 4: Implement the minimal shell and Material theme**

Use an empty `Routes` array in `app.routes.ts`. Render a `mat-toolbar` branding Neo4flix and a `<main>` element with a concise baseline message. Add Angular Material animation providers through `app.config.ts`; use a minimal Material theme in `styles.scss`. Do not create auth, catalog, rating, watchlist, recommendation, share, profile, or admin screens in this batch.

- [ ] **Step 5: Configure lint and run frontend verification**

Configure `angular-eslint` so `npm run lint` checks every `frontend/src/**/*.ts` file and fails on unhandled errors. Do not add a second styling framework or state library. Run:

Run:

```powershell
npm ci
npm run lint
npm test -- --run
npm run build
```

Expected: install, lint, Vitest suite, and production build all pass.

- [ ] **Step 6: Commit the frontend baseline**

```powershell
git add frontend
git commit -m "feat: add Angular application baseline"
```

## Task 5: Model the pinned Docker topology and migrator readiness gate

**Files:**

- Create: `infra/compose.yml`
- Create: `infra/compose.dev.yml`
- Create: `infra/nginx/default.conf`
- Create: `infra/neo4j/README.md`
- Create: `frontend/Dockerfile`
- Create: `database/migrator/check-gds.sh`
- Create: `scripts/test-compose-config.ps1`
- Create: `scripts/smoke-compose.ps1`

**Interfaces:**

- Consumes: four service JARs from Task 3, frontend image from Task 4, `.env.example` from Task 1, and canonical service ports/routes.
- Produces: `neo4j`, `database-migrator`, `user-service`, `movie-service`, `rating-service`, `recommendation-service`, and `web` Compose services; a `check-gds.sh` process that exits 0 only after `RETURN gds.version()` succeeds.

- [ ] **Step 1: Write a failing Compose-shape test**

Create `scripts/test-compose-config.ps1` to parse `docker compose --env-file .env.example -f infra/compose.yml config --format json` and assert the exact service names, Neo4j image `neo4j:2026.07.1-community`, no image contains `:latest`, and every business service depends on `database-migrator` with `condition: service_completed_successfully`.

- [ ] **Step 2: Run the Compose-shape test and confirm it fails because Compose does not exist**

Run: `pwsh -File scripts/test-compose-config.ps1`

Expected: failure stating that `infra/compose.yml` is missing.

- [ ] **Step 3: Implement the Neo4j/GDS and migrator services**

Define Neo4j with a named persistent volume, localhost-only Browser/Bolt development bindings, an explicit health check, environment-driven credentials, and the documented development GDS plugin convenience list `NEO4J_PLUGINS=["graph-data-science"]`. Add `database-migrator` using the same pinned Neo4j image, mount `database/migrator/check-gds.sh` read-only, wait for Neo4j health, execute the script, and exit. The script must poll `cypher-shell` with bounded attempts and execute exactly:

```cypher
RETURN gds.version();
```

It must print only readiness status and never echo credentials. Its header must state it is a Batch 0 readiness placeholder; it must not claim to apply schema migrations.

- [ ] **Step 4: Build the web image, then implement service/web dependencies and Nginx routes**

Create `frontend/Dockerfile` as a two-stage image pinned to `node:24.15.0-alpine3.22` for building and `nginxinc/nginx-unprivileged:1.28.0-alpine` for runtime. The build stage runs `npm ci` and `npm run build`; the runtime stage copies only Angular's browser output and `infra/nginx/default.conf`, runs unprivileged, and listens on 8080. Build the four service modules as named Compose services mapped to ports 8081–8084 for local development. Set their Neo4j settings through environment values and health checks against `/actuator/health`; do not publish those ports in a production override. Add `web` on 8080 with `infra/nginx/default.conf` serving the Angular SPA and separately routing `/api/v1/auth/` and `/api/v1/users/` to User, `/api/v1/movies/` and `/api/v1/genres/` to Movie, `/api/v1/ratings/` to Rating, and `/api/v1/recommendations/`, `/api/v1/recommendation-shares/`, and `/api/v1/shares/` to Recommendation. Forward or create `X-Request-Id`. Keep `compose.dev.yml` limited to local port bindings and development-safe values.

- [ ] **Step 5: Write and run the runtime smoke script**

Create `scripts/smoke-compose.ps1` to:

```powershell
docker compose --env-file .env -f infra/compose.yml ps --format json
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8082/actuator/health
Invoke-RestMethod http://localhost:8083/actuator/health
Invoke-RestMethod http://localhost:8084/actuator/health
docker compose --env-file .env -f infra/compose.yml logs database-migrator
```

Fail unless each health response reports `UP` and migrator logs contain a successful GDS check. Copy `.env.example` to ignored `.env`, replace only placeholders with local non-production values, run `pwsh -File scripts/test-compose-config.ps1`, then run the smoke script after `docker compose --env-file .env -f infra/compose.yml up --build -d`.

- [ ] **Step 6: Commit topology and runtime checks**

```powershell
git add infra database/migrator scripts/test-compose-config.ps1 scripts/smoke-compose.ps1
git commit -m "infra: add baseline Compose topology"
```

## Task 6: Provide one canonical verification path and developer documentation

**Files:**

- Create: `Makefile`
- Create: `scripts/verify.ps1`
- Create: `.github/workflows/verify.yml`
- Create: `docs/DEVELOPMENT.md`
- Modify: `README.md`

**Interfaces:**

- Consumes: Maven wrapper from Task 1, service tests from Tasks 2–3, frontend scripts from Task 4, and Compose validation from Task 5.
- Produces: `make test`, `make dev-up`, `make dev-down`, and `make verify`; CI invokes `make verify` rather than duplicating its steps.

- [ ] **Step 1: Write the failing verification-wrapper test**

Create a Pester test at `scripts/verify.Tests.ps1` that invokes `scripts/verify.ps1 -WhatIf` and asserts its command list includes Maven verification, `npm ci`, Angular lint/test/build, and `docker compose ... config`. The `-WhatIf` mode must print commands without changing state.

- [ ] **Step 2: Run the wrapper test and confirm it fails before the script exists**

Run: `Invoke-Pester scripts/verify.Tests.ps1`

Expected: failure because `scripts/verify.ps1` is absent.

- [ ] **Step 3: Implement the verification script and Make targets**

Implement `scripts/verify.ps1` with `-WhatIf` and normal modes. In normal mode, it must execute this order and stop on the first failure:

```powershell
.\mvnw.cmd verify
Push-Location frontend; npm ci; npm run lint; npm test -- --run; npm run build; Pop-Location
docker compose --env-file .env.example -f infra/compose.yml config
```

Set `make verify` to call the script. Set `make test` to run the Java and frontend test commands without Compose validation. Set `make dev-up` and `make dev-down` to use the exact `infra/compose.yml` command and never run a destructive volume deletion. Avoid targets for later-batch features such as seeds, reset, backup, restore, E2E, load, or security scans.

- [ ] **Step 4: Implement the CI wrapper**

Add `.github/workflows/verify.yml` triggered by pull requests and pushes. Pin the Java 21 and Node 24 setup actions by full commit SHA, cache Maven/npm safely, install Docker Compose if the runner does not already provide it, and run only `make verify`. Do not reproduce the verification commands in YAML.

- [ ] **Step 5: Document exact clean-checkout commands**

Add `docs/DEVELOPMENT.md` covering required tools, `.env` creation from `.env.example`, `make verify`, `make dev-up`, endpoint health checks, `make dev-down`, and the fact that `database-migrator` is a GDS readiness placeholder until Batch 1. Add a short README link to this document without changing README's canonical-authority guidance.

- [ ] **Step 6: Run wrapper tests and the non-mutating acceptance path**

Run:

```powershell
Invoke-Pester scripts/verify.Tests.ps1
pwsh -File scripts/verify.ps1 -WhatIf
make verify
```

Expected: Pester and `-WhatIf` pass; `make verify` runs the Java, frontend, and Compose configuration acceptance checks successfully.

- [ ] **Step 7: Commit verification/documentation**

```powershell
git add Makefile scripts/verify.ps1 scripts/verify.Tests.ps1 .github/workflows/verify.yml docs/DEVELOPMENT.md README.md
git commit -m "build: add baseline verification workflow"
```

## Task 7: Execute fresh batch acceptance and capture evidence

**Files:**

- Create: `docs/audit/batch-0-verification.md`

**Interfaces:**

- Consumes: all completed Batch 0 deliverables and their exact commands.
- Produces: honest fresh evidence for the Batch 0 human checkpoint; no master-plan status update occurs until every gate passes.

- [ ] **Step 1: Check for forbidden implementation scope before verification**

Run:

```powershell
rg -n "spring-boot-starter-webflux|kafka|rabbitmq|redis|graphql|kubernetes|spring-cloud|spring-data-jpa|ngrx" pom.xml backend frontend infra
rg -n "latest" infra frontend backend pom.xml
```

Expected: no prohibited framework/dependency matches and no floating image tag. Investigate every non-empty result before proceeding.

- [ ] **Step 2: Run fresh build and frontend acceptance checks**

Run:

```powershell
.\mvnw.cmd verify
Push-Location frontend; npm ci; npm run lint; npm test -- --run; npm run build; Pop-Location
docker compose --env-file .env.example -f infra/compose.yml config
```

Expected: all commands return exit code 0.

- [ ] **Step 3: Run the clean runtime/GDS acceptance check**

With the ignored local `.env` present, run:

```powershell
docker compose --env-file .env -f infra/compose.yml up --build -d
pwsh -File scripts/smoke-compose.ps1
docker compose --env-file .env -f infra/compose.yml down
```

Expected: Neo4j/GDS starts, the migrator succeeds, all four services are healthy, and the stack stops without deleting its named Neo4j volume.

- [ ] **Step 4: Check repository hygiene and document exact results**

Run:

```powershell
git status --short
git ls-files .env '*.pem' '*.key' '*token*'
git log --oneline 72d285c..HEAD
```

Create `docs/audit/batch-0-verification.md` recording command date/time, exit status, service health outputs, GDS version, known environment limitations, and the three Git command outputs. Never copy passwords, tokens, or full environment files into the evidence.

- [ ] **Step 5: Perform the required final review and commit evidence**

Request a spec-compliance review and a code-quality review against this plan. Resolve every identified defect, rerun the affected checks, and obtain re-review approval. Then commit the evidence:

```powershell
git add docs/audit/batch-0-verification.md
git commit -m "docs: record batch 0 verification"
```

- [ ] **Step 6: Update Batch 0 status only after all acceptance evidence and reviews pass**

Change only `## Batch 0` status in `docs/reference/00_MASTER_EXECUTION_PLAN.md` from `[ ]` to `[x]`. Do not alter any later batch status. Commit this change separately:

```powershell
git add docs/reference/00_MASTER_EXECUTION_PLAN.md
git commit -m "docs: mark batch 0 complete"
```

## Final acceptance commands

```powershell
.\mvnw.cmd verify
Push-Location frontend; npm ci; npm run lint; npm test -- --run; npm run build; Pop-Location
docker compose --env-file .env.example -f infra/compose.yml config
docker compose --env-file .env -f infra/compose.yml up --build -d
pwsh -File scripts/smoke-compose.ps1
docker compose --env-file .env -f infra/compose.yml down
git status --short
git log --oneline 72d285c..HEAD
```

## Plan self-review

- **Coverage:** Tasks 1–3 cover the root reactor, minimal common module, four service skeletons, request-ID/Problem Detail, Actuator health, Java 21, and pinned Spring baseline. Task 4 covers the Angular/Material/SCSS/Vitest baseline and lockfile. Task 5 covers Neo4j/GDS, migrator placeholder, Compose startup order, Nginx, and service topology. Task 6 provides the canonical Make/CI/documentation route. Task 7 covers acceptance, runtime, hygiene, evidence, review, and the status gate.
- **Scope:** The plan deliberately excludes Batch 1 graph migrations and all later product behavior. The GDS-check container is named and documented as a readiness placeholder, not a schema migrator.
- **Contracts:** Service names, ports, request-ID header, health endpoint, `ProblemDetail` extensions, Maven modules, and Compose dependencies are defined before their consumers.
- **Placeholder scan:** No deferred implementation marker is present. Every task has exact files, commands, expected observations, and either concrete test assertions or a verifiable configuration contract.
