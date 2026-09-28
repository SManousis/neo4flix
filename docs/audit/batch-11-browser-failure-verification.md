# Batch 11 Browser and Failure-Mode Verification

Date: 2026-09-15  
Branch: `main`  
Entry point: rebuilt Docker Compose stack at `http://localhost:8080`

## Stack evidence

- Rebuilt all Compose images with `docker compose ... up -d --build --wait --wait-timeout 600`.
- Neo4j volume was preserved; no reset or deletion was performed.
- Neo4j, migrator, user, movie, rating, recommendation, and web services became healthy.
- `GET /` returned `200` through Nginx with request ID and all configured browser security headers.

## Playwright evidence

Command: `NEO4FLIX_E2E_BASE_URL=http://localhost:8080 npm run e2e` from `frontend`.

- 9 tests discovered.
- 8 passed: anonymous catalog browse, USER admin denial, login/profile/logout, rating lifecycle, recommendation load/filter/outage flow, watchlist lifecycle, authenticated sharing/public lookup, and 2FA enrollment/login verification.
- 1 skipped because no disposable ADMIN E2E credentials were configured: ADMIN catalog CRUD.
- No browser test failed.
- The Playwright suite runs with one worker because these stateful auth flows intentionally share the local Nginx client identity and production auth rate-limit bucket.
- The credential-gated ADMIN contract was then run independently with a freshly registered, disposable user promoted only for this verification: `e2e/admin-catalog.spec.ts` passed (`1 passed`, 4.4s). The fixture cleanup query returned `remaining 0`; no test data or credentials were retained.

## Failure-mode limitations

- With `recommendation-service` stopped, `GET /api/v1/movies?page=0&size=1` continued to return `200`; the service was then restored and all six Compose services returned healthy.
- With Neo4j stopped, an authenticated catalog probe returned `500` with request ID `aa41cd3c5b18d775c049a2c8b6868a87`; Neo4j was restarted to healthy, the disposable probe user was deleted, and no volume was reset.
- The first live sharing smoke exposed a Neo4j temporal-binding defect; converting share timestamps to UTC `ZonedDateTime` fixed it. Repository tests, a live API smoke, and the new Playwright sharing test all pass after the fix.
- The 2FA browser scenario enrolls a disposable user from the returned `otpauth://` URI, verifies the password-only challenge, completes the current TOTP code, and deletes the user with reauthentication.
- k6 stress execution was not performed in this pass.

## Batch disposition

Batch 11 browser and failure-mode verification is complete for the exercise scope: the full default suite passed 8 tests with only the expected credential-gated skip, and the ADMIN CRUD contract passed separately with a disposable promoted fixture. k6 stress execution remains an optional follow-up and is not part of the Batch 11 gate.
