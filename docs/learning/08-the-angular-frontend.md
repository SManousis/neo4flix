# 8. The Angular frontend

The frontend is the application that runs in the user's browser. It is written
in TypeScript with Angular 22 and uses Angular Material and SCSS for interface
elements and styling.

**TypeScript** is JavaScript with a type system. **Angular** provides a
structured way to build pages from components, routes, services, forms, and
reactive state.

## How the frontend starts

The browser loads the generated `index.html` and JavaScript bundles. The entry
point is [`main.ts`](../../frontend/src/main.ts), which bootstraps the root
`AppComponent` using the providers in
[`app.config.ts`](../../frontend/src/app/app.config.ts).

The configuration provides:

- the Angular router;
- the HTTP client;
- the authentication interceptor;
- browser animations; and
- an application initializer that tries to restore the login session.

## Components

A **component** owns a portion of the interface. It combines:

- a TypeScript class for state and behavior;
- an HTML template for structure;
- styles for appearance; and
- metadata in `@Component`.

Some Neo4flix components use separate `.html` and `.scss` files, while smaller
components keep templates and styles inline. Both forms are normal Angular.

For example,
[`CatalogComponent`](../../frontend/src/app/features/catalog/catalog.component.ts)
loads movie data, stores loading/error/result state, reads URL filters, and
renders the catalog.

## Standalone components

Neo4flix uses Angular's standalone component style. A component declares the
Angular features and child components it imports instead of belonging to an
`NgModule`.

```typescript
@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `...`
})
export class CatalogComponent {}
```

## Routes

A route maps a browser URL to a component. The complete route list is in
[`app.routes.ts`](../../frontend/src/app/app.routes.ts).

```typescript
{
  path: 'watchlist',
  canActivate: [authGuard],
  loadComponent: () => import('./features/watchlist/watchlist.component')
    .then(module => module.WatchlistComponent)
}
```

`loadComponent` performs **lazy loading**: Angular downloads the component code
when the route is needed rather than placing every page in the initial bundle.

`canActivate` runs a route guard before navigation. `authGuard` sends anonymous
visitors toward login; `adminGuard` protects admin pages in the UI. Backend
authorization remains the real security boundary.

## Services and HTTP

Angular services contain behavior shared by components. Neo4flix uses API
services such as:

- `CatalogApiService`;
- `RatingApiService`;
- `WatchlistApiService`;
- `RecommendationApiService`; and
- `AuthApiService`.

A component uses Angular's `inject` function to obtain a service:

```typescript
private readonly api = inject(CatalogApiService);
```

The API service uses `HttpClient` to call a backend route and returns an RxJS
`Observable`. The component subscribes to receive the result or handle an
error.

## Observables

An **Observable** represents values that may arrive over time. An HTTP request
usually produces one response and then completes. Route parameters can produce
many values as the URL changes.

Neo4flix uses `takeUntilDestroyed` so subscriptions stop when their component
is destroyed. This avoids work continuing after the user has left the page.

## Signals

A **signal** stores reactive state. When a template reads a signal and its value
changes, Angular updates the relevant view.

```typescript
protected readonly loading = signal(true);
protected readonly error = signal<string | null>(null);
protected readonly results = signal<PageResult<MovieSummary>>(EMPTY_PAGE);
```

The component can call `loading.set(false)` or `results.set(page)`. Signals
make the connection between data and rendered HTML explicit.

## Template control flow

Angular templates use `@if` and `@for`:

```html
@if (loading()) {
  <p role="status">Loading movies...</p>
} @else if (error()) {
  <p role="alert">{{ error() }}</p>
} @else {
  @for (movie of results().items; track movie.id) {
    <article>{{ movie.title }}</article>
  }
}
```

This shows loading, error, and success states from the component signals.
`track movie.id` helps Angular update lists efficiently.

## Forms

Neo4flix uses **reactive forms**. A `FormControl` holds one input value; a
`FormGroup` combines controls. The TypeScript class reads validated form state
and decides when to submit it.

Catalog filters are also written into query parameters. A URL such as
`/movies?title=Arrival&page=0` can be refreshed or shared without losing the
current search.

## The authentication store and interceptor

[`AuthStore`](../../frontend/src/app/core/auth.store.ts) keeps the current user
and short-lived access token in memory. It bootstraps by attempting a refresh
through the secure cookie.

[`authInterceptor`](../../frontend/src/app/core/auth.interceptor.ts) adds the
access token to protected API requests. If a request receives `401`, it can ask
the store to refresh and retry according to the implemented rules.

The access token is not stored in browser local storage. The refresh token is
an HTTP-only cookie, so frontend JavaScript cannot read it.

## Styling and accessibility

Global styles live in [`styles.scss`](../../frontend/src/styles.scss), and
components may add focused styles. The UI intentionally favors clear,
functional styling over a large custom design system.

Templates use semantic headings, labels, buttons, `role="status"`,
`role="alert"`, and descriptive links so state changes are understandable to
assistive technology as well as sighted users.

## Recap

Angular builds the interface from standalone components. Routes choose pages,
services call APIs, Observables deliver asynchronous results, signals hold
reactive state, forms collect input, and the auth interceptor attaches access
tokens.

Next: [Chapter 9 explains authentication, authorization, and 2FA](09-login-security-and-2fa.md).
