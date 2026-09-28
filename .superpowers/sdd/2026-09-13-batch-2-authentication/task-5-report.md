# Task 5 Report — Angular Profile/Security UI and Browser Contracts

## Status

Implemented the authenticated `/profile` flow on top of the reviewed Task 4 auth store, guard, and interceptor contracts.

## Delivered

- Added a typed `ProfileApiService` for current-user profile read/update, password change, TOTP setup/confirm/disable, and reauthenticated account deletion.
- Added typed TOTP, password-change, and reauthentication request/response models.
- Added a lazy, `authGuard`-protected `/profile` route.
- Added accessible Material Profile, Security, Ratings, and Account sections with loading, empty, success, validation, and bounded error states.
- Kept email read-only and sent only `displayName` in profile updates.
- Required password policy confirmation for password changes and a six-digit TOTP code when the loaded profile has 2FA enabled.
- Kept QR and manual enrollment material in a component signal only; cleared it after successful confirmation, cancellation, any navigation start, component destruction/logout navigation, and account deletion.
- Required current-password reauthentication plus an exact typed `DELETE` confirmation for account deletion; a six-digit TOTP code is additionally required when 2FA is enabled.
- Cleared the in-memory auth store and returned to sign-in after successful deletion.
- Added ratings-history and watchlist navigation links.

## TDD Evidence

RED:

- `npm test -- --watch=false` failed at compile time because `ProfileComponent` and `ProfileApiService` did not exist after the initial component/API/route contracts were added.
- `npm test -- --watch=false --include src/app/features/profile/profile.component.spec.ts` then failed the active-2FA contract because password change incorrectly submitted with `code: null`.
- A further focused RED run proved an in-flight setup response could restore enrollment data after navigation began; the setup stream now terminates on navigation/destruction.

GREEN:

- Focused profile/API/route run: 3 files passed, 12 tests passed.
- Focused active-2FA and challenge-replay run: 2 files passed, 11 tests passed.
- The contracts cover profile load/edit, read-only email, password change and sensitive-field clearing, TOTP setup/confirm/disable, enrollment cleanup on confirmation/navigation, no Web Storage writes, active-2FA reauthentication requirements, explicit deletion confirmation, post-delete auth clearing/navigation, exact API methods/payloads, refresh-cookie clearing credentials, protected routing, ratings/watchlist links, and prevention of client-side challenge replay.

## Browser Contract Handling

Playwright was not run or added. `rg -n '\"(@playwright/test|playwright)\"' frontend/package.json frontend/package-lock.json` found no pinned browser tooling, and Task 5 prohibits adding floating dependencies.

Deterministic Angular contracts cover the browser-observable behavior available without Playwright:

- access tokens and TOTP enrollment material do not write to `localStorage` or `sessionStorage`;
- a successfully consumed 2FA challenge cannot be replayed from client memory;
- anonymous navigation to `/profile` redirects to sign-in while preserving the return URL;
- concurrent refresh, one-retry, refresh failure, and same-origin API scoping remain covered by the existing interceptor suite;
- account deletion uses credentialed HTTP so the backend refresh-cookie clearing response is accepted.

Refresh-cookie `Secure`, `HttpOnly`, `SameSite`, path, server-side challenge one-time consumption, and live protected-route navigation remain environment/browser acceptance checks because JavaScript component tests cannot inspect an HttpOnly cookie or execute a live backend flow.

## Verification

- Focused Task 5 contracts: `npm test -- --watch=false --include src/app/features/profile/profile.component.spec.ts --include src/app/core/profile-api.service.spec.ts --include src/app/core/auth.store.spec.ts --include src/app/app.routes.spec.ts` — PASS: Docker contract 1/1; Angular 4 files, 18 tests.
- `npm test -- --watch=false` — PASS: Docker contract 1/1; Angular 12 files, 44 tests.
- `npm run lint` — PASS: zero warnings/errors.
- `npm run build` — PASS: production bundle generated; initial bundle 423.15 kB.
- Production storage scan: `rg -n "localStorage|sessionStorage" frontend/src -g "!*.spec.ts"` — PASS: no matches.
- Browser-tooling pin scan — no Playwright dependency found; documented skip above.
- `git diff --cached --check` and `git diff --check` — PASS.

## Review Fix Round 1

- Separated profile and security success/status signals from API error signals; failures now render in `.error` regions with `role="alert"`, while successful actions retain `.status` and `role="status"`.
- Added associated `mat-error` output for required current-password, password-confirmation, and active-2FA code fields in password change; enrollment confirmation; 2FA disable; and account deletion forms.
- Added `HttpErrorResponse` component contracts for profile load/edit, password change, TOTP setup/confirm/disable, and deletion, plus loading, empty, accessible-error, and field-validation states.
- RED: the focused profile suite reported 6 failing and 10 passing tests because profile/security API failures had no alert/error element and required credential/code fields lacked validation output.
- GREEN: focused profile suite PASS, 1 file and 16 tests; focused Task 5 contracts PASS, 4 files and 26 tests; full suite PASS, Docker contract 1/1 and Angular 12 files/52 tests.
- `npm run lint` — PASS: zero warnings/errors.
- `npm run build` — PASS: production bundle generated; initial bundle 423.15 kB.
- Production storage scan and `git diff --check` — PASS.

## Concerns

- A live Playwright/browser run is deferred until an exact browser toolchain is pinned and the local authentication stack is available. Cookie flags and server-side challenge replay must be rechecked in Task 6 acceptance.
- The ratings link targets the canonical future `/ratings` destination; its feature route is not part of Task 5.
