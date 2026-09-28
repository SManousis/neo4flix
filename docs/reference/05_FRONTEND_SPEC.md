# Neo4flix — Frontend Specification

> **Authority:** This document owns Angular routes, pages, navigation, components, forms, client state, API interaction, loading/empty/error behavior, responsive design, accessibility, and browser-level workflows.

## 1. Frontend Goals

The Angular UI must make all assignment-required behavior obvious and usable while remaining simple enough to audit.

Design principles:

- movie-first visual browsing
- clear recommendation context
- no hidden required workflow
- loading/empty/error states everywhere
- backend authorization remains authoritative
- desktop-first but responsive
- keyboard-accessible critical controls

## 2. Stack

- Angular 22.1.5
- Angular Material 22.1.5
- TypeScript 6.0.x
- SCSS
- standalone components
- Reactive Forms
- Signals/services for app state
- Vitest
- Playwright

No NgRx/Tailwind/Bootstrap unless canonical architecture is revised.

## 3. Route Map

```text
/
├── /login
├── /register
├── /auth/2fa
├── /share/:publicToken
│
├── /home
├── /movies
├── /movies/:id
├── /movies/:id/rate
├── /search
├── /recommendations
├── /watchlist
├── /profile
│
└── /admin
    ├── /admin/movies
    ├── /admin/movies/new
    ├── /admin/movies/:id/edit
    └── /admin/genres
```

`/` redirects based on auth state.

## 4. Route Guards

Use functional guards where appropriate:

- `authGuard`
- `anonymousOnlyGuard`
- `adminGuard`

Guards improve navigation/UX only. Backend authorization remains authoritative.

## 5. Top Navigation

Authenticated USER:

```text
Neo4flix | Home | Movies | Recommendations | Watchlist | Search | Profile | Logout
```

ADMIN additionally sees `Admin`.

On small screens collapse navigation into a Material menu/drawer while keeping core destinations reachable.

## 6. Authentication Client State

`AuthStore`/service owns:

- current public user
- in-memory access token
- authentication bootstrap status
- refresh-in-flight coordination

On app startup:

```text
no access token in memory
  ↓
POST refresh using HttpOnly cookie
  ↓
success → store access token/user
failure → anonymous state
```

Never persist access token in local/session storage.

## 7. Auth HTTP Interceptor

Responsibilities:

- attach bearer token to protected API requests
- on token-expired 401, coordinate a single refresh request
- retry eligible original request once
- prevent recursive refresh loops
- clear auth state and route to login when refresh fails

Do not retry arbitrary 403/validation errors.

## 8. Login Page

Fields:

- email
- password

Behavior:

- client validation for UX
- generic invalid-credentials message
- loading state
- 429 message
- if response requires 2FA, store challenge temporarily in memory and navigate `/auth/2fa`

Do not expose whether an email exists.

## 9. Registration Page

Fields:

- email
- display name
- password
- password confirmation

Show password policy while typing. On success navigate to login with a success message.

## 10. 2FA Login Page

Route: `/auth/2fa`.

- six-digit code control
- submit
- back/cancel to login
- challenge-expired handling
- no “resend” because TOTP is authenticator-based

Direct navigation without active challenge routes to login.

## 11. Home Page

Sections:

- search bar
- Recommended for You
- Popular Movies
- Recently Released
- Browse more

Recommendation section adapts:

- zero ratings: popularity + “rate movies to improve” prompt
- few ratings: content/popularity
- mature history: hybrid

Never show a blank recommendation area without explanation.

## 12. Movie Card

Reusable component:

- poster/placeholder
- title
- release year
- bounded genre chips
- average rating/unrated state
- detail link
- watchlist action for authenticated users

Keyboard reachable.

## 13. Movies/Browse Page

Route `/movies`.

Controls:

- title search
- genre
- year range
- minimum rating
- sort
- pagination

Meaningful filters should be reflected in URL query parameters.

## 14. Search Route

`/search` may reuse the movie-list feature with query-focused presentation.

There is one canonical Movie search API; no separate frontend paths for title/genre/year.

## 15. Movie Detail Page

Display:

- poster/placeholder
- title
- overview
- release year
- exact date only if known
- runtime only if known
- genres
- average/rating count
- current user rating
- watchlist state
- related movies

Actions:

- rate/edit/remove rating
- watchlist toggle
- share

Anonymous viewers see public details and login prompts for personalized actions.

## 16. Dedicated Rating Page

Route `/movies/:id/rate`.

Required by assignment.

Use accessible 1–5 star/radio semantics.

States:

- no rating → create with POST
- existing rating → update with PUT/delete
- movie not found
- unauthenticated → login/guard

Do not treat POST as upsert.

## 17. Watchlist Page

Route `/watchlist`.

- list current user's movies
- remove
- open detail
- paginate if useful
- empty state with browse action

Optimistic UI is allowed only if failures restore state.

## 18. Recommendations Page

Route `/recommendations`.

Controls:

- genre
- year range
- minimum average rating
- sort

Each result may show:

- movie summary
- recommendation score
- reason
- view
- watchlist
- share

Show active strategy where useful:

- Popularity
- Content + Popularity
- Hybrid

Do not expose peer identities/private signal detail.

## 19. Recommendation Reasons

Canonical reason types:

- `SIMILAR_USERS`
- `GENRE_MATCH`
- `POPULAR`

Render server-provided reason; frontend must not invent algorithm claims.

## 20. Sharing UX

```text
Share button
  ↓
create RecommendationShare
  ↓
show/copy public URL
```

Use Clipboard API with fallback.

Optional profile section may list/revoke own shares.

## 21. Public Share Page

Route `/share/:publicToken`.

Anonymous-friendly.

Displays:

- shared movie summary
- “Shared with you through Neo4flix”
- browse/register/movie links

Does not display creator private information.

Expired/revoked/random token → generic unavailable/not-found.

## 22. Profile Page

Sections:

### Profile
- display name
- email read-only

### Security
- change password
- 2FA status
- setup QR
- confirm code
- disable with password+TOTP

### Ratings
- rating history
- movie link
- edit/remove

### Account
- delete with strong confirmation/reauthentication

## 23. 2FA Enrollment UI

1. Enable 2FA
2. call setup
3. show QR/manual code only for active setup
4. enter current TOTP
5. confirm
6. on success remove secret/QR from UI state

Never persist enrollment secret in browser storage.

## 24. Admin Area

### Admin landing
Simple navigation/status, not analytics-heavy.

### Movies
- list/table
- search
- create
- edit
- delete confirmation

### Movie form
- title
- overview
- release year
- optional exact date
- runtime
- poster URL
- genre multi-select

Year/date mismatch produces form error; API remains authoritative.

### Genres
- list
- create
- rename
- delete
- referenced-delete conflict message

## 25. Angular Material

Prefer Material for:

- toolbar/menu/drawer
- cards
- form fields
- select/chips
- paginator
- dialogs
- snackbars
- progress
- buttons/icons
- tables

Avoid custom UI primitives where Material already provides suitable accessible controls.

## 26. Typed API Clients

Recommended:

```text
core/api/auth-api.service.ts
core/api/user-api.service.ts
core/api/movie-api.service.ts
core/api/rating-api.service.ts
core/api/recommendation-api.service.ts
```

DTOs reflect `docs/reference/04_API_SPEC.md`.

## 27. State Ownership

- auth/access token: AuthStore
- browse filters: route query params + feature state
- watchlist: server authoritative, lightweight cache allowed
- recommendations: server authoritative
- forms: local Reactive Form state

Do not copy all server entities into one permanent global store.

## 28. Loading/Empty/Error States

Every async feature handles:

```text
loading
success
empty
error
```

Examples:

- no search result
- empty watchlist
- cold-start recommendation guidance
- Recommendation Service unavailable while movie browsing remains usable

## 29. Error Mapping

- 400 → validation
- 401 → refresh/login
- 403 → access denied
- 404 → not found/unavailable
- 409 → business conflict
- 429 → too many attempts
- 500/503 → controlled temporary message

Do not dump raw Problem Details in normal UI.

## 30. Accessibility

Mandatory:

- semantic headings/buttons
- labels/errors associated with inputs
- keyboard navigation
- visible focus
- icon buttons have accessible names
- poster alt/decorative handling
- rating stars keyboard/screen-reader usable
- adequate contrast
- dialog focus management

## 31. Responsive Baseline

Desktop-first, normal tablet/phone widths supported.

- cards reflow
- admin forms usable
- navigation collapses
- filters stack/wrap
- no critical horizontal overflow

No native app.

## 32. Browser Tests

Playwright proves:

- register/login
- 2FA
- search/detail
- rating CRUD
- watchlist
- recommendation behavior/filters
- anonymous share
- USER blocked from admin
- ADMIN movie CRUD
- XSS rendered as text

## 33. Frontend Definition of Done

All required routes are navigable, responsive enough for mobile, keyboard-usable, complete in loading/empty/error behavior, use typed API clients, and do not store long-lived auth secrets in browser-accessible storage.
