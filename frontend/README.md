# Neo4flix frontend

The frontend is an Angular 22 standalone application styled with Angular
Material and SCSS. It provides the catalog, movie detail, ratings, watchlist,
recommendation, sharing, authentication, and admin views served by the main
Docker Compose stack.

API calls use the `/api/v1` contract and are proxied through the Nginx container
when the full stack is running.

## Local development

Run these commands from `frontend/`:

```sh
npm ci
npm start
```

The development server listens at `http://localhost:4200/`.

## Verification

```sh
npm run lint
npm test -- --run
npm run build
```

The test script maps Vitest's `--run` flag to Angular's `--watch=false` option;
Angular compiles the application and initializes TestBed before running Vitest.
Use `npm test -- --watch` for watch mode. Production assets are written to
`dist/frontend/browser/`.

On Windows PowerShell with script execution disabled, use `npm.cmd` in place of
`npm`. No execution-policy change is needed.
