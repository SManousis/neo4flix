# Task 3 Report: TOTP Authentication Hardening and Account Deletion

## Status

Complete. The resumed in-progress implementation was preserved and verified without discarding prior work.

## Delivered behavior

- RFC 6238 TOTP enrollment uses six-digit, 30-second SHA-1 codes and a bounded verification window.
- Setup returns an `otpauth://` URI plus a ZXing-generated PNG data URL, while Neo4j stores only AES-256-GCM encrypted pending enrollment material with a ten-minute expiry.
- Confirmation rejects malformed, wrong, expired, missing, and already-consumed enrollment state; success atomically promotes the encrypted secret, clears pending fields, and enables 2FA.
- Password login for an active-2FA account returns only a short-lived opaque challenge. Raw challenge values are never persisted; SHA-256 hashes are unique, and successful verification consumes the challenge once before issuing an access token and rotating refresh session.
- Disable, password change, and account deletion reauthenticate with the current password and require a current TOTP code when 2FA is active.
- Account deletion explicitly removes owned recommendation shares, refresh sessions, and authentication challenges before deleting the user in one Neo4j transaction.
- Register, login, refresh, and all 2FA endpoints have bounded in-memory rate limiting with `429` and `Retry-After`; refresh/logout reject missing or hostile Origin/Referer values.
- V006 adds unique constraints for `AuthSession.refreshTokenHash` and `AuthChallenge.tokenHash`; migrator expectations and migration verification were updated to six migrations and sixteen named schema objects.

## Verification evidence

- Focused unit tests: `TotpServiceTest`, `SecretEncryptionServiceTest`, `RateLimitFilterTest`, and `AuthApplicationServiceTest` — passed.
- Real Neo4j/HTTP tests: `AuthNeo4jIntegrationIT` and `AuthProductionContextIT` — 14 tests passed. Coverage includes pending expiry, confirm failure/success, active-login gating, wrong/expired/replayed challenge rejection, concurrent one-time consumption, reauthentication, no raw secret/password/token persistence, origin rejection, explicit orphan cleanup, and live V006 uniqueness enforcement.
- User-service reactor: `mvn -q -pl backend/user-service -am verify` — passed.
- Full backend reactor: `mvn -q -f backend/pom.xml verify` — passed across platform-common, user, movie, rating, and recommendation modules.
- Migration contract: `scripts/test-migrations.ps1` — passed with 6 versions and 16 named schema objects.
- Final repository checks: `git diff --check` and targeted sensitive-log inspection are recorded immediately before commit.

## Concerns

- Verification used the installed JDK 26 because the requested Java 21 JDK was not present in `JAVA_HOME`; Maven emitted upstream deprecation/dynamic-agent warnings, but all builds and tests exited successfully.
- The rate limiter is intentionally process-local. Horizontal deployments require a shared limiter at the gateway or a distributed backing store to enforce a cluster-wide quota.

## Review Fix Round 1: Encoded Path Matching

The review probe demonstrated that raw `HttpServletRequest.getRequestURI()` comparison allowed percent-encoded route characters to bypass both the login quota and refresh/logout Origin policy. The filter now uses Spring's parsed `RequestPath` and compares decoded path segments, so encoded spellings share the canonical endpoint key and cookie-route policy. Ambiguous path forms are rejected before dispatch: matrix parameters, dot segments, duplicate separators, decoded slash/backslash separators, and control characters return `400`.

Regression evidence:

- RED: the new MockMvc encoded login/2FA quota cases expected `429` but received `204`; encoded refresh/logout cases expected `403` but received `204`.
- GREEN: `RateLimitFilterTest` passed all 6 tests, including encoded login, refresh, logout, 2FA, traversal, separator, duplicate-slash, and matrix-parameter cases.
- Live HTTP/Neo4j: `AuthProductionContextIT` passed all 12 tests.
- Full backend reactor: `mvn -q -f backend/pom.xml verify` passed across all backend modules.
