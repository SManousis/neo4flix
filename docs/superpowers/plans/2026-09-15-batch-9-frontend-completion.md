# Frontend Completion, Accessibility, Responsive, and Contract Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining Angular route/page gaps and make every required workflow consistently typed, responsive, keyboard-usable, and explicit about loading, empty, and error states.

**Architecture:** Keep standalone Angular components and typed API services. Route state remains in query parameters, feature state remains local signals/forms, and the server remains authoritative for auth, ratings, watchlists, recommendations, and catalog data. Prefer existing Angular Material primitives and shared layout styles over new UI frameworks.

**Tech Stack:** Angular 22.1.5, Angular Material 22.1.5, TypeScript 6, standalone components, Reactive Forms, signals/RxJS, Vitest, Playwright.

**Spec:** `docs/reference/01_PRODUCT_SPEC.md`, `docs/reference/04_API_SPEC.md`, `docs/reference/05_FRONTEND_SPEC.md`, `docs/reference/06_TESTING_SECURITY.md`

## Global Constraints

- Required routes are `/`, `/login`, `/register`, `/auth/2fa`, `/share/:publicToken`, `/home`, `/movies`, `/movies/:id`, `/movies/:id/rate`, `/search`, `/recommendations`, `/watchlist`, `/profile`, `/admin`, `/admin/movies`, `/admin/movies/new`, `/admin/movies/:id/edit`, and `/admin/genres`.
- Browse filters use route query parameters; recommendation filters remain server-backed and URL-backed.
- Every async feature renders loading, success, empty, and controlled error states; raw Problem Details are never dumped into normal UI.
- Controls have semantic labels, keyboard access, visible focus, useful names, and no critical horizontal overflow at phone/tablet widths.
- No access token is written to browser storage; no public share token or private recommendation signal is logged or rendered.
- Use only existing typed API clients and Angular Material; do not add NgRx, Tailwind, Bootstrap, or duplicate business APIs.

---

### Task 1: Close route and page gaps

**Files:**
- Create: `frontend/src/app/features/home/home.component.ts`, `frontend/src/app/features/home/home.component.spec.ts`
- Create: `frontend/src/app/features/search/search.component.ts`, `frontend/src/app/features/search/search.component.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`, `frontend/src/app/app.routes.spec.ts`
- Modify: `frontend/src/app/features/admin/catalog-admin.component.ts` only if route-specific headings/actions are needed

**Interfaces:**
- `HomeComponent` is authenticated, renders links to movies/recommendations/watchlist, and shows a non-empty explanation when recommendation loading fails.
- `SearchComponent` owns a non-empty title control, synchronizes `title` with `ActivatedRoute.queryParamMap`, calls `CatalogApiService.movies({ title })`, and renders loading/empty/error/success states.
- `/admin`, `/admin/movies`, `/admin/movies/new`, `/admin/movies/:id/edit`, and `/admin/genres` resolve through the existing admin component with `adminGuard`; `/` resolves to the authenticated home page.

- [x] **Step 1: Write failing route and page tests** for every missing route, admin guard coverage, search query-param serialization, and home navigation links.
- [x] **Step 2: Run the focused frontend tests and confirm they fail**

Run: `npm test -- --watch=false --include='src/app/app.routes.spec.ts' --include='src/app/features/home/home.component.spec.ts' --include='src/app/features/search/search.component.spec.ts'`

- [x] **Step 3: Implement the home and search standalone components** with typed `CatalogApiService` calls, explicit loading/empty/error states, and Material/semantic controls.
- [x] **Step 4: Register all canonical and admin alias routes** with the correct guards and route parameters.
- [x] **Step 5: Run focused tests and commit**

Run: `npm test -- --watch=false --include='src/app/app.routes.spec.ts' --include='src/app/features/home/home.component.spec.ts' --include='src/app/features/search/search.component.spec.ts'`

Commit: `feat: close frontend route and page gaps`

### Task 2: Reconcile catalog, detail, watchlist, and profile states

**Files:**
- Modify: `frontend/src/app/features/catalog/catalog.component.ts`, `frontend/src/app/features/catalog/movie-detail.component.ts`, `frontend/src/app/features/watchlist/watchlist.component.ts`, `frontend/src/app/features/profile/profile.component.ts`
- Modify: `frontend/src/app/core/catalog-api.service.ts`, `frontend/src/app/core/catalog.models.ts`
- Test: adjacent `catalog`, `movie-detail`, `watchlist`, and `profile` specs

- [x] **Step 1: Add failing tests** for URL-backed browse filters, pagination/empty/error states, movie poster/genre/rating fields, retry controls, and profile/watchlist errors.
- [x] **Step 2: Implement route-query filter serialization, retryable error signals, empty-state actions, poster/genre/rating rendering, and DTO fields from `docs/reference/04_API_SPEC.md` without duplicating API calls or storing entities globally.
- [x] **Step 3: Run the focused catalog/detail/watchlist/profile suites and commit**

### Task 3: Accessibility and responsive baseline

**Files:** `frontend/src/app/app.component.ts`, `frontend/src/app/app.component.html`, `frontend/src/app/app.component.scss`, feature component templates/styles, `frontend/src/styles.scss`, and accessibility specs.

- [x] **Step 1: Add failing tests** for navigation names, focusable controls, form labels/errors, poster alt behavior, rating keyboard semantics, and mobile navigation.
- [x] **Step 2: Implement shared responsive layout, visible focus, stacked filters/forms, card reflow, and accessible names** using Angular Material primitives.
- [x] **Step 3: Run lint, focused accessibility tests, and commit**

### Task 4: Recommendation/admin UX and API contract reconciliation

**Files:** `frontend/src/app/core/recommendation.models.ts`, `frontend/src/app/features/recommendations/recommendations.component.ts`, `frontend/src/app/features/admin/catalog-admin.component.ts`, `frontend/src/app/app.routes.ts`, and adjacent specs.

- [x] **Step 1: Add failing tests** for all backend strategy values, score/movie summary rendering, admin route aliases, validation messages, and controlled 400/401/403/404/409/429/503 mapping.
- [x] **Step 2: Implement typed DTO and UX corrections** while preserving server-side authorization and recommendation privacy.
- [x] **Step 3: Run focused suites and commit**

### Task 5: Browser contract and Batch 9 audit

**Files:** `frontend/e2e/*.spec.ts`, `docs/audit/batch-9-verification.md`, `docs/superpowers/ACTIVE_BATCH_CONTEXT.md`, `docs/reference/00_MASTER_EXECUTION_PLAN.md`.

- [x] **Step 1: Add serial Playwright coverage** for route reachability, anonymous search/detail, authenticated rating/watchlist/recommendation flows, anonymous sharing, USER/admin denial, and XSS-as-text.
- [x] **Step 2: Run full Maven, frontend test/lint/build, Compose interpolation, and serial Playwright**; record credential-gated skips explicitly.
- [x] **Step 3: Run `git diff --check`, mark Batch 9 complete only when the route/accessibility/contract gate is met, update active context to Batch 10, commit audit evidence, and push `main`.**
