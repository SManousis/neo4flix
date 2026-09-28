# Batch 1 verification — 2026-09-10

Acceptance status: **PASSING — fresh clean-volume live acceptance completed.**
Batch 1 remains `[ ]` pending independent Task 6 and whole-branch review.

## Final fix wave — current audit-seed evidence (2026-09-13)

This supplement updates only the explicit audit-seed graph evidence after the
final-fix fixture extension. It is based on `a1dd28e` plus the uncommitted final
fix wave. The earlier clean rerun remains historical evidence below, but its
audit-seed counts and fingerprint describe the pre-Genre fixture and are not the
current seed shape.

The ignored local `.env` and existing named volume were retained. No environment
value or credential was printed, copied here, or committed. The Neo4j Compose
service was started normally against that retained volume; no reset or volume
deletion occurred. The first host-JAR seed attempt used Compose's internal
`bolt://neo4j` address and failed before any graph write because that Docker DNS
name is not available to a host process. The successful retry resolved the
published Bolt address in memory, then set the host process variables only for
the command and cleared them in `finally`.

At `2026-09-13T17:01:45+03:00`, two explicit
`pwsh -NoProfile -File scripts/seed.ps1 audit` loads both exited 0. Fixed
container-side queries after the second load returned:

```text
users=3, movies=4, genres=4, ratings=10, inGenre=6
audit-alice -> audit-matrix score=5
invalid RATED keys=0
```

The current snapshot includes all `User`, `Movie`, and `Genre` nodes plus every
`RATED` and `IN_GENRE` relationship, sorted by stable application identifiers
and hashed in memory as UTF-8 SHA-256:

```text
BECDD08883F6352FDBCD6B47C1D0FA8EF06083E4915E3002E8EEC9DE5BEB5CB3
AUDIT_GRAPH_IDENTICAL_ON_RERUN=True
```

This fingerprint supersedes the earlier `CFE02...` audit-seed fingerprint,
which intentionally omitted Genre nodes and `IN_GENRE` edges. The repeat-load
result proves the four fixed UUID Genre nodes and six parameterized
Movie-to-Genre relationships are idempotent while preserving the three users,
four movies, ten ratings, fixed score, and `RATED.key` contract.

## Historical clean rerun — 2026-09-10

At the user's explicit authorization, the controller provisioned an untracked,
ignored local `.env` and removed only the verified old
`neo4flix_neo4j-data` volume. The Task 6 implementer did not perform that reset.
Fresh acceptance below supersedes the earlier environment blocker; the complete
first-attempt evidence remains under **Historical blocked attempt**.

Rerun base: `a38a25dd3d6e385c957928920ca1caa667130c5c`.
Times are local ISO 8601, Europe/Athens (`+03:00`), except explicitly UTC Docker
metadata and application log markers. All runtime commands used approved Docker
access. No `.env` values were printed, copied into this audit, or committed.

Bare `make` remains absent; the actual Makefile targets were executed with:

```text
C:\Users\User\AppData\Local\Microsoft\WinGet\Packages\BrechtSanders.WinLibs.POSIX.UCRT_Microsoft.Winget.Source_8wekyb3d8bbwe\mingw64\bin\mingw32-make.exe
```

Host Maven/seed commands used process-local
`JAVA_HOME=C:\Program Files\Java\jdk-26.0.1`. Their JDK 26 native-access,
Unsafe, and reflective-final-field warnings remain host limitations; the actual
Compose migrator and all four business services were separately observed on
Java **21.0.11**, Temurin **21.0.11+10-LTS**.

### Command results

| Command | Start (+03:00, 2026-09-10) | End (+03:00, 2026-09-10) | Exit |
| --- | --- | --- | --- |
| `mingw32-make.exe test-integration` | 09:21:27.4252380 | 09:22:52.6218178 | 0 |
| `pwsh -NoProfile -File scripts/test-smoke-compose.ps1` | 09:22:00.1584901 | 09:22:00.7802346 | 0 |
| `pwsh -NoProfile -File scripts/test-compose-config.ps1` | 09:22:01.5051751 | 09:22:02.2039469 | 0 |
| Pre-start volume/container queries below | 09:22:47.5046403 | 09:22:47.9493595 | 0 each |
| `docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600` | 09:23:03.0639941 | 09:24:45.5226655 | 0 |
| `pwsh -File scripts/smoke-compose.ps1` (default invocation) | 09:25:01.8038407 | 09:25:18.6459102 | 0 |
| Migration rerun/history, no-seed, volume/JRE checks below | 09:25:40.6223828 | 09:25:50.9143236 | 0 |
| `mingw32-make.exe seed-audit`, first load plus snapshot | 09:26:40.0762495 | 09:26:50.6836946 | 0 |
| `mingw32-make.exe seed-audit`, second load plus snapshot | 09:26:50.6839398 | 09:27:01.0918287 | 0 |
| Seed score/key assertions | after second snapshot | 09:27:04.6697094 | 0 |
| Filtered initial migrator logs | 09:27:12.4093065 | 09:27:12.7524101 | 0 |
| `docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml down` and post-down checks | 09:27:29.2653977 | 09:27:42.3677096 | 0 each |

The integration target ran the real Makefile recipe, not a WhatIf substitute:

```text
pwsh -NoProfile -File scripts/verify.ps1 -Integration
Neo4jSchemaIntegrationTest: 2 tests, 0 failures, 0 errors, 0 skipped
WatchlistedRelationshipConcurrencyIT: 1 test, 0 failures, 0 errors, 0 skipped
RatedRelationshipConcurrencyIT: 1 test, 0 failures, 0 errors, 0 skipped
BUILD SUCCESS
CLEAN_INTEGRATION_EXIT=0
```

The seven smoke contract cases again passed. The Compose configuration contract
also passed, with nonfatal sandbox Docker-config access warnings. These two
checks are **controlled-boundary/static proof**, distinct from the fresh live
Compose smoke and real Testcontainers results.

### Empty volume, migration, GDS, and health

Before startup, both commands returned no rows and exit 0:

```powershell
docker volume ls --filter 'name=^neo4flix_neo4j-data$' --format '{{.Name}}'
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml ps --all --format '{{.Service}} {{.State}}'
```

Compose then reported creation of `neo4flix_neo4j-data`. The initial migrator's
filtered log markers (UTC) prove fresh application of all five migrations:

```text
2026-09-10T06:24:23.495Z Starting MigratorApplication using Java 21.0.11
2026-09-10T06:24:24.197Z GDS verification succeeded for database=neo4j version=2026.07.0
2026-09-10T06:24:25.830Z Applied migration 001 ("core node constraints").
2026-09-10T06:24:26.016Z Applied migration 002 ("relationship uniqueness").
2026-09-10T06:24:26.074Z Applied migration 003 ("search indexes").
2026-09-10T06:24:26.218Z Applied migration 004 ("auth support constraints").
2026-09-10T06:24:26.360Z Applied migration 005 ("share constraints").
2026-09-10T06:24:26.790Z Database migrator completed mode=migrate database=neo4j versions=5
```

The default smoke returned the following live results:

```text
Service                State   Health  ExitCode
database-migrator      exited                 0
movie-service          running healthy        0
neo4j                  running healthy        0
rating-service         running healthy        0
recommendation-service running healthy        0
user-service           running healthy        0
web                    running healthy        0

Migrator exited 0; logs confirm GDS readiness and migration to latest (5 versions).
Schema verified: 11 named constraints, 3 named ONLINE indexes; GDS="2026.07.0".
user-service is UP (http://localhost:8081).
movie-service is UP (http://localhost:8082).
rating-service is UP (http://localhost:8083).
recommendation-service is UP (http://localhost:8084).
Compose runtime smoke passed: migrations/schema verified, four services UP, GDS ready, web reachable.
CLEAN_DEFAULT_SMOKE_EXIT=0
```

The smoke discovers these existing published bindings; no new fixed mappings
were added. The queries are the named constraint/index and GDS queries in
`scripts/smoke-compose.ps1`, now executed successfully against live Neo4j.

The additional live queries used the same container-side credential expansion
as the smoke, with fixed Cypher and no credential output:

```cypher
MATCH (m:__Neo4jMigration) RETURN count(m) AS migrationHistoryCount;
MATCH (n)
WHERE n:User OR n:Movie OR n:Genre OR n:AuthSession OR n:AuthChallenge OR n:RecommendationShare
RETURN count(n) AS businessNodeCount;
```

History contained **6** records before and after:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml run --rm --no-deps database-migrator migrate
```

The rerun exited 0, logged `Skipping already applied migration` for 001–005,
and completed with five versions. The history query returned 6 both times,
with `MIGRATION_HISTORY_UNCHANGED=True`; six is the observed total history
record count, not a claim of six versioned migration files. Before the explicit
seed, `businessNodeCount` was **0**, proving startup did not load fixtures.

For each of `user-service`, `movie-service`, `rating-service`, and
`recommendation-service`, `docker compose --env-file .env -f infra/compose.yml
-f infra/compose.dev.yml exec -T <service> java -version` exited 0 and printed
OpenJDK 21.0.11 / Temurin-21.0.11+10-LTS.

### Explicit deterministic audit seed

Each `mingw32-make.exe seed-audit` executed
`pwsh -NoProfile -File scripts/seed.ps1 audit`, rebuilt the host migrator, and
logged `Database migrator completed mode=seed-audit database=neo4j versions=5`.
Both target exits were 0.

The seed script requires process environment variables. The invocation captured
`docker compose ... config --format json` only in memory, parsed the migrator
environment without printing it, and populated the three required seed variables.
The host Bolt address came from `docker compose ... port neo4j 7687`.
Resolved config objects were cleared before the seed, and the three variables
were cleared in `finally`. No direct `.env` read or credential display occurred.

Each load returned `users, movies, ratings = 3, 4, 10`. Snapshot queries were:

```cypher
MATCH (u:User) WITH count(u) AS users
MATCH (m:Movie) WITH users, count(m) AS movies
MATCH ()-[r:RATED]->() RETURN users, movies, count(r) AS ratings;

MATCH (n) WHERE n:User OR n:Movie
RETURN n.id AS id, properties(n) AS properties ORDER BY id;

MATCH ()-[r:RATED]->()
RETURN r.key AS key, properties(r) AS properties ORDER BY key;
```

The sorted node and relationship rows were joined with LF and hashed in memory
as UTF-8 SHA-256. Both complete snapshots produced:

```text
CFE02DF9F435130962C503AB2EC3A858D223CF56F1A39F338C416EDD5468E426
AUDIT_GRAPH_IDENTICAL_ON_RERUN=True
```

Additional fixed queries asserted Alice/Matrix's score equals **5** and no RATED
key differs from `user.id + ':' + movie.id` (**0** invalid keys). This is actual
explicit seed command execution and repeat-load verification against the
fresh Compose database, not just the earlier loader integration test.

### Ordinary shutdown and named-volume retention

Before shutdown, `docker volume inspect neo4flix_neo4j-data --format '{{.Name}}
{{.CreatedAt}}'` returned `neo4flix_neo4j-data 2026-09-10T06:24:07Z`.

After the ordinary `down` command above (without `-v`), the same inspection
and Compose status query returned:

```text
CLEAN_COMPOSE_DOWN_EXIT=0
neo4flix_neo4j-data 2026-09-10T06:24:07Z
CLEAN_VOLUME_INSPECT_EXIT=0
CLEAN_POST_DOWN_STATUS_EXIT=0
```

The post-down container query had no rows. Ordinary shutdown removed the seven
Compose containers and network and retained the newly seeded named volume with
the same creation timestamp. It can be reused on next startup. No further volume
deletion occurred in this rerun. Authenticated post-restart graph read-back was
not part of this retention check.

### Fresh git hygiene

At `2026-09-10T09:27:55.9605276+03:00`, before the evidence edit, all requested
commands exited 0. Full outputs, including empty status/path results:

```text
> git status --short

> git ls-files .env '*.pem' '*.key' '*token*'

> git log --oneline 9e10b26..HEAD
a38a25d docs: record batch 1 acceptance blocker
9de357f docs: record batch 1 verification
dd8586a docs: advance active batch context
bd72812 fix: align seed scaffolding with graph ID contract
90e8296 feat: add deterministic graph seed loaders
e5f85c2 docs: advance active batch context
a19aa3f test: prove graph relationship uniqueness
b636bf9 docs: advance active batch context
07b6548 fix: harden Neo4j GDS integration harness
e28c7b7 test: add Neo4j GDS integration harness
e104d66 docs: advance active batch context
d96fae1 fix: complete graph relationship mappings
e778998 feat: add graph persistence mappings
4817697 docs: advance active batch context
4ca6fe4 docs: add active batch context
ec68e1f fix: harden database migration verification
a9c2e36 feat: add Neo4j migration baseline
fca6db7 docs: add batch 1 implementation plan
63a7103 docs: add batch 1 graph schema design
```

`git check-ignore .env` returned `.env`, exit 0.
`git diff --exit-code -- docs/reference/00_MASTER_EXECUTION_PLAN.md` returned no output, exit 0.
The fresh evidence resolves the missing live Compose/GDS/schema/seed gate.
Independent Task 6 and final whole-branch reviews are still required; no batch
status was changed. Existing host-JDK warnings and later-batch public business
flows remain outside this acceptance result.


Final post-edit checks ran from `2026-09-10T09:30:06.9042472+03:00` to
`2026-09-10T09:30:08.1764220+03:00`: seven smoke contract cases passed, static
Compose contract passed, `git diff --check` exited 0, and the master-plan diff
was empty (exit 0). Only `docs/audit/batch-1-verification.md` was modified in the
tracked worktree. Static checks again emitted the nonfatal sandbox Docker-config
warning; Git emitted only its local LF-to-CRLF conversion warning.

## Historical blocked attempt — superseded by the clean rerun above

Historical acceptance status: **NOT PASSING — Compose authentication/environment gate blocked.**
Batch 1 remains `[ ]` pending independent Task 6 and whole-branch review. This
report records fresh Task 6 evidence against base
`dd8586a371222e68134555a58de07d828cf5939b` on
`batch-1-graph-schema-migrations`; it does not reuse prior task results as fresh
runtime proof.

## Environment and authorized deviations

Worktree: `C:\Users\User\Desktop\Neo4flix\.worktrees\batch-1-graph-schema-migrations`.
All timestamps below are local ISO 8601 with offset `+03:00` (Europe/Athens);
Neo4j log timestamps are UTC. Docker Desktop server reported `29.6.1`, API `1.55`.

The checkout has no `.env`. It was neither read nor created. The controller
authorized using tracked `.env.example` with the existing Compose files, and
required preserving the existing named volume if its credentials did not work.
No environment values or credentials are reproduced here. No new fixed port
bindings were introduced; the smoke script discovers published HTTP ports from
Compose status.

`make` is unavailable on this host. The exact Makefile recipes were invoked via
PowerShell where possible. `JAVA_HOME` was initially absent; Maven was rerun with
the discovered installed `C:\Program Files\Java\jdk-26.0.1` in the process
environment. This is **JDK 26 host testing, not Java 21 runtime proof**. Maven
reported native-access, deprecated `Unsafe`, and reflective final-field warnings.
The existing Compose Dockerfiles build with pinned Temurin 21 images; successful
image builds do not establish business-service runtime health.

Default sandbox Docker access failed with named-pipe permission denied and an
inaccessible Docker config warning. Scoped elevated Docker executions were
approved and reached the real Docker daemon. These are not WhatIf runs.

## Pre-extension smoke and contract-first checks

At `2026-09-10T00:53:59.6728462+03:00`, before editing the smoke script:

```text
pwsh -File scripts/smoke-compose.ps1
Create .env from .env.example and replace local placeholders first.
PRE_EXTENSION_SMOKE_EXIT=1
end: 2026-09-10T00:54:00.1494554+03:00
```

This failed at the missing-file prerequisite and **did not test live schema**.
Source inspection showed the original script already required migrator exit 0,
but had no migration-completion assertion or named constraint/index queries. Its
GDS assertion expected the obsolete `GDS readiness check succeeded.` marker,
whereas the current Java migrator logs `GDS verification succeeded ...`.

`scripts/test-smoke-compose.ps1` executes the actual smoke script with controlled
Docker/HTTP responses. These are **contract checks, not live acceptance proof**.
An initial fixture scoping error was corrected before the meaningful RED run.
At `2026-09-10T00:57:05.9013134+03:00`:

```text
pwsh -NoProfile -File scripts/test-smoke-compose.ps1
Smoke contract failed: missing migration completion; rejected=False expected=True
SMOKE_CONTRACT_RED_EXIT=1
end: 2026-09-10T00:57:06.5685606+03:00
```

After the minimal smoke extension, the first GREEN run ended at
`2026-09-10T00:58:08.7810791+03:00`, exit 0. The final focused run started at
`2026-09-10T00:59:37.4492152+03:00` and ended at
`2026-09-10T00:59:37.9810803+03:00`, exit 0:

```text
PASS: missing migration completion
PASS: missing GDS readiness
PASS: migrator nonzero exit
PASS: missing named constraint
PASS: missing or offline named index
PASS: failed schema query
PASS: current migrator and complete schema
SMOKE_CONTRACT_GREEN_EXIT=0
```

Failure cases require their expected exception, so an unrelated script error
cannot pass a negative test. The green case uses the current Java migrator log
markers and nonstandard published ports. The smoke now requires all 11 named
constraints and all 3 named search indexes ONLINE, migration completion through
five versions, and a live GDS `2026.07.x` version query. Credentials expand inside
the Neo4j container; the script does not print raw migrator logs or environment
configuration. The schema names are fixed literals, not request data.

## Fresh live integration evidence

`make test-integration` was attempted at
`2026-09-10T00:54:11.0877416+03:00`: command-not-found; no make process or native
exit status existed. The surrounding diagnostic shell returned 0 because its
unset native status was propagated, **not because the test passed**.

The equivalent recipe `pwsh -NoProfile -File scripts/verify.ps1 -Integration`
then exited 1 (`00:54:29.5791358`–`00:54:30.1316536`, `+03:00`) because
`JAVA_HOME` was absent. With the discovered JDK configured, the exact recipe was
rerun from `2026-09-10T00:56:28.9172976+03:00` through
`2026-09-10T00:58:06.6049416+03:00`, exit **0**. It invokes:

```powershell
.\mvnw.cmd -pl backend/platform-common,backend/rating-service,backend/user-service -am '-Dtest=Neo4jSchemaIntegrationTest,*ConcurrencyIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

```text
Neo4jSchemaIntegrationTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
WatchlistedRelationshipConcurrencyIT: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
RatedRelationshipConcurrencyIT: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
INTEGRATION_EXIT=0
```

These tests started real `neo4j:2026.07.1-community` containers with ephemeral
ports. The empty-database test runs the current migrator, requires positive
migration history, checks named constraints, reruns migration with zero added
history entries and unchanged history count, then runs verification. Migrator
verification checks versions 001–005, all expected constraint/index metadata,
and ONLINE indexes. The GDS test executes `RETURN gds.version()` and requires
`2026.07.x`. **The exact GDS patch string is not printed by this harness**, so
this report does not invent one. The concurrency tests passed their actual
RATED duplicate-constraint handling and concurrent WATCHLISTED MERGE assertions.

Independent seed coverage was freshly run against temporary real Neo4j
containers, not the blocked Compose graph:

```powershell
.\mvnw.cmd -pl database/migrator -am '-Dtest=AuditSeedLoaderIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Start `2026-09-10T00:59:41.1576059+03:00`; end
`2026-09-10T01:00:11.7298150+03:00`; exit **0**.

```text
Applied migration 001 ("core node constraints").
Applied migration 002 ("relationship uniqueness").
Applied migration 003 ("search indexes").
Applied migration 004 ("auth support constraints").
Applied migration 005 ("share constraints").
AuditSeedLoaderIT: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
AUDIT_SEED_IT_EXIT=0
```

The audit test asserts three users, the fixed Alice/Matrix score of five, and ten
RATED edges after a second load. Its second case verifies UUID IDs in demo/load
scaffolding. This proves loader behavior; **it does not prove `make seed-audit`
success against the retained Compose volume**.

## Compose, smoke, and explicit seed path

The exact requested command was attempted at
`2026-09-10T01:00:17.2707565+03:00` and ended at
`2026-09-10T01:00:17.3735435+03:00`, exit 1:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600
```

Output: `couldn't find env file: ...\.env`. This is an environment failure, not
a migration or service-health result.

The controller-authorized live alternative started at
`2026-09-10T00:54:53.7064002+03:00` and ended at
`2026-09-10T01:03:19.9542758+03:00`, exit **1**:

```powershell
docker compose --env-file .env.example -f infra/compose.yml -f infra/compose.dev.yml up --build -d --wait --wait-timeout 600
```

All six application/migrator/web images built successfully. Neo4j started with
the existing `neo4flix_neo4j-data` named volume. At
`2026-09-10T00:58:51.9941563+03:00`, safe startup diagnostics showed:

```text
neo4j-1 | 2026-09-09 21:57:07.592+0000 INFO Started.
neo4j-1 | 2026-09-09 21:57:10.020+0000 WARN The client is unauthorized due to authentication failure.
neo4j-1 | 2026-09-09 21:58:45.256+0000 WARN The client is unauthorized due to authentication failure.
neo4flix_neo4j-data 2026-09-09T09:09:46Z
```

The volume predates this run and was not empty. The tracked example credentials
did not authenticate to it. No password reset, volume replacement, new bindings,
or user-specific environment access was attempted.

The startup command's terminal result was:

```text
Container neo4flix-neo4j-1 Error dependency neo4j failed to start
dependency failed to start: container neo4flix-neo4j-1 is unhealthy
COMPOSE_UP_EXIT=1
```

The extended smoke was run against that actual stack:

```powershell
pwsh -File scripts/smoke-compose.ps1 -EnvFile .env.example
```

Start `2026-09-10T00:59:54.2395884+03:00`; end
`2026-09-10T00:59:55.2296084+03:00`; exit **1**:

```text
Service                State   Health   ExitCode
database-migrator      created                 0
movie-service          created                 0
neo4j                  running starting        0
rating-service         created                 0
recommendation-service created                 0
user-service           created                 0
web                    created                 0
The database-migrator must have exited successfully.
LIVE_SMOKE_EXIT=1
```

The unrun migrator's status-field zero does not count as successful completion.
No live Compose schema counts, migration-completion logs, GDS query version,
business-service health, or web success were established.

`make seed-audit` at `2026-09-10T01:00:17.3030936+03:00` failed command lookup
(PowerShell exit 1; no native make exit). The exact recipe
`pwsh -NoProfile -File scripts/seed.ps1 audit` ran from
`2026-09-10T01:00:17.3430106+03:00` to
`2026-09-10T01:00:17.8189151+03:00`, exit 1:
`NEO4J_URI must be set; seed commands do not read .env or start containers.`
No Compose audit data was loaded.

The exact requested shutdown with `--env-file .env` ran from
`2026-09-10T01:00:48.6565280+03:00` to
`2026-09-10T01:00:48.7414211+03:00`, exit 1 (missing `.env`).
After the startup command exited, the authorized ordinary shutdown was run:

```powershell
docker compose --env-file .env.example -f infra/compose.yml -f infra/compose.dev.yml down
docker volume inspect neo4flix_neo4j-data --format '{{.Name}} {{.CreatedAt}}'
docker compose --env-file .env.example -f infra/compose.yml -f infra/compose.dev.yml ps --all --format '{{.Service}} {{.State}}'
```

Start `2026-09-10T01:03:33.6876703+03:00`; end
`2026-09-10T01:03:45.5379555+03:00`. All three commands exited **0**:

```text
COMPOSE_DOWN_EXIT=0
neo4flix_neo4j-data 2026-09-09T09:09:46Z
VOLUME_INSPECT_EXIT=0
POST_DOWN_STATUS_EXIT=0
```

Post-down container status output was empty. The seven Compose containers and
their network were removed by ordinary `down`; the same named Neo4j data volume
and creation timestamp remained. Containers can be recreated from Compose. No
`down -v`, data deletion, or reset was performed. This proves volume retention,
not authenticated read-back of graph contents.

## Verification limits and review handoff

No WhatIf result is used as live proof. Seven controlled-boundary smoke cases
and source inspection are explicitly distinct from six passing real Neo4j
integration tests. The fresh Compose run failed its environment/authentication
gate; the full Batch 1 live acceptance path therefore remains incomplete.
The exact GDS patch value and Java 21 service health remain unproven in this
run. Public register/login/movie/rating/recommendation/watchlist flows in the
eventual clean-stack runbook belong to later batches and were not exercised.

Task 6 changes are confined to the smoke script, its contract test, and this
audit. No architecture, public API, migration, seed, or batch status changed.
No push, merge, destructive cleanup, or secret-bearing file was added. Review
must preserve the non-passing acceptance status until the missing live gate is
resolved and independent reviews complete.

## Git hygiene (full requested outputs)

At `2026-09-10T01:02:48.6687096+03:00`, all three commands below exited 0.
The status is the pre-commit snapshot including this audit file; the commit
cannot contain its own hash. The post-commit SHA and clean-status verification
are recorded in the Task 6 handoff report.

```text
> git status --short
 M scripts/smoke-compose.ps1
?? docs/audit/batch-1-verification.md
?? scripts/test-smoke-compose.ps1

> git ls-files .env '*.pem' '*.key' '*token*'

> git log --oneline 9e10b26..HEAD
dd8586a docs: advance active batch context
bd72812 fix: align seed scaffolding with graph ID contract
90e8296 feat: add deterministic graph seed loaders
e5f85c2 docs: advance active batch context
a19aa3f test: prove graph relationship uniqueness
b636bf9 docs: advance active batch context
07b6548 fix: harden Neo4j GDS integration harness
e28c7b7 test: add Neo4j GDS integration harness
e104d66 docs: advance active batch context
d96fae1 fix: complete graph relationship mappings
e778998 feat: add graph persistence mappings
4817697 docs: advance active batch context
4ca6fe4 docs: add active batch context
ec68e1f fix: harden database migration verification
a9c2e36 feat: add Neo4j migration baseline
fca6db7 docs: add batch 1 implementation plan
63a7103 docs: add batch 1 graph schema design
```

`git diff --check` at the same timestamp exited 0. Git emitted its local
LF-to-CRLF conversion warning for the modified PowerShell file, not a whitespace
error. The blank `git ls-files` output means no tracked file matched the exact
requested globs; it is not a comprehensive secret scanner.

Final focused verification ran from `2026-09-10T01:03:57.6317038+03:00` to
`2026-09-10T01:03:58.2073742+03:00`: `git diff --check` exit 0, all seven
`pwsh -NoProfile -File scripts/test-smoke-compose.ps1` cases exit 0, and
`git diff --exit-code -- docs/reference/00_MASTER_EXECUTION_PLAN.md` exit 0. The batch-status
source was not modified.
