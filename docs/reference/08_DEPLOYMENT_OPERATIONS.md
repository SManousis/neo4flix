# Neo4flix — Deployment and Operations Specification

> **Authority:** This document owns environment topology, Docker startup, GDS packaging, TLS, ports, secrets, persistence, backup/restore, seed operations, smoke tests, failure recovery, deployment readiness, and operational Definition of Done.

## 1. Environments

1. local development
2. audit/staging
3. production-like single-host deployment

Same containers/config model where practical. No Kubernetes.

## 2. Reproducibility Baseline

Fresh machine with Git + Docker + Compose should run documented stack.

Canonical wrapper:

```bash
make dev-up
```

Local Java/Node optional convenience, not required baseline.

## 3. Docker Topology

```text
web (Nginx + Angular)
├── auth/users → user-service
├── movies/genres → movie-service
├── ratings → rating-service
└── recommendations/shares → recommendation-service

all business services → Neo4j + GDS
movie-service → recommendation-service facade

database-migrator runs before business services
```

## 4. Startup Order

```text
Neo4j
  ↓ health/query
GDS check
  ↓
database-migrator
  ↓ success
business services
  ↓ health
web/Nginx
```

Migration failure blocks normal service startup.

## 5. Ports

Local recommended:

- web 8080
- Angular dev 4200
- User 8081
- Movie 8082
- Rating 8083
- Recommendation 8084
- Neo4j Browser 7474
- Bolt 7687

Deployment exposes only 80/443 publicly.

## 6. Persistence

Neo4j `/data` on persistent volume.

Normal `docker compose down` does not delete data.

## 7. Reset

```bash
make reset-db
```

Clearly destructive; removes dev DB, starts clean, migrates. Does not silently seed.

## 8. GDS Development

Dev/test may use Docker plugin convenience mechanism.

Release/audit must not depend on internet plugin download at startup.

## 9. GDS Release Packaging

Deterministic image/build with compatible Neo4j 2026.07.x + GDS 2026.07.

Smoke:

```cypher
RETURN gds.version();
```

Missing GDS blocks recommendation readiness.

## 10. Nginx

- serve Angular
- TLS
- HTTP→HTTPS
- `/api/v1` reverse proxy
- request forwarding/ID
- security headers

Not application auth.

## 11. Target

One Linux VM/host + Docker Compose. No cluster required.

## 12. Public Exposure

Public 80/443 only.

Do not expose service ports, Bolt, or Browser publicly.

Audit Browser via local environment, localhost binding, or secure tunnel.

## 13. HTTPS

Nginx + Let's Encrypt/Certbot for internet staging/audit.

Checks:

- valid cert
- redirect
- renewal config/dry run
- Secure refresh cookie
- no mixed content

## 14. Secrets

Committed:
- `.env.example`
- non-secret config

Never committed:
- `.env`
- production Neo4j password
- JWT private key
- TOTP encryption key
- TLS private keys
- tokens
- real passwords

Prefer mounted read-only key material.

## 15. `.env.example`

Example names:

```text
NEO4J_URI=bolt://neo4j:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=change-me
JWT_PRIVATE_KEY_PATH=/run/secrets/jwt-private.pem
JWT_PUBLIC_KEY_PATH=/run/secrets/jwt-public.pem
TOTP_ENCRYPTION_KEY=change-me
FRONTEND_BASE_URL=http://localhost:8080
DEMO_ADMIN_PASSWORD=change-me
DEMO_USER_PASSWORD=change-me
```

Placeholders are not production credentials.

## 16. Images

Spring multi-stage: Maven/JDK build → Java 21 runtime, non-root where practical.

Frontend: Node build → Nginx runtime.

No unnecessary source/build tooling in runtime images.

## 17. Version Policy

- no `latest`
- pin base images
- pin Neo4j/GDS pair
- commit npm lock
- BOM-managed Spring compatibility

Upgrade Neo4j/GDS together.

## 18. Migrations

One-shot migrator after DB health.

Prove:

- empty→latest
- latest rerun safe
- failure blocks dependent startup

Backup before incompatible production-like migration.

## 19. Seed Commands

```bash
make seed-demo
make seed-audit
make seed-load
```

Demo: UI-friendly.

Audit: deterministic recommendation clusters.

Load: synthetic representative scale.

Production-like deployment never auto-seeds fake data.

## 20. Demo Credentials

Seed roles/usernames may be deterministic, passwords supplied by environment and hashed.

No hardcoded `admin/admin`.

TOTP audit account enrolls during demo; no permanent committed secret.

## 21. Optional MovieLens Import

Developer tooling only; app/audit work offline without it.

If used:

- preserve external source/id
- map year/genres honestly
- never invent exact dates
- explicitly document any rating-scale conversion

## 22. Community Backup

Do not claim Enterprise online backups.

Use maintenance/offline dump:

1. stop/quiesce Neo4j
2. `neo4j-admin database dump`
3. store timestamped dump outside live volume
4. restart/smoke

Wrapper:

```bash
make backup
```

## 23. Restore

```bash
make restore BACKUP=/path/file.dump
```

Require explicit path and destructive warning, stop DB, load into expected clean target, restart, migrate if needed, smoke.

## 24. Restore Proof

```text
known data → backup → clear test DB → restore → start → verify known data
```

A dump file alone is not backup proof.

## 25. Clean Stack Smoke

From empty volumes:

1. build
2. start Neo4j
3. verify GDS
4. migrate
5. services healthy
6. web reachable
7. register/login
8. get movies
9. create rating
10. recommendations
11. watchlist

Admin additionally CRUDs a movie.

## 26. Failure Recovery

### Service restart
No persistent corruption.

### Neo4j restart
Data remains; no reseed; migration state safe.

### Recommendation Service down
Normal movie browse/search/detail continue; facade 503.

### Migration failure
Visible failure, no partial “healthy” app.

## 27. Logging

Structured deployed logs + request IDs; propagate service-to-service.

No ELK/Prometheus/Grafana requirement for MVP.

## 28. Security/Operational Scan

Final gate:

- dependency review
- secret scan
- image scan
- HTTPS/header check
- exposed-port check

Repository entry points:

- `pwsh -NoProfile -File scripts/test-security-headers.ps1` checks the Nginx header policy.
- `make security` runs the dependency and tracked-secret review plus any installed gitleaks/Trivy scanners without printing `.env` values.

## 29. Staging/Audit Gate

- [ ] clean Compose startup
- [ ] migrations
- [ ] GDS
- [ ] four services healthy
- [ ] intended public ports only
- [ ] HTTPS
- [ ] Secure refresh cookie
- [ ] core smoke
- [ ] admin smoke
- [ ] deliberate audit seed
- [ ] k6
- [ ] backup
- [ ] restore test

## 30. Operational Definition of Done

- clean checkout reproducible
- empty migration
- pinned dependencies/images
- deterministic GDS
- HTTPS
- no improper public service/DB exposure
- persistence survives restart/down
- backup/restore proven
- demo/audit/load seeds deterministic
- failure paths controlled
- docs match commands
