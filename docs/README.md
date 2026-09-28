# Neo4flix documentation

This folder contains the practical guides, engineering reference, and audit
evidence for Neo4flix. Start with the root [README](../README.md) for the
project overview and local quick start.

New to the project or to its technologies? Read the ordered
[Learning Neo4flix](learning/README.md) guide. It begins with what the
application is and how a user interacts with it, then introduces the services,
Neo4j graph, Spring Boot annotations, Angular frontend, security,
recommendations, migrations, and tests.

## Use the project

- [Beginner learning guide](learning/README.md) — a chapter-by-chapter book
  explaining the project from first principles.
- [Local development](DEVELOPMENT.md) — prerequisites, environment setup,
  Compose lifecycle, seed data, and verification commands.
- [Neo4j bootstrap](../infra/neo4j/README.md) — graph database and GDS startup
  details.
- [Audit runbook](audit/AUDIT_RUNBOOK.md) — repeatable audit and evidence
  workflow.
- [Audit report](audit/AUDIT_REPORT.md) — latest full scan, evidence status,
  and prioritized remaining-work checklist.
- [01-edu audit checklist](audit/01-EDU_AUDIT_QUESTION_CHECKLIST.md) — the
  assignment questions mapped to project evidence.

## Evidence and operations

- [Final status](audit/FINAL_STATUS.md) — current completion snapshot and open
  release gates.
- [Test evidence](audit/TEST_EVIDENCE.md) — automated test and verification
  results.
- [Security checklist](audit/SECURITY_CHECKLIST.md) — security checks and known
  limitations.
- [Stress-test notes](audit/STRESS_TEST.md) — bounded load and rate-limit
  observations.
- [Recommendation explanation](audit/RECOMMENDATION_EXPLANATION.md) — how the
  graph ranking is explained to users.
- [Graph demo](audit/GRAPH_DEMO.md) — useful Neo4j queries for demonstrations.
- [Usability test](audit/USABILITY_TEST.md) — the remaining human-evidence
  checklist.

## Engineering reference

The numbered documents in [`docs/reference`](reference/) are the canonical
specifications:

1. [Master execution plan](reference/00_MASTER_EXECUTION_PLAN.md)
2. [Product specification](reference/01_PRODUCT_SPEC.md)
3. [Technical architecture](reference/02_TECHNICAL_ARCHITECTURE.md)
4. [Graph database specification](reference/03_GRAPH_DATABASE_SPEC.md)
5. [API specification](reference/04_API_SPEC.md)
6. [Frontend specification](reference/05_FRONTEND_SPEC.md)
7. [Testing and security](reference/06_TESTING_SECURITY.md)
8. [Recommendation and audit validation](reference/07_RECOMMENDATION_AUDIT_VALIDATION.md)
9. [Deployment and operations](reference/08_DEPLOYMENT_OPERATIONS.md)

The `superpowers/` directory is an internal implementation ledger containing
batch plans, design records, and active context. It is retained for traceability
and is not required for a normal local run.
