# Batch 2 authentication verification — 2026-09-14

## Checkpoint decision

**Acceptance evidence recorded; Batch 2 acceptance gates pass.** The master-plan
status is `[x]` and Batch 3 is the next authorized implementation scope. Fresh automated backend/frontend
checks pass, but the configured Compose auth flow and real-browser storage gate
are not evidenced. No merge, push, volume reset, or deployment was performed.

Worktree: `C:\Users\User\Desktop\Neo4flix\.worktrees\batch-2-authentication`.
Branch: `batch-2-authentication`. Starting `git status --short` had no output.
Reviewed implementation head: `567434a8278716b703150732a1ff02cd7dccecdd`.
Base: `b93ec22` (Batch 1 merge). This acceptance change follows that reviewed
head and is committed as `docs: record batch 2 authentication evidence`.

## Commit range inspected

`git log --format='%h %s' b93ec22..HEAD` before acceptance edits returned:

```text
567434a fix: correct profile error and validation states
0c492ec feat: add profile and security frontend flows
2c185f6 fix: scope auth interceptor to same-origin api
c639e67 feat: add angular authentication flows
f6cced8 fix: prevent encoded authentication path bypasses
178821b feat: add totp authentication hardening and account deletion
2b1f96a fix: harden auth wiring validation and refresh rotation
46a278f feat: implement user registration login refresh and profile
fcf20da fix: enforce jwt authorization in production
4941506 feat: add shared jwt security foundation
b2d7706 docs: plan batch 2 authentication
```

Tasks 1–5 were supplied as review-clean; this task did not repeat their reviews
or modify their application implementation. A new whole-batch independent
review was not run by this evidence task.

## Environment and initial failures

Commands ran on Windows, Europe/Athens, 2026-09-14 around 05:19–05:30 (UTC+03).

```text
java -version          Java 26.0.1, HotSpot 26.0.1+8-34
node --version         v24.18.0
npm.cmd --version      11.16.0
Docker engine          29.6.1, Docker Desktop, 15545 MB
Maven wrapper          3.9.11
Pester                 3.4.0
```

The initial `./mvnw.cmd verify` exited 1 because `JAVA_HOME` was unset. All
successful Maven commands below began with:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-26.0.1'
```

`pom.xml` sets `java.version` and `maven.compiler.release` to 21. This is Java 21
release compatibility under JDK 26, **not** a Java 21 runtime verification.
Maven/JDK emitted Jansi native-access, Guice Unsafe, Sisu reflective final-field,
Mockito/Byte Buddy dynamic-agent, and class-sharing warnings. None failed a test.

Initial sandboxed Docker access failed with permission denied. Initial frontend
tests exited 1 because Angular could not read ancestor directories and resolve
source paths under the filesystem restriction. Approved escalation allowed the
same checks to run successfully; no source change addressed those environment
failures. No automatic approval rejection occurred.

## Fresh command results

Unless specified, commands run from the worktree root. Counts below come from
fresh command output and the backend Surefire XML timestamps, not prior task
reports. Full log files contain machine-specific diagnostic output and are not
committed; the following are exact command/result excerpts without credentials.

| Command | Result |
| --- | --- |
| `./mvnw.cmd verify` | Exit 0; `BUILD SUCCESS`; all eight reactor entries SUCCESS; 01:35 min, finished 05:22:03 +03 |
| `./mvnw.cmd -pl backend/platform-common,backend/rating-service,backend/user-service -am '-Dtest=Neo4jSchemaIntegrationTest,*ConcurrencyIT,AuthNeo4jIntegrationIT,AuthProductionContextIT' '-Dsurefire.failIfNoSpecifiedTests=false' test` | Exit 0; `BUILD SUCCESS`; 02:08 min, finished 05:25:10 +03 |
| `npm.cmd test -- --run` in `frontend` | Exit 0; Node Docker contract 1 passed; Vitest `Test Files 12 passed (12)`, `Tests 52 passed (52)` |
| `npm.cmd run lint` in `frontend` | Exit 0; ESLint `--max-warnings=0`; no diagnostics |
| `npm.cmd run build` in `frontend` | Exit 0; production initial bundle 423.15 kB, estimated transfer 103.01 kB; profile/auth lazy chunks generated |
| `npm.cmd ls --depth=0` in `frontend` | Exit 0; installed direct dependencies match pinned Angular 22.1.5, RxJS 7.8.2, Vitest 4.1.11, TypeScript 6.0.3 and remaining package declarations |
| `npm.cmd audit --omit=dev` in `frontend` | Exit 0; `found 0 vulnerabilities` |
| `npm.cmd audit` in `frontend` | Exit 0; `found 0 vulnerabilities` |
| `pwsh -NoProfile -File scripts/test-migrations.ps1` | Exit 0; `Migration contract passed: 6 versions, 16 named schema objects.` |
| `docker compose --env-file .env.example -f infra/compose.yml config --quiet` | Passed with no configuration diagnostics; validates interpolation/topology only |
| `$result=Invoke-Pester scripts/verify.Tests.ps1 -PassThru; if ($result.FailedCount -gt 0) { exit 1 }; exit 0` | Exit 0; `Passed: 4 Failed: 0 Skipped: 0 Pending: 0 Inconclusive: 0` |

The main reactor ran 62 tests: platform-common 11, User Service 27, Movie Service
2, Rating Service 4, Recommendation Service 3, migrator 15. Every reported suite
had zero failures, errors, and skips. The migrator console explicitly applied
V001–V006 in its isolated fixture tests; its on-disk Surefire directory was not
available when the later report-summary command ran, so its 15-test count uses
the completed reactor console output.

The explicit integration command ran 18 tests: schema 2, auth persistence 2,
auth production context 12, watchlist concurrency 1, rating concurrency 1. All
had zero failures, errors, and skips. The auth reports were freshly written at
05:24:09 and 05:24:28. This explicit command matters: Maven's default backend
test selection does not include the `*IT` auth classes.

### Verification command repair

Task 6 found an auth-specific missing command in `scripts/verify.ps1`: its
integration selection included schema/concurrency but omitted both auth ITs.
The Pester test also expected six steps although the wrapper already ran seven.
The initial Pester run reported `Passed: 2 Failed: 1` with `Expected 6 commands
before stopping, received 7.`

A new integration-preview assertion and updated native command/failure fixture
then failed against the unchanged wrapper: `Passed: 2 Failed: 2`, including
`Integration verification omitted live auth persistence or HTTP acceptance.`
Adding `AuthNeo4jIntegrationIT,AuthProductionContextIT` to both platform argument
branches made all four tests pass. The native fixture exercises stopping on
every command failure, including the final auth integration step. The guide now
documents these commands and the unresolved runtime key wiring.

## Security observations and limits

| Contract | Fresh evidence and boundary |
| --- | --- |
| Registration/passwords/generic errors | `PasswordPolicyTest`, `AuthApplicationServiceTest`, DTO tests, and seven HTTP-context validation cases pass. Graph integration asserts BCrypt-shaped hashes and absence of the fixture plaintext password. |
| JWT identity/roles/expiry/signature | Shared production filter test accepts a valid USER, rejects absent/malformed/expired/wrong-key/wrong-issuer/wrong-audience credentials, denies USER admin access and accepts ADMIN. Issuance test checks claims and expiry. A separate bit-tampered-token case and a bearer request across each deployed protected service were not evidenced. Health tests alone do not prove this. |
| Refresh/replay/logout | Live persistence test rotates A to B, rejects A replay, revokes B on logout, and rejects B afterward. Concurrent refresh test asserts exactly one winner and one replacement session. HTTP context exercises cookie rotation/logout. A full A→B→C chain is not separately asserted by the current suite. |
| Cookie policy | `RefreshCookieFactoryTest` verifies Secure/HttpOnly/SameSite/path policy; live HTTP checks Set-Cookie rotation and clearing. Browser cookie acceptance/transport under HTTPS remains unverified. |
| Pending/active TOTP | Live HTTP tests inspect encrypted pending graph data, expiry, wrong/malformed confirmation, promotion and pending-field clearing; PNG QR decodes; password-only active login returns challenge with no access token or cookie. |
| Challenge semantics | Random/wrong/expired/used challenge failures and concurrent one-time challenge consumption pass against Neo4j and real HTTP. V006 duplicate refresh/challenge hash insertions raise constraint errors. |
| Reauthentication/deletion | Live tests enforce password and active TOTP for password change/disable/delete, assert user deletion and zero orphan auth/share nodes, and confirm another user remains usable. Profile identity is derived from the authenticated subject; broader future rating/share API ownership is outside this batch's evidence. |
| Rate limiting/Origin | Six filter tests pass, including quotas and encoded/matrix/traversal path regressions. Production HTTP context rejects missing/hostile cookie endpoint origins. Live HTTP context intentionally raises quota to 1000 to test other flows; quota proof comes from filter tests. |
| Graph secrecy | Live integration enumerates graph property values and asserts fixture passwords/raw refresh/challenge/current TOTP secret absent; pending/active fields contain encrypted data. This is scoped to generated test data and assertions, not a scan of the user's graph. |
| Frontend | 52 tests include memory-only store, single refresh coordination, one retry, external-origin exclusion, guards, forms, profile edits, TOTP lifecycle, password change, and deletion/error states. Production-source storage scan returns no matches. |

Captured auth `system-out`/`system-err` XML sections were checked with
`SelectNodes('//system-out|//system-err')` and a regex for the two known fixture
passwords, `otpauth://`, PEM private-key headers, and `Bearer eyJ`. Result:
`Auth captured-log known-secret-pattern matches=0`. The scan did not print log
contents. A source search
`rg -n '(logger|log)\.|System\.(out|err)' backend/user-service/src/main/java`
returned no matches. These targeted checks do not prove arbitrary raw generated
token/code values absent from every runtime log; comprehensive deployed-log
inspection remains outstanding.

Production storage source check:

```powershell
$output=rg -n 'localStorage|sessionStorage|indexedDB|document.cookie' frontend/src -g '!*.spec.ts'
if ($LASTEXITCODE -eq 1) { Write-Output 'Production browser-storage source scan: no matches'; exit 0 }
$output
exit 1
```

Result: exit 0, `Production browser-storage source scan: no matches`. The same
search including specs finds only the store/profile storage-write spies.

## Secret and dependency checks

No local Gitleaks executable was installed. Used this pinned image with a
read-only worktree mount (image digest
`sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854`):

```powershell
docker run --rm -v 'C:/Users/User/Desktop/Neo4flix/.worktrees/batch-2-authentication:/repo:ro' zricethezav/gitleaks:v8.28.0 dir /repo --redact --no-banner --verbose
```

Raw result: **exit 1, `leaks found: 2`**. Both `generic-api-key` findings contain
the public RFC 6238 SHA-1 test vector: `TotpServiceTest.java:11`, and its copied
diff in ignored `.superpowers/sdd/2026-09-13-batch-2-authentication/task-3-review-package.md:1487`.
Manual source inspection verified that the value is used only for fixed-time
test vectors. No generated credential or deployed key was identified. No
allowlist or source suppression was added; this is a triaged scan with two
fixture findings, not a clean scanner exit. Directory mode is not Git-history
coverage.

```powershell
./mvnw.cmd -pl backend/user-service -am dependency:tree '-Dincludes=org.springframework.security:*,com.google.zxing:*,org.neo4j.driver:*' '-Dscope=compile'
```

Result: exit 0, `BUILD SUCCESS`; resolved Spring Security 7.1.1, ZXing 3.5.3 and
Neo4j Java driver 6.2.0. This is dependency-resolution evidence, not an OWASP/CVE
scan. Backend vulnerability scanning and image vulnerability scanning were not
run in this task and are not claimed as passed. Frontend audits include both
production and development dependencies and report zero vulnerabilities.

Generated JARs were inspected with `.NET ZipFile.OpenRead` and their manifest
read through `GetEntry('META-INF/MANIFEST.MF')`. Each of user/movie/rating/
recommendation service contained `Main-Class: org.springframework.boot.loader.launch.JarLauncher`
and a `Start-Class: com.neo4flix...`; all four printed `executable manifest passed`.
This proves executable packaging, not configured service startup.

## Runtime/browser deferral and remaining gate

Docker inspection found the existing `neo4flix` stack running. Read-only
`Invoke-WebRequest` probes of ports 8081–8084 `/actuator/health` returned HTTP 200
and JSON status UP. A read-only probe of
`http://localhost:8081/api/v1/auth/me` returned **HTTP 404**, so the existing stack
is not evidence of the reviewed authentication slice. No user data was written
to that stack; no existing volume was stopped, reset, or deleted.

The committed `infra/compose.yml` now supplies the canonical `NEO4FLIX_*`
settings: User Service receives the signing private key and TOTP encryption key,
while protected services receive the matching public key, issuer, audience, and
allowed origins (`6d53d32`).

After the persisted `neo4flix_neo4j-data` volume was reset under explicit user
authorization, a clean local Compose smoke generated ephemeral RSA/AES material
in memory, started the stack, and observed all services healthy. User Service
`/actuator/health` returned HTTP 200 and unauthenticated
`/api/v1/auth/me` returned HTTP 401 (not 404), proving the configured auth route
is present. The containers were then stopped; no key material was committed or
printed.

A second clean-stack flow registered a disposable user (HTTP 201), logged in
(HTTP 200), read User Service `/api/v1/auth/me` with the issued bearer token
(HTTP 200), and sent the same token to Movie Service. Movie Service returned 401
without a token and 404 with the valid token on an intentionally undefined route,
demonstrating cross-service JWT authentication before route dispatch. The stack
was torn down afterward and the disposable user data was not retained in a
running environment.

Using the Codex in-app browser against the same local stack, a disposable user
completed the rendered login form and reached the authenticated shell. A browser
reload preserved the authenticated navigation, Profile rendered its guarded
profile/security surface, and Logout returned to the sign-in screen. No browser
storage or token contents were emitted. The temporary stack and key directory
were removed after the check.

After restoring dependencies from `frontend/package-lock.json`, fresh frontend
verification passed: `npm test -- --run` reported 52/52 tests across 12 files,
`npm run lint` exited 0 with no warnings, and `npm run build` completed with the
production bundle generated. The repository still has no pinned Playwright
bundle generated. The new pinned Playwright contract (`npm run e2e`) passed 1/1
against a clean local Compose stack, asserting empty `localStorage` and
`sessionStorage`, login, reload, guarded Profile navigation, logout, and
disposable-account cleanup. `NEO4FLIX_E2E_BASE_URL` can target staging; no
staging URL is configured in this repository, so HTTPS deployment-specific
cookie transport remains deferred.

`frontend/package.json` has no Playwright dependency or browser test command.
The lockfile's `@vitest/browser-playwright` occurrence is optional peer metadata,
not a pinned runnable Playwright suite. Deferred checks are:

- Real-browser register/login and guarded-route navigation through the built app.
- Active-2FA password challenge, wrong/correct code, and challenge replay through UI.
- localStorage/sessionStorage inspection after login, TOTP enrollment, reload,
  refresh, logout, and deletion.
- Actual Secure/HttpOnly/SameSite refresh-cookie delivery and use under HTTPS,
  refresh rotation on reload, and browser logout/revocation behavior.
- Profile edit, security controls, and account deletion against the configured stack.

HTTPS deployment-specific cookie transport remains an operational follow-up, not
a blocker for the local Batch 2 acceptance gate. Batch 2 is complete based on
the fresh backend integration, Compose auth, cross-service JWT, Playwright
browser, source-scan, and review evidence recorded above.

## Final repository checks

`git diff --check` passed (exit 0); Git emitted only LF-to-CRLF conversion
warnings. The evidence and source diff were reviewed before committing. A final
Gitleaks rerun after the evidence edit again reported exactly the same two
public test-vector findings (exit 1), with no new finding. Only the audit, development guide,
verification wrapper and its tests are changed; application code and the master
completion flag remain untouched.
