# Batch 12 Audit Evidence Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing deterministic audit fixture and recommendation implementation into a concise, repeatable evaluator runbook with graph and scoring evidence.

**Architecture:** Documentation-only first slice. The existing `audit-fixture.json` remains the source of truth; `AuditSeedLoader` and `scripts/seed.ps1` remain the only seed entry points. Three evidence documents will reference current parameterized Cypher, service classes, and golden tests, and a batch audit record will capture fresh verification without changing production code.

**Tech Stack:** Markdown, PowerShell, Maven, Neo4j Cypher, Neo4j GDS, Docker Compose, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-15-batch-12-audit-evidence-design.md`

## Global Constraints

- Use the existing `database/seeds/audit/audit-fixture.json`; do not duplicate or silently alter fixture IDs, timestamps, scores, or relationships.
- Keep all Cypher parameterized; never interpolate user-provided values into executable queries.
- Never print, commit, or document `.env` values, passwords, JWTs, refresh tokens, or TOTP secrets.
- Do not reset or delete Neo4j volumes; seed loading must be idempotent and additive through the existing `MERGE` loader.
- Do not change production Java, frontend, migrations, or API behavior in this slice.
- Record observed evidence separately from expected evidence; never claim a command was run unless its output is captured in the batch audit.

---

### Task 1: Document the deterministic seed and safe operator runbook

**Files:**
- Create: `docs/audit/AUDIT_RUNBOOK.md`
- Inspect: `database/seeds/audit/audit-fixture.json`
- Inspect: `database/migrator/src/main/java/com/neo4flix/migrator/seed/AuditSeedLoader.java`
- Inspect: `scripts/seed.ps1`
- Test: `database/migrator/src/test/java/com/neo4flix/migrator/seed/AuditSeedLoaderIT.java`

**Interfaces:**
- Consumes: the existing `seed-audit` migrator command and fixture IDs/counts.
- Produces: a no-secret runbook with startup, migration, seed, read-only verification, and cleanup guidance.

- [x] **Step 1: Write the runbook structure**

Create sections for prerequisites, Compose startup, migrator verification, audit seeding, expected fixture counts, read-only Cypher checks, application walkthrough handoff, and cleanup. State that seed commands do not read `.env` and require process-scoped `NEO4J_URI`, `NEO4J_USERNAME`, and `NEO4J_PASSWORD`.

- [x] **Step 2: Add exact safe commands**

Document Windows PowerShell commands from the repository root:

```powershell
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml up -d --wait --wait-timeout 600
.\scripts\seed.ps1 audit
docker compose --env-file .env -f infra/compose.yml -f infra/compose.dev.yml ps
```

Explain that operators must inject the three seed environment variables without echoing them, and that `docker compose down -v`, `Remove-Item` against Neo4j volumes, and ad-hoc password output are prohibited for this runbook.

- [x] **Step 3: Record the fixture contract**

List the exact idempotent counts: 6 users, 8 movies, 4 genres, 11 `IN_GENRE` relationships, and 14 `RATED` relationships. Name the deterministic personas (`audit-alice`, `audit-bob`, `audit-carol`, `audit-fresh`, `audit-sparse`, `audit-negative`) and explain that rerunning `seed-audit` must keep relationship counts at 14 rather than duplicate them.

- [x] **Step 4: Add the focused verification command**

Document `.\mvnw.cmd -pl database/migrator -am -Dtest=AuditSeedLoaderIT test` as the fixture/loader proof, with expected result `2 tests, 0 failures` in the current migrator module. Do not invent a local Compose result; leave the batch audit to record the actual run.

- [x] **Step 5: Run documentation checks**

Run `git diff --check` and scan the new file for forbidden material:

```powershell
if (Select-String -Path docs/audit/AUDIT_RUNBOOK.md -Pattern 'eyJ|BEGIN PRIVATE KEY|accessToken|refreshToken|TOTP_SECRET' -Quiet) { throw 'secret-like content found' }
```

Expected: no diff errors and no secret-like match.

---

### Task 2: Add the live graph and GDS demonstration guide

**Files:**
- Create: `docs/audit/GRAPH_DEMO.md`
- Inspect: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence/RecommendationNeo4jRepository.java`
- Inspect: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/RecommendationGoldenFixtureIT.java`
- Inspect: `docs/reference/03_GRAPH_DATABASE_SPEC.md`

**Interfaces:**
- Consumes: seeded IDs and current repository query semantics.
- Produces: read-only, parameterized Neo4j Browser/Cypher-shell snippets with expected evidence.

- [x] **Step 1: Document the graph shape**

Explain `(:User)-[:RATED {key, score, createdAt, updatedAt}]->(:Movie)` and `(:Movie)-[:IN_GENRE]->(:Genre)`, including why the deterministic rating key is `<userId>:<movieId>` and why rating scores live on relationships.

- [x] **Step 2: Add parameterized count and relationship queries**

Include Neo4j Browser parameters and read-only queries for total nodes, total ratings, Alice’s rated titles, and genre traversal. Use `$userId`, `$movieId`, and `$minimumOverlap` parameters; do not concatenate values into Cypher.

- [x] **Step 3: Explain the collaborative query**

Map each stage of `COLLABORATIVE_QUERY`—overlap matching, ordered aligned vectors, minimum overlap, `gds.similarity.cosine`, peer limit, positive peer ratings, rated exclusion, and normalized score—to the current repository source. Include the exact GDS smoke query already used by the golden test and expected similarity `1.0`.

- [x] **Step 4: Add an evidence checklist**

Require the evaluator to capture only node/relationship counts, query result rows, and the GDS scalar. Explicitly exclude emails, password hashes, JWT claims, and peer identity from screenshots or public output.

- [x] **Step 5: Verify references**

Run `rg -n "COLLABORATIVE_QUERY|gds\.similarity\.cosine|RecommendationGoldenFixtureIT" docs/audit/GRAPH_DEMO.md backend/recommendation-service/src/main/java backend/recommendation-service/src/test/java` and `git diff --check`. Expected: all cited symbols resolve and no diff errors occur.

---

### Task 3: Add the recommendation strategy and scoring explanation

**Files:**
- Create: `docs/audit/RECOMMENDATION_EXPLANATION.md`
- Inspect: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationScoringService.java`
- Inspect: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/core/RecommendationApplicationService.java`
- Inspect: `backend/recommendation-service/src/main/java/com/neo4flix/recommendation/persistence/RecommendationNeo4jRepository.java`
- Inspect: `backend/recommendation-service/src/test/java/com/neo4flix/recommendation/RecommendationGoldenFixtureIT.java`
- Inspect: `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md`

**Interfaces:**
- Consumes: current strategy enum, weights, query result fields, fixture personas, and golden assertions.
- Produces: an evaluator-readable mapping from fixture input to strategy, candidate, score, and reason output.

- [x] **Step 1: Map fixture personas to cold-start states**

Document Fresh (0 ratings) as `POPULARITY`, Sparse (1 rating) as `CONTENT_PLUS_POPULARITY`, and Alice (4 ratings with qualifying peers) as `HYBRID`, citing the strategy-selection code and the golden test assertions.

- [x] **Step 2: Explain each signal and bound**

Describe collaborative similarity, genre affinity, popularity/confidence, and final weighted normalization only as implemented. State the score range `[0,1]`, default ordering, deterministic tie-break behavior, and that negative ratings do not create positive genre affinity.

- [x] **Step 3: Explain exclusion and filters**

Document the `NOT (me)-[:RATED]->(movie)` rule, why watchlisted movies remain eligible, and how genre/year/minimum-average-rating/paging are applied after candidate generation. Include the Blade Runner and already-rated assertions from `RecommendationGoldenFixtureIT`.

- [x] **Step 4: Map reason text and privacy boundaries**

List the canonical public reason strings (`SIMILAR_USERS`, `GENRE_MATCH`, `POPULAR`) and state that peer email/name, rating history, auth data, and identity-bearing vectors are never exposed.

- [x] **Step 5: Verify source alignment**

Run `rg -n "POPULARITY|CONTENT_PLUS_POPULARITY|HYBRID|score\(\)|reason\(|doesNotContain|gds\.similarity\.cosine" docs/audit/RECOMMENDATION_EXPLANATION.md backend/recommendation-service/src/main/java backend/recommendation-service/src/test/java` and `git diff --check`. Expected: every documented strategy/reason has a source or test match.

---

### Task 4: Capture Batch 12 foundation evidence and handoff

**Files:**
- Create: `docs/audit/batch-12-verification.md`
- Modify: `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`
- Modify: `docs/reference/00_MASTER_EXECUTION_PLAN.md`

**Interfaces:**
- Consumes: Tasks 1–3 documents and fresh test output.
- Produces: honest audit evidence and a clear next-workstream handoff for the remaining Batch 12 usability/security/test documents.

- [x] **Step 1: Run focused backend verification**

Run:

```powershell
.\mvnw.cmd -pl database/migrator -am '-Dtest=AuditSeedLoaderIT' test
.\mvnw.cmd -pl backend/recommendation-service -am '-Dtest=RecommendationGoldenFixtureIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Record exact test counts and any environment skip; do not substitute a prior run. The current run is expected to report 2 migrator tests and 2 recommendation tests with zero failures.

- [x] **Step 2: Run repository hygiene checks**

Run `git diff --check` and the secret-like scans from Tasks 1–3. Expected: zero diff errors and no secret-like matches.

- [x] **Step 3: Write the verification record**

Record the commands, observed counts, fixture contract, and explicit limitations. State that this slice does not claim human usability, security checklist, or k6 evidence; those remain unfinished Batch 12 work.

- [x] **Step 4: Update handoff metadata**

Mark only the Batch 12 evidence-foundation slice as complete in the active context. Keep the master plan at `[~]` until usability, security, test evidence, and runbook requirements are all completed. Set the next unfinished workstream to the remaining Batch 12 walkthrough/evidence documents.

- [x] **Step 5: Review, commit, and push**

Run `git status --short`, request a focused review of the docs/evidence diff, then commit with `docs: add batch 12 audit evidence foundation`. Push `main` and verify `git rev-parse HEAD` equals `git ls-remote origin refs/heads/main`.
