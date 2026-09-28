# 4. How to run Neo4flix

This chapter explains the normal local workflow. The authoritative and most
up-to-date command reference remains [Local development](../DEVELOPMENT.md).

Run every command from the repository root: the folder where you cloned the
project and where `README.md`, `pom.xml`, and `Makefile` are located. Its exact
path depends on where you placed the clone.

## What must be installed

The supported development setup requires:

- Git;
- Java 21 with `JAVA_HOME` configured;
- Node.js 24 and npm 11;
- PowerShell 7, available as `pwsh`;
- Docker Desktop with Docker Compose; and
- GNU Make for the shortest commands.

Make is convenient but not essential because the README documents equivalent
direct commands.

## What `.env` is

An environment file contains local configuration values that should not be
hardcoded into source code. Create it from the safe template:

```powershell
Copy-Item .env.example .env
```

Open `.env` and provide valid local values for the active `NEO4J_*` and
`NEO4FLIX_*` settings. The file configures the Neo4j
credentials, JWT key pair, TOTP encryption key, allowed browser origins, and
runtime security settings. The older `JWT_PRIVATE_KEY_PATH`,
`JWT_PUBLIC_KEY_PATH`, and `TOTP_ENCRYPTION_KEY` entries are legacy placeholders
and do not configure the current authentication runtime. `DEMO_ADMIN_PASSWORD`
and `DEMO_USER_PASSWORD` are reserved placeholders that the current seed
loaders do not consume.

The real `.env` is ignored by Git. Never commit it, paste it into an issue, or
print private keys and passwords in terminal output.

### Generate the required cryptographic values

The runtime needs a matching RSA key pair and a base64-encoded 32-byte TOTP
encryption key. The following PowerShell 7 commands generate them in memory and
write their base64 representations directly into `.env` without printing the
secret values:

```powershell
$rsa = [System.Security.Cryptography.RSA]::Create()
$rsa.KeySize = 3072
$jwtPrivate = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
$jwtPublic = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
$totpKey = [Convert]::ToBase64String(
    [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)

$envFileText = Get-Content .env -Raw
$envFileText = $envFileText -replace '(?m)^NEO4FLIX_JWT_PRIVATE_KEY=.*$', "NEO4FLIX_JWT_PRIVATE_KEY=$jwtPrivate"
$envFileText = $envFileText -replace '(?m)^NEO4FLIX_JWT_PUBLIC_KEY=.*$', "NEO4FLIX_JWT_PUBLIC_KEY=$jwtPublic"
$envFileText = $envFileText -replace '(?m)^NEO4FLIX_TOTP_ENCRYPTION_KEY=.*$', "NEO4FLIX_TOTP_ENCRYPTION_KEY=$totpKey"
Set-Content -Path .env -Value $envFileText -NoNewline

$rsa.Dispose()
Remove-Variable jwtPrivate, jwtPublic, totpKey, envFileText
```

Base64 key material is passed through Compose environment variables, so the
containers do not need access to host key files. Set `NEO4J_PASSWORD`,
to a local password as well. If a Neo4j volume already exists, read the volume
warning later in this chapter before changing its password.

## Starting the complete stack

With Docker Desktop running:

```powershell
make dev-up
```

Without GNU Make:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600
```

Important options in that command are:

- `--env-file .env`: load local settings;
- `-f ...`: combine the base and development Compose files;
- `--build`: build project images when necessary;
- `-d`: run containers in the background;
- `--wait`: wait for health checks; and
- `--wait-timeout 600`: allow up to ten minutes for the first build/start.

## Opening the project

When the command succeeds, use:

| Address | Purpose |
| --- | --- |
| <http://localhost:8080/> | Neo4flix web application |
| <http://localhost:7474/> | Neo4j Browser |
| `localhost:7687` | Neo4j Bolt protocol used by drivers |
| <http://localhost:8081/actuator/health> | User Service health |
| <http://localhost:8082/actuator/health> | Movie Service health |
| <http://localhost:8083/actuator/health> | Rating Service health |
| <http://localhost:8084/actuator/health> | Recommendation Service health |

The `/actuator/health` endpoints should report `UP` when their services are
ready.

## Checking container state

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml ps
```

To inspect logs for one service:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml logs user-service
```

Replace `user-service` with `neo4j`, `movie-service`, `rating-service`,
`recommendation-service`, `database-migrator`, or `web` as needed.

## Loading deterministic audit data

The seed helper deliberately does not read `.env`. Export only the three Neo4j
connection settings into the current PowerShell process:

```powershell
$env:NEO4J_URI = 'neo4j://localhost:7687'
$env:NEO4J_USERNAME = 'neo4j'
$env:NEO4J_PASSWORD = ((Get-Content .env | Where-Object { $_ -match '^NEO4J_PASSWORD=' } | Select-Object -First 1) -replace '^NEO4J_PASSWORD=', '')

& .\scripts\seed.ps1 audit
```

This builds the database migrator and runs it in `seed-audit` mode. It loads a
small, predictable graph for testing and demonstrations.

## Stopping the project safely

```powershell
make dev-down
```

Or use the direct command:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml down
```

This removes the project containers and network but preserves the named Neo4j
data volume. Your graph data is therefore available on the next start.

## The important volume warning

Do not add `-v` to `docker compose down` unless you intentionally want to erase
the local graph database. `down -v` deletes the named Neo4j volume and its data.

Neo4j sets its initial password when a new data volume is created. Changing
`NEO4J_PASSWORD` in `.env` later does not change the password already stored in
that existing volume. If the values no longer match, Neo4j starts but its
health check reports authentication failures. Resolve this by restoring the
original password or deliberately recreating the volume after deciding that
its data can be destroyed.

## Running without the full stack

The project can verify individual layers without running the normal development
database:

```powershell
.\mvnw.cmd verify
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
```

Java integration tests use isolated Testcontainers databases, so they do not
modify the normal development graph.

## Recap

Create a private `.env`, start Docker Desktop, bring the Compose stack up, open
port 8080, and stop it with `down` when finished. Named volumes preserve data;
`down -v` destroys it.

Next: [Chapter 5 explains why the backend is divided into services](05-the-services-explained.md).
