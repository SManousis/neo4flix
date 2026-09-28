# Batch 8 Verification — Recommendation Sharing and CRUD

Date: 2026-09-15  
Branch: `main`  
Scope: recommendation-share CRUD, secure public links, movie cleanup, and Angular share flows.

## Automated verification

| Area | Command/result |
| --- | --- |
| Recommendation Service reactor | `JAVA_HOME="C:\\Program Files\\Java\\jdk-26.0.1" .\\mvnw.cmd -B -pl backend/recommendation-service -am test` — BUILD SUCCESS; 39 tests, 0 failures/errors/skips |
| Full Maven reactor | `JAVA_HOME="C:\\Program Files\\Java\\jdk-26.0.1" .\\mvnw.cmd -B test` — BUILD SUCCESS; 129 tests across all modules, 0 failures/errors/skips |
| Frontend unit suite | `npm test -- --watch=false` — 24 files, 90 tests passed |
| Frontend lint | `npm run lint` — passed with zero warnings/errors |
| Frontend production build | `npm run build` — passed; Angular bundle generated at `frontend/dist/frontend` |
| Compose interpolation | `docker compose --env-file .env.example -f infra/compose.yml -f infra/compose.dev.yml config --quiet` — passed; no local `.env` was changed or printed |
| Diff hygiene | `git diff --check` — passed |

The focused Batch 8 frontend suite (share API, public page, recommendations action, and route registration) passed 16 tests before the full-suite run. The final full suite includes those tests.

## Browser verification

The repository currently contains no share-specific Playwright spec. Existing authenticated recommendation specs are guarded by `NEO4FLIX_E2E_EMAIL` and `NEO4FLIX_E2E_PASSWORD`; those credentials were not present in this environment, so those tests are intentionally skipped. Compose services were not started and no credentials or secrets were created for this verification pass.

## Security and contract evidence

- Public tokens are generated with `SecureRandom` and URL-safe Base64; only the lowercase SHA-256 token hash is persisted. The raw token is returned only by successful creation.
- All share repository queries bind owner, share, movie, token-hash, timestamps, and expiry values as parameters. Public lookup filters revoked and expired shares in the query and projects only safe movie/genre fields.
- Owner CRUD derives identity from the authenticated JWT `sub`; the request body and query string cannot select an alternate owner. Missing, expired, revoked, or random tokens map to the same not-found response.
- Anonymous access is limited to `GET /api/v1/shares/**`; owner CRUD remains authenticated. Public projections omit creator identity, token hashes, ratings, watchlists, vectors, peer identities, and other private recommendation data.
- Movie deletion removes inbound `RecommendationShare` nodes before detaching the movie, preventing orphaned public links.
- Neo4j temporal projections are normalized from `Instant`, `ZonedDateTime`, or `OffsetDateTime`; public genre/rating aggregation counts distinct ratings.
- Angular keeps the raw token out of logs and state beyond the returned public path, uses encoded public-token URLs, renders only safe movie fields, displays a paste-ready absolute URL, and provides a Clipboard API fallback.

## Source commits

- `1ca4174` — add recommendation share token contracts
- `526ec70` — add parameterized recommendation share repository
- `10bc4a1` — expose recommendation share CRUD
- `80d949c` — secure public shares and clean deleted movies
- `a31a785` — add anonymous recommendation share page
- `2705533` — add recommendation share action
- `3a6a985` — allow recommendation share repository proxying
