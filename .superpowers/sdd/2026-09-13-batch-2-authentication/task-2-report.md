# Task 2 Report — Registration, Login, Refresh Rotation, and Profile APIs

## Status

Implemented the password-authentication and self-profile baseline on top of Task 1's RS256 `JwtTokenService`. TOTP challenge verification, endpoint rate limiting, and deletion cleanup remain deliberately deferred to Task 3.

## Delivered behavior

- `POST /api/v1/auth/register` creates a `USER`, normalizes email for uniqueness, stores a BCrypt hash, returns only `PublicUser`, and creates no session.
- `POST /api/v1/auth/login` returns an access JWT plus opaque refresh cookie for non-2FA accounts. Active-2FA accounts return the `RequiresTwoFactor` seam and never receive a normal access/refresh session.
- `POST /api/v1/auth/refresh` consumes the cookie value, hashes it, atomically revokes the live graph session and creates its replacement, then returns a new access JWT and cookie. Replayed and revoked values fail.
- `POST /api/v1/auth/logout` revokes the presented session when present and always clears the refresh cookie.
- `GET /api/v1/auth/me`, `GET /api/v1/users/me`, and `PATCH /api/v1/users/me` derive the user solely from JWT `sub`; the patch DTO exposes only `displayName`.
- `POST /api/v1/auth/change-password` requires the current password, applies the password policy, and stores a new BCrypt hash. Active-2FA accounts stop at the explicit Task 3 second-factor seam.
- Refresh cookies retain `Secure`, `HttpOnly`, configured `SameSite`, bounded path, and bounded TTL attributes on issue and clear.
- All custom Cypher data is passed through named parameters. Controllers return API records, never `UserNode`, `AuthSessionNode`, or `AuthChallengeNode`.

## TDD evidence

### RED

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.1'
.\mvnw.cmd -q -pl backend/user-service -am '-Dtest=PasswordPolicyTest,AuthApplicationServiceTest,RefreshCookieFactoryTest,AuthDtoValidationTest,AuthNeo4jIntegrationIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Observed result: failed during `user-service` test compilation for the intended missing `PasswordPolicy`, DTO, application-service, cookie-factory, and repository types. This established the test suite before production implementation.

### GREEN — focused unit tests

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.1'
.\mvnw.cmd -q -pl backend/user-service -am '-Dtest=PasswordPolicyTest,AuthApplicationServiceTest,RefreshCookieFactoryTest,AuthDtoValidationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Observed result: exit 0; 11 focused tests passed. One initial fixture assertion was corrected from two to three violations because an oversized malformed email independently violates both `@Size` and `@Email`.

### GREEN — live Neo4j lifecycle

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.1'
.\mvnw.cmd -q -pl backend/user-service -am '-Dtest=AuthNeo4jIntegrationIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Observed result: exit 0 against `neo4j:2026.07.1-community`. The test proved login token A rotates to B, A replay is rejected, logout revokes B, and graph properties contain neither the raw password nor raw refresh values. Stored refresh hashes are 43-character base64url SHA-256 values; the password value is BCrypt.

### GREEN — reactor verification

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.1'
.\mvnw.cmd -q -pl backend/user-service -am verify
```

Observed result: exit 0 after a RED application-context regression exposed final nested repository classes that Spring could not proxy. Removing only the unnecessary `final` modifiers resolved the root cause; the exact reactor verify then passed.

## Security inspection

- Source scan found no `Logger`, `log.*`, `System.out`, `System.err`, or `printStackTrace` calls in Task 2 production/test sources.
- No raw password, refresh token, or challenge token is passed into a persistence query.
- Registration runs a real BCrypt encode; unknown-email login still performs BCrypt verification against a dummy hash to reduce timing-based account enumeration.
- Refresh material uses 32–64 `SecureRandom` bytes and only SHA-256 hashes enter Neo4j.
- Timestamps are sourced from a UTC `Clock` and bound to Neo4j as UTC zoned values.

## Deferred seam / concerns

- Active-2FA login currently returns `202` with the `requiresTwoFactor` marker but no challenge material; Task 3 must replace this seam with the one-time hashed challenge implementation.
- Active-2FA password change is rejected with `SECOND_FACTOR_REQUIRED`; Task 3 must validate the submitted six-digit code before allowing the mutation.
- Register/login/refresh rate limiting, expected-Origin checks for cookie mutations, TOTP, and account-deletion orphan cleanup remain Task 3 scope.
- Verification ran on JDK 26 while compiling with Maven release 21. Maven/Jansi, Mockito, and Byte Buddy emitted forward-compatibility warnings; these are environment/toolchain warnings, not application failures, and do not constitute Java 21 runtime proof.

## Review fix round 1

### Production bean graph and route smoke

RED command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.1'
.\mvnw.cmd -q -pl backend/user-service -am '-Dtest=AuthProductionContextIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Observed result: eight HTTP checks returned `401` because the configured-key context had no `AuthApplicationService`, `AuthController`, or `ProfileController`. The bean-level `@ConditionalOnBean(JwtTokenService.class)` was being evaluated before the signer bean was available.

Fix: the signer, application service, and controllers now use the same nonblank-private-key condition from `JwtKeyConfiguration`. The configured-key context creates the complete production graph while the existing no-key health context remains supported.

GREEN proof: `AuthProductionContextIT#configuredProductionContextMapsAndServesCoreAuthAndProfileRoutes` exited 0 and exercised registration, login, refresh, `/auth/me`, and `/users/me` through the real HTTP server, configured RSA keys, security filter chain, and live Neo4j.

### Password policy HTTP contract

The production-context test parameterizes seven violations: too short, missing uppercase, missing lowercase, missing digit, missing special, whitespace, and excessive length. Each MockMvc request now proves HTTP `400`, code `VALIDATION_FAILED`, and a nonempty `fieldErrors.password` value.

Fix: `PasswordPolicy` raises a specific policy violation and the high-priority auth advice maps it to the same field-level Problem Details contract as Jakarta request validation.

GREEN proof: `AuthProductionContextIT#everyPasswordPolicyViolationReturnsFieldLevelBadRequest` exited 0 for all seven cases.

### Concurrent refresh compare-and-set

RED command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.1'
.\mvnw.cmd -q -pl backend/user-service -am '-Dtest=AuthNeo4jIntegrationIT#concurrentRefreshOfOneTokenHasExactlyOneWinner' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Observed result: `[true, true]`; both simultaneous refreshes passed the pre-lock `revokedAt IS NULL` predicate and created replacements.

Fix: the rotation query now performs a self-dependent, write-locked compare-and-set on `rotatedToSessionId`. Only the request whose unique replacement ID claims the old session proceeds to revoke it and create the linked replacement; all competitors return no row and are rejected.

GREEN proof: the same concurrent test exited 0, observed exactly one success and one `InvalidRefreshTokenException`, and counted exactly one session with `rotatedFromSessionId`.

### Combined fix verification

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.1'
.\mvnw.cmd -q -pl backend/user-service -am '-Dtest=PasswordPolicyTest,AuthApplicationServiceTest,RefreshCookieFactoryTest,AuthDtoValidationTest,AuthProductionContextIT,AuthNeo4jIntegrationIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Observed result: exit 0 for the complete focused unit, production HTTP-context, and live Neo4j suite.

Final reactor command `./mvnw -q -pl backend/user-service -am verify` also exited 0 after the fix. Aggregated retained user-service Surefire reports contained 27 tests with zero failures, errors, or skips (including the explicitly invoked `*IT` classes).
