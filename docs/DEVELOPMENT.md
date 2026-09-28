# Local development

Run commands from the repository root. This is the detailed companion to the
[project README](../README.md): it covers clean-checkout setup, authentication
verification, Compose lifecycle, seed data, and the checks used by CI.

For a first run, use **Required tools**, **Clean checkout**, and **Start and check
the stack**. The later sections are targeted verification and operational notes.

## Required tools

- Git and GNU Make (`make` on PATH).
- Java 21 JDK, with `JAVA_HOME` set to the JDK directory and its `bin` on PATH.
  The committed Maven Wrapper downloads Maven 3.9.11; no global Maven is needed.
- Node.js 24 LTS and npm (the frontend lockfile was generated with npm 11.16.0).
- PowerShell 7 (`pwsh` on PATH), on Windows, Linux, or macOS.
- Docker with Compose v2.17 or newer. Start Docker Desktop or the Docker engine
  before bringing up the stack. Configuration validation alone needs no engine.

Dependency installation and initial image builds need network access. On Windows,
run these commands in PowerShell 7 with GNU Make available as `make`.

## Clean checkout

Create the ignored local environment file using PowerShell:

```powershell
Copy-Item .env.example .env
```

Replace the `change-me` values in `.env` with local, non-production values before
startup. Keep `NEO4J_USERNAME=neo4j`. The example's `JWT_PRIVATE_KEY_PATH`,
`JWT_PUBLIC_KEY_PATH`, and `TOTP_ENCRYPTION_KEY` are legacy placeholders; they do
not configure Batch 2 authentication. See the auth configuration requirements below.
Never commit `.env` or private keys. Changing a password in `.env` does not rotate
credentials in an existing Neo4j data volume.

Run the same acceptance path used by CI:

```powershell
make verify
```

This runs Maven `verify`, then frontend `npm ci`, lint, tests in run-once mode,
and the production build, then validates `infra/compose.yml` with `.env.example`.
It stops on the first failing command. It installs dependencies and writes build
outputs and starts isolated Testcontainers Neo4j instances for schema, auth HTTP,
refresh/challenge replay, and relationship concurrency tests. It does not modify
the development graph. The example environment makes Compose configuration
validation independent of local credentials.

To preview commands without running them, or run only Java/frontend tests:

```powershell
pwsh -NoProfile -File scripts/verify.ps1 -WhatIf
make test
```

`make test` includes `npm ci` so it also works before the first frontend install.
The PowerShell wrapper can be invoked directly with `-TestOnly` for the same path.
Maintainers can run its behavioral tests with Pester installed:

```powershell
Invoke-Pester scripts/verify.Tests.ps1
```

## Authentication verification and runtime prerequisites

Run the live auth and graph acceptance path with Docker running:

```powershell
pwsh -NoProfile -File scripts/verify.ps1 -Integration
```

This selects `Neo4jSchemaIntegrationTest`, `*ConcurrencyIT`,
`AuthNeo4jIntegrationIT`, and `AuthProductionContextIT`. The auth HTTP suite starts
the actual User Service context on a random port against an isolated Neo4j graph,
generates ephemeral RSA and AES keys in memory, and cleans up its containers.
No `.env` or existing database credentials are needed for these tests.

For a configured runtime, User Service requires `NEO4FLIX_JWT_PRIVATE_KEY`
(PKCS#8 RSA PEM, DER base64, or a readable key-file path),
`NEO4FLIX_JWT_PUBLIC_KEY` (the matching public key), and
`NEO4FLIX_TOTP_ENCRYPTION_KEY` (base64 of 32 random bytes). Every protected service
requires the same public key, issuer, and audience; the signing private key and
TOTP encryption key belong only to User Service. Configure
`NEO4FLIX_ALLOWED_ORIGINS` for the intended frontend origin. Keep keys and any
temporary `.env` local and ignored; never print key material or tokens.

The committed Compose topology passes the canonical `NEO4FLIX_*` auth settings
into the business services: User Service receives the signing private key and
TOTP encryption key, while every protected service receives the matching public
key, issuer, audience, and allowed origins. Key values may be PEM/base64
material or readable file paths. Runtime Compose auth acceptance still requires
valid local values and a live smoke run; see `docs/audit/batch-2-verification.md`.

The Vitest storage/interceptor/profile checks remain component evidence; the
repository's pinned Playwright contract covers local real-browser storage,
reload, navigation, and logout behavior.

The repository now includes a pinned Playwright contract in `frontend/e2e`.
With an auth-configured Compose stack running, execute:

```powershell
npm.cmd --prefix frontend run e2e
```

The suite creates and removes a disposable user, verifies that access-token
state is absent from `localStorage` and `sessionStorage`, exercises login,
reload, guarded profile navigation, and logout, and accepts
`NEO4FLIX_E2E_BASE_URL` for a staging URL.

## Start and check the stack

```powershell
make dev-up
pwsh -NoProfile -File scripts/smoke-compose.ps1
```

`make dev-up` builds and starts the base plus development Compose files and waits
up to 600 seconds for readiness. The smoke check verifies the migrator exited
successfully, all four service health responses report `UP`, and web returns 200.

| Endpoint | Purpose |
| --- | --- |
| <http://localhost:8080/> | Angular app through Nginx |
| <http://localhost:8081/actuator/health> | User Service health |
| <http://localhost:8082/actuator/health> | Movie Service health |
| <http://localhost:8083/actuator/health> | Rating Service health |
| <http://localhost:8084/actuator/health> | Recommendation Service health |
| <http://localhost:7474/> | Neo4j Browser; Bolt is `localhost:7687` |

For an individual health check:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

The base topology publishes only web. `infra/compose.dev.yml` adds localhost-only
backend and Neo4j ports for these checks. Both local lifecycle commands use:
`docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml`.

The one-shot `database-migrator` first proves `RETURN gds.version();`, then in
`migrate` mode applies the versioned schema migrations and verifies both their
history and the expected Neo4j schema. `verify` and every explicit `seed-*`
mode require that same migrated, valid schema before completing; they do not
apply migrations themselves. Business services start only after the migrator
succeeds. Development currently downloads GDS at Neo4j startup; deterministic
GDS packaging is required for release/audit. See [Neo4j bootstrap details](../infra/neo4j/README.md).

## Explicit seed data

Seed data is separate from schema migrations and is never loaded by application
startup or Compose. First run the migrator in `migrate` mode against the target
test/development graph; seed modes verify that existing migration history and
schema before they write fixture data. Then export its Neo4j connection values
in the shell that invokes one of:

```powershell
make seed-demo
make seed-audit
make seed-load
```

The commands require `NEO4J_URI`, `NEO4J_USERNAME`, and `NEO4J_PASSWORD`; they
do not read `.env`, publish ports, or start containers. `seed-audit` loads the
fixed, idempotent audit fixture (three users and ten ratings). `seed-demo` and
`seed-load` create only deterministic, credential-free scaffolding. To preview
without building or changing graph data, use:

```powershell
pwsh -NoProfile -File scripts/seed.ps1 -WhatIf audit
```

`make reset-db` is intentionally deferred to the deployment batch. No destructive
reset target is available in this batch.

## Authentication CORS and trusted proxy identity

`NEO4FLIX_ALLOWED_ORIGINS` configures the explicit HTTP(S) origin allowlist used
by Spring Security CORS and the User Service refresh/logout origin checks.
The defaults are `http://localhost:4200,http://localhost:8080`; credentialed
requests never use a wildcard origin.

For rate limiting behind Nginx, set `NEO4FLIX_AUTH_TRUSTED_PROXIES` in the User
Service environment to the exact Nginx peer IP or a dedicated, restricted proxy
CIDR. The default is empty, so direct requests cannot select their rate bucket
through headers. The repository Nginx configuration overwrites `X-Real-IP` with
its socket peer; only a configured trusted peer may supply that identity.
`X-Forwarded-For` is ignored. Keep `server.forward-headers-strategy=none` so the
socket peer remains available for this trust check. Missing/malformed client
headers fall back to that peer. Do not trust a shared network containing
untrusted clients or enable general forwarding-header rewriting.

Compose currently requires explicit environment overrides for this trust setting;
it remains subject to the documented JWT/TOTP key-wiring and auth acceptance blockers.

## Stop the stack

```powershell
make dev-down
```

This removes the development containers and network while preserving the named
Neo4j data volume for the next start. The target never requests volume deletion.
