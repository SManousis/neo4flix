# Batch 12 Audit Evidence Foundation Verification

Date: 2026-09-15  
Branch: `main`  
Scope: deterministic seed/runbook, graph/GDS guide, and recommendation explanation

## Documents delivered

- `docs/audit/AUDIT_RUNBOOK.md` — safe stack/seed procedure and exact fixture contract.
- `docs/audit/GRAPH_DEMO.md` — read-only graph inspection, collaborative query stages, and GDS smoke proof.
- `docs/audit/RECOMMENDATION_EXPLANATION.md` — strategy, signal, exclusion, score, reason, and privacy mapping.

The source fixture remains `database/seeds/audit/audit-fixture.json`; no fixture IDs, timestamps, scores, relationships, production Java, frontend, migrations, or volumes were changed.

## Fresh verification

### Audit seed loader

Command:

```powershell
.\mvnw.cmd -pl database/migrator -am '-Dtest=AuditSeedLoaderIT' test
```

Result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`; Maven `BUILD SUCCESS`.

The two Testcontainers cases proved the audit loader after migrations and proved idempotent reload behavior. The fixture contract is 6 users, 8 movies, 4 genres, 11 `IN_GENRE` relationships, and 14 `RATED` relationships.

### Recommendation golden fixture

The first unquoted reactor command stopped in `platform-common` because the selected test class does not exist in that upstream module. The corrected command applies the selector only where present:

```powershell
.\mvnw.cmd -pl backend/recommendation-service -am '-Dtest=RecommendationGoldenFixtureIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`; Maven `BUILD SUCCESS`.

The golden cases proved deterministic hybrid ranking, cold-start strategies, already-rated exclusion, filters, negative-only behavior, score bounds, and live `gds.similarity.cosine` execution. Neo4j emitted non-fatal warnings for optional movie properties absent from the minimal fixture; no test failed.

### Documentation hygiene

- `git diff --check`: passed.
- Secret-like scans for JWT/private-key/access-token/refresh-token/TOTP material in the new runbook: no matches.
- Graph and recommendation source-reference searches resolved the cited repository/test symbols.

### Final regression gate

- Full Maven reactor: `131` tests across the backend/migrator modules, `0` failures, `0` errors, `0` skips; `BUILD SUCCESS`.
- Frontend `npm test`: `27` test files and `105` tests passed; the Docker/Node compatibility check also passed.

## Added minimum-completion evidence

- `SECURITY_CHECKLIST.md` maps verified controls to the existing tests and
  records deployment-only and optional-scanner gaps.
- `TEST_EVIDENCE.md` consolidates the fresh Maven, frontend, browser, Compose,
  and outage evidence without treating skipped credentials as passes.
- `USABILITY_TEST.md` defines the required seven-step human journey and keeps
  the participant result pending rather than fabricating observations.
- `scripts/k6/smoke.js`, `scripts/k6/authenticated-smoke.js`, and
  `STRESS_TEST.md` provide bounded public and disposable-account profiles. The
  public runner passed 30 requests with 0% HTTP failures and p95 latency of
  14.83 ms; the authenticated runner passed 45/45 checks with 0% failures and
  p95 latency of 131.53 ms. A 5-VU/30-second run passed 450/450 checks with 0%
  server failures and 115 explicit rate-limit responses. These remain bounded
  development results, not production capacity claims.
- `scripts/backup-neo4j.ps1` and `scripts/restore-neo4j.ps1` provide explicit,
  guarded Community offline dump/load helpers. They refuse ambiguous restore
  targets; a disposable two-container marker recovery cycle passed without
  touching the project volume.

## Explicit limitations

This slice does not claim a human usability walkthrough, authenticated k6 load
evidence, or HTTPS/redirect evidence. The existing Batch 11 real-stack evidence
and direct-main workflow are preserved.
