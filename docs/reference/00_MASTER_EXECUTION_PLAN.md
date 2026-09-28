# Neo4flix — Master Execution Plan

> **For agentic workers:** This is the only document that controls **implementation order and progress state**.
>
> Requirements live in the canonical specifications. Do not use this file as a substitute for product, graph/database, API, frontend, testing/security, recommendation/audit, or deployment details.
>
> Required workflow: use `superpowers:writing-plans` before implementation; prefer `superpowers:executing-plans` for the shared main checkout; use `superpowers:test-driven-development`, `superpowers:systematic-debugging`, focused review/fix loops when needed, and `superpowers:verification-before-completion`.
>
> Direct-main mode is authorized for this repository. Commit logical checkpoints directly on `main`; never push without explicit user approval.

## 1. Goal

Build and prove the 01-edu Neo4flix MVP:

> A secure Angular/Spring Boot microservice movie application backed by Neo4j that supports movie CRUD/search, user/auth/2FA, ratings, watchlists, shareable recommendations, and a demonstrable graph-based hybrid recommendation engine.

The final project must be straightforward to audit: graph, Cypher/GDS logic, four services, security, usability, Docker, HTTPS, and stress evidence must all be demonstrable.

## 2. Canonical Document Map

| Document | Owns |
|---|---|
| `docs/reference/01_PRODUCT_SPEC.md` | MVP scope, roles, workflows, exclusions, business invariants |
| `docs/reference/02_TECHNICAL_ARCHITECTURE.md` | stack, services, repo/package/runtime shape, build/config tooling |
| `docs/reference/03_GRAPH_DATABASE_SPEC.md` | graph labels/relationships/properties, constraints, migrations, transactions, ownership |
| `docs/reference/04_API_SPEC.md` | HTTP contract, auth/status/pagination/endpoint semantics |
| `docs/reference/05_FRONTEND_SPEC.md` | Angular routes/pages/components/state/UX/accessibility |
| `docs/reference/06_TESTING_SECURITY.md` | tests, negative paths, auth/security, load/security gates |
| `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md` | recommendation algorithm, GDS/Cypher proof, audit/usability evidence |
| `docs/reference/08_DEPLOYMENT_OPERATIONS.md` | Compose, GDS, TLS, secrets, seed, backup/restore, operational smoke |
| `docs/superpowers/plans/*.md` | generated task plans for one implementation batch only |

## 3. Source-of-Truth Rules

When information overlaps:

1. current official 01-edu subject/audit is the external assignment requirement; if it changes, explicitly reconcile before implementation
2. `docs/reference/01_PRODUCT_SPEC.md` governs approved product/MVP semantics
3. domain-specific canonical spec governs its technical concern
4. `docs/reference/02_TECHNICAL_ARCHITECTURE.md` governs cross-cutting implementation shape
5. this master plan governs order/progress only
6. generated Superpowers plans execute approved requirements and may not silently override them
7. existing code is current-state evidence, not authority over explicit canonical requirements

If canonical documents materially conflict on API, graph model, security, service ownership, recommendation behavior, or product invariant: **stop and report the exact conflict**. Do not invent a third interpretation.

## 4. Status Legend

- `[ ]` not started/incomplete/unverified
- `[~]` implementation exists but the batch gate is not fully proven
- `[x]` fresh acceptance evidence + required review passed
- `[!]` blocked by unresolved conflict/failure/dependency

A status is an evidence claim.

## 5. Global Implementation Invariants

1. Preserve exactly four required business microservices.
2. All use one shared Neo4j graph; write ownership remains strict.
3. Rating Service is the only `RATED` writer.
4. User Service is the only User/auth/watchlist writer.
5. Movie Service is the only Movie/Genre writer.
6. Recommendation Service is the only RecommendationShare writer and recommendation algorithm owner.
7. Movie Service recommendation endpoint is a facade, not a duplicate engine.
8. User Service rating history is a read-only facade; no duplicate rating mutation logic.
9. `POST /ratings` creates; it must not silently update an existing relationship.
10. Relationship uniqueness must survive concurrent requests.
11. `releaseYear` required; `releaseDate` optional and never fabricated.
12. Recommendations exclude already-rated movies and reflect negative ratings correctly.
13. Raw passwords/tokens/TOTP secrets/private keys never persist/log in unsafe form.
14. Access JWT remains in browser memory; refresh token is HttpOnly/Secure cookie in deployed environments.
15. Backend authorization is authoritative.
16. All user values in Cypher are parameterized; sort/query identifiers are allowlisted.
17. One-shot migrator owns schema; services do not race migrations.
18. GDS must be actually used and verified.
19. No Kafka/RabbitMQ/Redis/GraphQL/Kubernetes/WebFlux/NgRx/additional business services unless canonical design is revised.
20. Tests using mocks cannot substitute for real Neo4j/browser/runtime proof where the behavior depends on them.

## 6. Per-Batch Superpowers Protocol

#### A. Prepare

#### New Batch

For a batch that has not yet been planned:

1. read this batch's **Required reading**
2. inspect current repository/code/test state relevant to the batch
3. invoke `superpowers:writing-plans`
4. save the plan to:

```text
docs/superpowers/plans/YYYY-MM-DD-batch-N-<slug>.md
```

5. reconcile the canonical context required by the batch
6. ensure the generated plan contains enough task-level context that
   implementation workers do not need to rediscover the whole architecture
7. create/update:

```text
docs/superpowers/ACTIVE_BATCH_CONTEXT.md
```

8. review the plan against canonical requirements before execution

#### Resume Existing Batch

If an active valid plan and `ACTIVE_BATCH_CONTEXT.md` already exist:

1. do not regenerate the plan
2. do not repeat full repository orientation
3. read the active context
4. read the active plan
5. read the current SDD ledger/workspace when available
6. inspect relevant git state
7. resume the first unfinished workstream

Canonical specifications remain authoritative and may be opened when required,
but full-spec rereading is not the default resume behavior.

#### Plan Content Rule

Plans must specify:

- exact paths
- interfaces/contracts before dependent code
- dependency order
- TDD steps where appropriate
- focused tests
- required real integration/runtime proof
- verification commands
- canonical section references

Plans should **reference canonical specifications, not reproduce them**.

Do not copy large canonical sections into generated plans unless exact text is
necessary to resolve an implementation ambiguity.

### B. Shared Main Checkout

Implementation proceeds directly on `main` in this repository. Do not create,
reuse, or delete worktrees for ordinary batch execution. Do not run parallel
implementers against the shared checkout.

Before each workstream, verify:

```bash
git status --short
git branch --show-current
```

The controller must understand and preserve unrelated user edits. Commit small,
logical checkpoints directly on `main` so every change is independently
reversible. Never reset, rewrite, delete, or destructively alter user work.
Pushing still requires explicit human approval.

### C. Execute

Prefer `superpowers:executing-plans` in the shared checkout. Use subagents only
for read-only review or when the user explicitly requests delegation; never
dispatch concurrent writers against `main`.

#### SDD Dispatch and Context Policy

Use **one coherent implementation workstream at a time**, not one agent per
small checkbox. Keep the controller's context compact and execute tightly
coupled changes together.

A coherent workstream is a group of tightly coupled changes that benefit from
sharing the same implementation context.

Examples:

```text
repository + service + controller + DTO + focused tests
```

```text
migration + migration verification + constraint tests
```

Do not combine unrelated features merely to reduce agent count.

The controller supplies each worker a compact task packet containing:

- objective
- relevant active invariants
- exact files/interfaces involved
- exact canonical section references
- acceptance criteria
- expected focused tests
- prior decisions/interfaces the task depends on

Workers should not reread the whole planning set or complete canonical specs
unless the supplied context is insufficient.

The SDD ledger/workspace is authoritative for execution progress.

Do not redispatch completed work after context compaction or session restart.

TDD remains required for behavior-bearing work.

Use `superpowers:systematic-debugging` for unexplained failures.

#### Review Depth

Review effort is risk-based.

##### LOW Risk

Examples:

- straightforward configuration wiring
- documentation
- simple boilerplate
- non-behavioral refactoring

Flow:

```text
implementation
→ self-check
→ focused verification
```

##### NORMAL Risk

Default for ordinary application behavior.

Flow:

```text
implementation
→ combined specification/code-quality review
→ fixes if needed
→ focused verification
```

##### CRITICAL Risk

Use independent deeper review for:

- authentication
- authorization
- password/token/TOTP handling
- graph-schema migrations
- relationship uniqueness
- concurrency integrity
- destructive graph transactions
- Cypher security
- recommendation scoring/GDS correctness
- deployment secrets
- TLS/security configuration
- any task explicitly identified as critical by canonical requirements

Flow:

```text
implementation
→ specification/integrity review
→ code-quality/security review
→ fix/re-review when findings exist
→ focused verification
```

A finding-free review does not require a ceremonial re-review.

#### Reviewer Context

Reviewers begin with:

- the task packet
- active batch context
- changed files/diff
- relevant tests
- exact canonical references

Reviewers open additional canonical material only when needed to:

- verify a requirement
- investigate a possible violation
- resolve ambiguity

Independent review means independent reasoning, not independent rediscovery
of the entire repository.

#### Lean Review Policy

A focused review is required for security-sensitive, cross-service, migration,
or failed-test changes. Otherwise review the coherent workstream once at the
batch gate. Do not repeat full diff/spec loading after a clean review.

- controller supplies compact task context and canonical references
- TDD for behavior-bearing work
- focused tests during implementation
- one full verification run at the batch gate
- one review/fix/re-review loop only when a concrete blocker is found
- plan-scoped `.superpowers/sdd/...` ledger/workspace remains authoritative

Use `superpowers:executing-plans` when subagents/SDD are unavailable or when
delegation would duplicate context.

Use `superpowers:systematic-debugging` for unexplained failures.

Task commits are made directly on `main` as logical checkpoints. Do not push
without explicit approval.

### D. Verify

Invoke `superpowers:verification-before-completion` and run fresh acceptance commands.

Batch report must include:

```text
Batch:
Status:
Plan file:
Base SHA:
Head SHA:
Changed files:
Focused tests:
Integration tests:
Frontend/build/lint:
Runtime checks:
Security checks (if applicable):
Review verdict:
Known caveats:
git status --short:
git log --oneline BASE..HEAD:
```
### Active Context Maintenance

Before ending an implementation session or after completing a coherent
workstream, update:

```text
docs/superpowers/ACTIVE_BATCH_CONTEXT.md
```

Update only information that changed:

- completed workstream IDs
- current unfinished workstream
- newly established interfaces/paths
- relevant implementation decisions
- current blockers
- verification commands/results when useful

Do not use chat history as the only persistence mechanism.


### E. Human Checkpoint

After the batch gate passes:

1. mark only that batch `[x]`
2. summarize implementation/proof
3. update the final batch evidence
4. set `ACTIVE_BATCH_CONTEXT.md` status to `COMPLETE`
5. state the next batch and its required reading
6. stop for human authorization

Do not automatically prepare or execute the next batch.

After the user authorizes the next batch:

1. perform the new-batch preparation workflow
2. create the new implementation plan
3. replace the completed active context with the new batch context
4. begin execution only after preparation is complete


## Required-Reading Resolution Policy

`Required reading` identifies the canonical sources the primary controller
must reconcile when preparing a new batch.

It does **not** mean every implementation worker or reviewer must independently
read every listed file.

At batch preparation time, the controller must resolve the required reading
into the exact sections relevant to that batch and record those references in:

```text
docs/superpowers/ACTIVE_BATCH_CONTEXT.md
```

After the batch plan and active context exist:

- workers receive task-specific context
- reviewers receive review-specific context
- complete canonical files are opened only when necessary
- canonical authority remains unchanged

Do not routinely copy canonical specification text into:

- generated plans
- task packets
- reviewer packets
- active context

Use concise requirements plus exact canonical section references instead.

When a batch explicitly requires a complete document or full-project
reconciliation, the complete document must still be reviewed.



# 7. Ordered Implementation Batches

## Batch 0 — Repository Bootstrap and Reproducible Baseline

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md`
- `docs/reference/02_TECHNICAL_ARCHITECTURE.md`
- `docs/reference/06_TESTING_SECURITY.md`
- `docs/reference/08_DEPLOYMENT_OPERATIONS.md` startup/config sections

**Deliverables:**

- canonical monorepo skeleton
- root Maven reactor + backend aggregator
- four Spring Boot 4.1.1 service skeletons
- `platform-common` minimal infrastructure module
- Angular 22.1.5 + Material bootstrap
- Docker Compose skeleton with Neo4j/GDS, migrator placeholder, four services, web
- `.env.example`, `.gitignore`, `.editorconfig`
- request-ID/logging/ProblemDetail baseline
- Actuator health baseline
- root Makefile/scripts including initial `make verify`
- dependency locks/pinned image tags
- CI wrapper path

**Gate:**

- clean checkout builds Java reactor
- Angular install/build/test baseline succeeds
- Compose config validates
- Neo4j+GDS can start and respond
- four services can start/health with baseline config
- no secrets committed

**Next:** Batch 1.

---

## Batch 1 — Graph Schema, Migrations, Seeds, and Neo4j Test Harness

**Status:** [x]

**Required reading:**

- `docs/reference/02_TECHNICAL_ARCHITECTURE.md` migration/GDS sections
- `docs/reference/03_GRAPH_DATABASE_SPEC.md`
- `docs/reference/06_TESTING_SECURITY.md` Neo4j/concurrency sections
- `docs/reference/08_DEPLOYMENT_OPERATIONS.md` migrations/seeds

**Deliverables:**

- Neo4j-Migrations one-shot migrator
- node uniqueness constraints
- relationship-key uniqueness constraints
- search/index baseline
- canonical Spring Data Neo4j mappings/projections needed by first slices
- Testcontainers Neo4j integration harness with GDS where required
- demo/audit/load seed framework
- deterministic audit fixture loader
- empty DB migration verification

**Gate:**

- empty Neo4j migrates to latest
- rerun is safe/idempotent by migration tool semantics
- constraints/indexes verified
- relationship uniqueness test proves concurrent duplicate prevention strategy
- deterministic audit seed loads
- GDS version/function smoke succeeds

**Next:** Batch 2.

---

## Batch 2 — Authentication, Profile, Refresh Sessions, and TOTP 2FA Vertical Slice

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md` auth/profile/2FA
- `docs/reference/02_TECHNICAL_ARCHITECTURE.md` auth
- `docs/reference/03_GRAPH_DATABASE_SPEC.md` User/AuthSession/AuthChallenge/TOTP
- `docs/reference/04_API_SPEC.md` auth/users
- `docs/reference/05_FRONTEND_SPEC.md` auth/profile
- `docs/reference/06_TESTING_SECURITY.md` authentication/security

**Deliverables:**

- registration
- BCrypt password storage/policy
- RS256 JWT issuance/public-key validation foundation
- rotating opaque refresh session cookie
- logout/revocation
- auth/me
- profile GET/PATCH
- password change
- pending TOTP setup/QR/confirm
- 2FA login challenge
- disable 2FA with reauth
- account deletion cleanup transaction
- Angular register/login/2FA/profile-security flows
- rate limiting for sensitive auth endpoints

**Gate:**

- auth negative paths pass
- refresh replay rejected
- 2FA cannot be bypassed/replayed
- raw secrets/tokens absent from graph/logs
- browser storage shows no access token persistence
- USER identity/role validates across protected services
- account deletion leaves no orphan user auth/share nodes

**Next:** Batch 3.

---

## Batch 3 — Movie/Genre Catalog, Search, Related Movies, and Admin Vertical Slice

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md` browse/search/admin
- `docs/reference/03_GRAPH_DATABASE_SPEC.md` Movie/Genre/IN_GENRE/search/deletion
- `docs/reference/04_API_SPEC.md` movies/genres
- `docs/reference/05_FRONTEND_SPEC.md` movie/search/admin
- `docs/reference/06_TESTING_SECURITY.md` movie/search/security

**Deliverables:**

- Movie CRUD (ADMIN mutation)
- Genre CRUD with referenced-delete conflict
- year/date integrity
- movie list/detail
- title/genre/year/date/rating filters
- pagination/sort allowlist
- rating aggregate reads
- related-movie content query
- Angular home/browse/search/detail/admin movie+genre screens
- public anonymous browse/detail

**Gate:**

- ADMIN CRUD E2E/API
- USER mutation 403 backend-side
- combined filters against real Neo4j
- Cypher-looking search harmless
- unknown sort rejected
- releaseDate remains nullable/no fake dates
- Movie deletion cleanup tested

**Next:** Batch 4.

---

## Batch 4 — Rating Service CRUD and User Rating-History Facade

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md` rating semantics
- `docs/reference/03_GRAPH_DATABASE_SPEC.md` RATED
- `docs/reference/04_API_SPEC.md` ratings + user facade
- `docs/reference/05_FRONTEND_SPEC.md` rating/profile
- `docs/reference/06_TESTING_SECURITY.md` rating/concurrency

**Deliverables:**

- POST/GET/PUT/DELETE rating
- duplicate POST conflict
- idempotent DELETE
- rating history
- movie aggregate
- User Service `/users/me/ratings` read facade
- dedicated rating page
- movie detail rating controls
- profile rating history
- concurrent create tests

**Gate:**

- exactly one RATED under concurrency
- POST not upsert
- PUT requires existing
- JWT ownership
- aggregates correct
- browser rating CRUD E2E

**Next:** Batch 5.

---

## Batch 5 — Watchlist Vertical Slice

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md` watchlist
- `docs/reference/03_GRAPH_DATABASE_SPEC.md` WATCHLISTED
- `docs/reference/04_API_SPEC.md` watchlist
- `docs/reference/05_FRONTEND_SPEC.md` watchlist/movie cards
- `docs/reference/06_TESTING_SECURITY.md` watchlist/concurrency

**Deliverables:**

- add/list/remove
- idempotent behavior
- Angular watchlist page
- card/detail actions
- isolation/anonymous tests
- concurrency test

**Gate:**

- one WATCHLISTED under concurrency
- user isolation
- complete loading/empty/error UX
- no watchlist signal silently added to recommender

**Next:** Batch 6.

---

## Batch 6 — Recommendation Engine Core

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md` recommendations
- `docs/reference/03_GRAPH_DATABASE_SPEC.md` recommendation reads
- `docs/reference/06_TESTING_SECURITY.md` golden fixture/performance
- `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md`

**Deliverables:**

- custom Cypher repository
- actual GDS similarity
- bounded peer/candidate generation
- content scoring with negative ratings
- popularity/confidence
- hybrid weights/config
- cold start
- already-rated exclusion
- deterministic reasons
- golden-fixture integration tests
- EXPLAIN/PROFILE notes

**Gate:**

- deterministic rankings real Neo4j+GDS
- GDS truly invoked
- negative ratings correct
- cold-start strategies pass
- exclusion pass
- score bounded
- inputs/limits safe

**Next:** Batch 7.

---

## Batch 7 — Recommendation API, Filters, UI, and Movie Facade

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md` recommendation UX
- `docs/reference/04_API_SPEC.md` recommendation/facade
- `docs/reference/05_FRONTEND_SPEC.md` recommendations
- `docs/reference/06_TESTING_SECURITY.md` cross-service/failure
- `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md`

**Deliverables:**

- `/recommendations/me`
- filters/paging/sort
- strategy/signals/reason DTO
- Angular page/cards/filters
- Movie `/movies/recommended` RestClient facade
- bearer/request-ID propagation
- controlled 503
- equivalence tests

**Gate:**

- filters work API/UI
- explanations evidence-based
- peer identity absent
- facade equivalent
- movie browsing survives recommendation-service outage

**Milestone:** Core recommendation experience is demonstrable.

**Next:** Batch 8.

---

## Batch 8 — Recommendation Sharing and Recommendation-Service CRUD Completion

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md` sharing
- `docs/reference/03_GRAPH_DATABASE_SPEC.md` RecommendationShare
- `docs/reference/04_API_SPEC.md` share CRUD
- `docs/reference/05_FRONTEND_SPEC.md` sharing
- `docs/reference/06_TESTING_SECURITY.md` share privacy

**Deliverables:**

- share create/list/read/update/delete
- cryptographic public token + hash-only persistence
- ownership
- expiry/revocation
- public lookup
- share/copy/public Angular page
- optional profile share management

**Gate:**

- Recommendation Service has real CRUD resource
- raw token not stored
- owner isolation
- expired/revoked/random unavailable
- public private-data leakage absent

**Next:** Batch 9.

---

## Batch 9 — Frontend Completion, Accessibility, Responsive, Contract Reconciliation

**Status:** [x]

**Required reading:**

- `docs/reference/01_PRODUCT_SPEC.md`
- `docs/reference/04_API_SPEC.md`
- `docs/reference/05_FRONTEND_SPEC.md`
- `docs/reference/06_TESTING_SECURITY.md` frontend/accessibility

**Deliverables:**

- close route/page gaps
- consistent loading/empty/error
- responsive navigation/cards/forms/admin
- keyboard/labels/focus/rating accessibility
- refresh/error UX
- query-param filter state
- remove dead/duplicate paths
- DTO/OpenAPI/client reconciliation

**Gate:**

- all pages navigable
- mobile smoke
- critical keyboard flow
- no HttpClient/business-logic sprawl
- frontend matches API

**Next:** Batch 10.

---

## Batch 10 — Cross-Cutting Security Hardening and Observability

**Status:** [x]

**Required reading:**

- `docs/reference/02_TECHNICAL_ARCHITECTURE.md` security/logging
- `docs/reference/04_API_SPEC.md` cross-cutting
- `docs/reference/06_TESTING_SECURITY.md`
- `docs/reference/08_DEPLOYMENT_OPERATIONS.md` secrets/headers

**Deliverables:**

- centralized Problem Details
- request-ID propagation
- structured logging
- CORS
- Origin/Referer cookie checks
- Nginx headers
- URL/body/query/page bounds
- recommendation limits
- rate limits
- sensitive-log review
- scan scripts

**Gate:**

- JWT/role/ownership matrix
- Cypher/sort/XSS tests
- cookie attrs
- CORS/CSRF checks
- 429
- scans reviewed/no unaddressed actionable release blocker

**Next:** Batch 11.

---

## Batch 11 — Full Browser E2E and Failure-Mode Verification

**Status:** [x]

**Required reading:**

- `docs/reference/05_FRONTEND_SPEC.md`
- `docs/reference/06_TESTING_SECURITY.md` Playwright/failure
- `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md` runbook

**Deliverables:**

- core user E2E
- 2FA E2E
- recommendation-change E2E
- sharing E2E
- USER admin denial
- ADMIN CRUD
- XSS proof
- recommendation-service-down behavior
- Neo4j failure smoke where feasible

**Gate:**

- critical flows against real Docker services
- backend denial proven
- controlled failure UX

**Next:** Batch 12.

---

## Batch 12 — Audit Dataset, Cypher/GDS Evidence, Usability, Runbook

**Status:** [~]

**Required reading:**

- `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md`
- `docs/reference/06_TESTING_SECURITY.md` usability/evidence
- `docs/reference/08_DEPLOYMENT_OPERATIONS.md` audit seed

**Deliverables:**

- final deterministic audit seed
- `docs/audit/AUDIT_RUNBOOK.md`
- `GRAPH_DEMO.md`
- `RECOMMENDATION_EXPLANATION.md`
- `SECURITY_CHECKLIST.md`
- `TEST_EVIDENCE.md`
- `USABILITY_TEST.md`
- `STRESS_TEST.md` (bounded smoke profile; full load gate remains Batch 13)
- Cypher docs matching code
- real human usability walkthrough

**Gate:**

- official subject/audit mapped to implementation proof
- graph demo works
- recommendation explainable from real code/query
- usability executed, not placeholder

**Next:** Batch 13.

---

## Batch 13 — Load/Stress and Performance Integrity

**Status:** [~]

**Required reading:**

- `docs/reference/03_GRAPH_DATABASE_SPEC.md` load seed/invariants
- `docs/reference/06_TESTING_SECURITY.md` load/concurrency
- `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md` performance
- `docs/reference/08_DEPLOYMENT_OPERATIONS.md` seeds

**Deliverables:**

- deterministic load seed
- k6 smoke/audit profiles
- search/recommendation profiling
- post-stress relationship integrity checks
- `docs/audit/STRESS_TEST.md`

**Gate:**

- load completes without crash/corruption
- error rate target met or diagnosed/fixed
- relationship uniqueness intact
- app works afterward
- honest metrics recorded

**Next:** Batch 14.

---

## Batch 14 — Staging/Audit Deployment, HTTPS, Backup and Restore

**Status:** [ ]

**Required reading:**

- `docs/reference/06_TESTING_SECURITY.md` transport
- `docs/reference/08_DEPLOYMENT_OPERATIONS.md`
- `docs/reference/07_RECOMMENDATION_AUDIT_VALIDATION.md` startup

**Deliverables:**

- release images
- deterministic Neo4j+GDS packaging
- single-host Compose
- Nginx HTTPS/Let's Encrypt
- intended ports only
- secret mounting
- clean startup smoke
- Community-compatible offline dump backup
- restore
- tested restore cycle

**Gate:**

- valid HTTPS/redirect
- GDS/migrations/services healthy
- public exposure correct
- smoke pass
- backup and actual restore pass
- headers/cookies verified

**Next:** Batch 15.

---

## Batch 15 — Final Neo4flix Audit and Definition of Done

**Status:** [ ]

**Required reading:**

- all canonical specs for final reconciliation
- `docs/audit/*`

**Final proof:**

- clean `main` checkout
- empty-volume migration/startup
- `make verify`
- `make verify-all`
- four-service Docker
- graph/GDS
- register/login/2FA
- movie/genre CRUD + role denial
- search/filter
- rating CRUD + graph relation
- watchlist
- recommendation/cold start/filter/explanation
- Movie facade
- User rating-history facade
- RecommendationShare CRUD/public share
- malicious/security matrix
- k6
- HTTPS
- backup/restore
- final docs/code reconciliation
- broad final review

**Gate:**

All canonical Definition-of-Done items pass with fresh evidence. No material requirement conflict remains. Repository status is understood and no secrets/generated junk are accidentally tracked.

Then use `superpowers:finishing-a-development-branch`. Do not merge/push main without explicit approval.

# 8. Global Definition of Done

Neo4flix is complete only when all 16 batches are `[x]` and:

- official subject/audit mapped to evidence
- four required services meaningful
- graph visible/explainable
- relationship uniqueness under concurrency
- recommendation engine uses Neo4j/GDS and reacts to ratings
- JWT/2FA/security/HTTPS pass
- usability evidence real
- stress evidence real
- backup/restore proven
- docs match implementation
- final review + fresh verification pass
