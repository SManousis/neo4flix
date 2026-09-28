# Batch 12 Audit Evidence Foundation Design

## Goal

Make the existing recommendation fixture and graph queries directly usable in an evaluator walkthrough without changing application behavior. The first slice will produce a repeatable seed/runbook pair and two concise evidence guides that point to the implementation and its existing golden tests.

## Scope

The slice covers:

1. A deterministic audit-seed contract built around the existing `database/seeds/audit/audit-fixture.json`, `AuditSeedLoader`, and `scripts/seed.ps1` entry point.
2. `docs/audit/AUDIT_RUNBOOK.md` with safe startup, migration, seed, verification, and cleanup commands that never require committing or printing `.env` values.
3. `docs/audit/GRAPH_DEMO.md` with parameterized Cypher that demonstrates the seeded `User`–`RATED`–`Movie`–`IN_GENRE` graph and the live GDS cosine call already used by the recommendation repository/tests.
4. `docs/audit/RECOMMENDATION_EXPLANATION.md` mapping the deterministic fixture personas, strategy thresholds, candidate/exclusion rules, scoring signals, and reason text to current production classes and the golden fixture test.

The documents will state exact expected counts from the existing fixture (6 users, 8 movies, 4 genres, 11 movie-genre links, and 14 ratings) and distinguish test-container evidence from any local Compose evidence.

## Data flow

`audit-fixture.json` is loaded by `AuditSeedLoader` through parameterized `MERGE` statements. `scripts/seed.ps1 audit` builds and runs the migrator without reading `.env`; the operator supplies `NEO4J_URI`, `NEO4J_USERNAME`, and `NEO4J_PASSWORD` in the process environment. The runbook will use `--what-if`/explicit cleanup guidance and will not prescribe volume deletion or destructive reset commands.

The graph guide will use read-only Cypher parameters for counts, relationship inspection, and a small `gds.similarity.cosine` probe. The recommendation guide will cite `RecommendationNeo4jRepository`, `RecommendationScoringService`, `RecommendationApplicationService`, and `RecommendationGoldenFixtureIT`; it will not duplicate query logic or claim a signal that is absent from code.

## Verification

- Validate the fixture and loader with the existing `AuditSeedLoaderIT` and recommendation golden tests.
- Run `git diff --check` and a markdown placeholder/credential scan.
- If the Compose stack is used, record only service health and query results; never print passwords, JWTs, or `.env` contents.
- Keep the current Batch 11 evidence and direct-main workflow unchanged.

## Non-goals

- No changes to production recommendation algorithms, APIs, schema migrations, or frontend behavior.
- No new GDS projection or optional Jaccard implementation.
- No k6 stress run; that remains the Batch 13 follow-up.
- No Neo4j volume reset and no permanent audit credentials.
