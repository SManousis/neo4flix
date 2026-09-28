# Test Evidence Index

Date: 2026-09-24
Environment: Windows, Docker Desktop, Java 26 runtime with the project Maven
wrapper, Node/npm frontend toolchain

## Fresh regression evidence

| Slice | Command/evidence | Result |
| --- | --- | --- |
| Backend and migrator reactor | `.\mvnw.cmd test` | 131 tests, 0 failures, 0 errors, 0 skips; `BUILD SUCCESS` |
| Frontend unit suite | `npm.cmd --prefix frontend test` | 27 files, 105 tests passed |
| Audit fixture loader | `.\mvnw.cmd -pl database/migrator -am '-Dtest=AuditSeedLoaderIT' test` | 2/2 passed; idempotent fixture reload |
| Recommendation golden fixture | `.\mvnw.cmd -pl backend/recommendation-service -am '-Dtest=RecommendationGoldenFixtureIT' '-Dsurefire.failIfNoSpecifiedTests=false' test` | 2/2 passed; deterministic ranking and GDS cosine path |
| Recommendation focused query-plan and golden suite | `.\mvnw.cmd -pl backend/recommendation-service -am '-Dtest=RecommendationGoldenFixtureIT,RecommendationQueryPlanIT' '-Dsurefire.failIfNoSpecifiedTests=false' test` | 3/3 passed after the plain-JAR lifecycle and isolated nested-build fixes |
| Browser contract | `npm.cmd --prefix frontend run e2e` against local Compose (fresh rerun) | 8 passed, 1 skipped for absent disposable ADMIN credentials in 15 seconds |
| Standalone ADMIN browser contract | Disposable registration/promotion and cleanup | 1 passed; cleanup count 0 |
| Compose runtime | `docker compose ... ps` and `scripts/smoke-compose.ps1` | Six services healthy; web returned HTTP 200 with request ID |
| Recommendation/Neo4j outage handling | Batch 11 controlled outage record | Catalog remained available; recommendation outage returned a request-traceable error; stack restored |
| Disposable Neo4j recovery | Guarded backup/restore helpers with separate temporary source/target containers and volumes | Marker node dumped, restored, and read back successfully; temporary resources removed |
| Authenticated k6 smoke | `scripts/k6/authenticated-smoke.js` with disposable runtime user | 15 seconds/1 VU; 48 requests; 45/45 checks; 0.00% HTTP failures; teardown left 0 users |
| Bounded sustained k6 run | Same profile at 5 VUs for 30 seconds | 453 requests; 450/450 checks; 0.00% server failures; 115 explicit 429s; p95 11.83 ms; 0 users remained |
| Full Maven/Testcontainers verification | `.\mvnw.cmd verify` with Docker | All reactor modules passed; 131 backend tests reported 0 failures/errors/skips; JDK 26 used because JDK 21 is not installed locally |
| Executable service JAR smoke | `scripts/test-executable-service-jars.ps1` | User, movie, rating, and recommendation JARs packaged and started successfully |
| Local audit seed after plain-JAR packaging change | `scripts/seed.ps1 audit` | Audit seed completed through the executable migrator JAR; six migrations and GDS verification remained healthy |

## Explicit limitations

- The browser suite is automated evidence. The user separately confirmed the
  seven-step human usability audit was completed; detailed participant notes
  were not supplied for storage in `USABILITY_TEST.md`.
- `scripts/k6/smoke.js` is an anonymous catalog smoke profile. It does not prove
  authenticated recommendation/rating throughput or a release SLO.
- Local Compose is HTTP-only. HTTPS redirect/HSTS, certificate rotation, and
  production ingress evidence are N/A because this project will not be deployed.
- Optional OWASP Dependency-Check, Gitleaks, and Trivy binaries were not
  installed in the execution environment; their absence is recorded rather than
  treated as a passing scan.
