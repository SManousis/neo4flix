# 12. Testing the project

Neo4flix uses different kinds of tests because no single test can efficiently
prove everything. A small unit test can check a scoring formula quickly; a
browser test can prove that a person can complete a journey; a Compose smoke
test can prove that independently built containers communicate.

## The testing layers

```text
                  Browser journeys
                /                  \
          Compose and API integration
         /                           \
 Java/Testcontainers integration   Angular component tests
         \                           /
             Focused unit tests
```

Tests near the bottom are fast and precise. Tests near the top cover more of
the system but are slower and require more infrastructure.

## Java unit tests

The backend uses JUnit 5, Mockito, and AssertJ.

- **JUnit** discovers and runs tests.
- **Mockito** creates controlled substitute dependencies.
- **AssertJ** provides readable assertions.

Examples include password-policy tests, controller status tests, recommendation
weight tests, and score calculations.

Run the default Maven reactor:

```powershell
.\mvnw.cmd verify
```

`verify` compiles all Maven modules, runs tests matched by each module's Maven
configuration, and builds their artifacts. Maven's default Surefire naming
patterns do not automatically include every backend class ending in `IT`, so
this command alone is not evidence that every integration test ran.

## Neo4j integration tests

Repository logic and graph constraints need a real database. Testcontainers
starts disposable Neo4j containers for tests, applies migrations, and destroys
the containers afterward.

The repository contains integration tests for behaviors such as:

- graph constraints and indexes;
- authentication persistence;
- rating and watchlist concurrency;
- deterministic seed loading;
- Cypher queries; and
- recommendation/GDS behavior.

They do not modify the normal development volume.

The verification wrapper's integration selection currently runs the schema,
rating/watchlist concurrency, and authentication integration tests in Platform
Common, Rating Service, and User Service:

```powershell
pwsh -NoProfile -File scripts/verify.ps1 -Integration
```

Recommendation's golden-fixture and query-plan integration tests are separate
and can be selected explicitly:

```powershell
.\mvnw.cmd -pl backend/recommendation-service -am `
  '-Dtest=RecommendationGoldenFixtureIT,RecommendationQueryPlanIT' `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Read the command output and current audit status before claiming these tests
pass. An explicitly selected test that does not compile or execute is a failed
gate, not a skip.

## Angular unit and component tests

The frontend uses Vitest with a browser-like DOM environment. Tests create
components with controlled API services, trigger user actions, and inspect the
rendered result.

Run them once:

```powershell
npm.cmd --prefix frontend test -- --run
```

Also run static analysis and a production build:

```powershell
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
```

Lint catches TypeScript/template quality problems. A production build proves
that Angular can compile and bundle the application.

## Browser end-to-end tests

Playwright controls a real browser against the running Compose stack. E2E tests
cover journeys including:

- registration and login;
- catalog navigation;
- ratings;
- watchlists;
- recommendations;
- public sharing;
- 2FA; and
- credential-gated admin catalog actions.

With the configured stack running:

```powershell
npm.cmd --prefix frontend run e2e
```

An E2E failure can originate in the browser, Nginx, a service, authentication,
or the database, so logs and request IDs are important when diagnosing it.

## Compose smoke testing

The smoke script checks the live service health and representative API paths:

```powershell
pwsh -NoProfile -File scripts/smoke-compose.ps1 -EnvFile .env
```

Compose configuration also has static contract tests that do not need every
container to be running.

## Security checks

```powershell
pwsh -NoProfile -File scripts/security.ps1
```

The wrapper checks tracked secret-like paths and runs npm audit. It also runs
OWASP Dependency-Check, Gitleaks, and Trivy when those optional tools are
installed. A skipped optional scanner is not the same as a successful scan, so
the output should always be read carefully.

## Load and stress tests

The repository contains k6 scripts in [`scripts/k6`](../../scripts/k6/). These
send repeated requests, measure latency and failures, and exercise rate limits.
Load tests answer different questions from unit tests: they observe behavior
under repeated or concurrent activity rather than validating one isolated rule.

The supported profiles and interpretation notes are in
[`STRESS_TEST.md`](../audit/STRESS_TEST.md).

## Convenient wrapper commands

With the documented tools installed:

```powershell
make verify       # Maven, frontend checks, Compose configuration
make test         # focused Java and frontend test path
make verify-all   # build/start stack, smoke, E2E, security, and k6
make security     # security wrapper
```

`verify-all` requires Docker, a valid `.env`, working runtime keys, frontend
dependencies, and browser tooling. It is intentionally broader than a normal
unit-test run.

## Testing against the 01-edu audit questions

Automated tests support an audit, but they do not replace a live explanation.
Use the ordered checklist in
[`01-EDU_AUDIT_QUESTION_CHECKLIST.md`](../audit/01-EDU_AUDIT_QUESTION_CHECKLIST.md)
and the walkthrough in [`AUDIT_RUNBOOK.md`](../audit/AUDIT_RUNBOOK.md).

The evaluator should be able to see and hear:

- the application working through its main browser journeys;
- graph nodes, relationships, properties, and Cypher queries;
- each service's responsibility;
- the recommendation algorithm and one explainable result;
- authentication, 2FA, password policy, and role protection;
- error handling and useful request IDs; and
- honest limitations such as local HTTP-only operation or unavailable optional
  scanners.

Never mark a human usability question, HTTPS deployment requirement, optional
scanner, or blocked live test as passed without the required evidence. Current
results and remaining gates belong in
[`FINAL_STATUS.md`](../audit/FINAL_STATUS.md), not in this learning chapter.

## How to learn from a test

When reading an unfamiliar test:

1. Read the test name as a behavior sentence.
2. Identify the setup: which inputs and substitutes are created?
3. Identify the action: which method or HTTP request is executed?
4. Read the assertions: what must be true afterward?
5. Open the production class being tested.
6. Change nothing until you can explain why the test should pass.

Tests are executable examples of the project rules, and they are often the
fastest path to understanding a feature.

## Recap

Unit tests prove focused rules, Testcontainers proves real graph behavior,
Vitest checks Angular logic and rendering, Playwright checks browser journeys,
Compose tests check runtime wiring, security tools scan risks, and k6 observes
load behavior. The audit combines this evidence with a live demonstration.

You have reached the end of the ordered guide. Keep the
[glossary](glossary.md) nearby and revisit chapters while exploring the code.
