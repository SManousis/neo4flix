# 01-edu Neo4flix Audit Question Checklist

This checklist turns the official 01-edu Neo4flix audit questions into a local
walkthrough. The source questions are maintained in the upstream audit README:

<https://github.com/01-edu/public/tree/master/subjects/java/projects/neo4flix/audit>

Run [AUDIT_RUNBOOK.md](AUDIT_RUNBOOK.md) first. Keep the Compose stack running,
load the deterministic audit fixture, and record observations without copying
passwords, tokens, TOTP secrets, or private keys into the evidence.

## Before the evaluator arrives

1. Start Docker Compose and run `scripts/smoke-compose.ps1`.
2. Load the audit fixture with `scripts/seed.ps1 audit` after exporting only the
   local Neo4j connection variables in the current PowerShell process.
3. Open <http://localhost:8080/> and Neo4j Browser at <http://localhost:7474/>.
4. Keep the browser suite and focused proof commands available; they are
   supporting evidence, not a replacement for demonstrating the UI live.

## Functional and data/design questions

| Official question | How to execute it locally | Evidence to record |
| --- | --- | --- |
| Does the application work and expose its core functionality? | Walk through register/login, catalog, details, rating, recommendations, watchlist, and sharing in the browser. | Completion, errors, and request IDs; automated support in `batch-11-browser-failure-verification.md`. |
| Do nodes and relationships represent movies, users, and ratings? | Use the read-only queries in `GRAPH_DEMO.md` and inspect the graph in Neo4j Browser. | Counts, labels, relationship directions, and representative properties. |
| Are relationship attributes appropriate? | Run the `RATED`/`IN_GENRE` queries in `GRAPH_DEMO.md`; inspect `WATCHLISTED` through the watchlist repository contract and `CREATED_SHARE`/`SHARES` through `RecommendationShareRepository`. | Scores, timestamps, deterministic keys, ownership fields, and share expiry; never expose credentials. |

## Microservice questions

| Area | Live action | Supporting proof |
| --- | --- | --- |
| Movie service read/write and recommendation inputs | Browse/search movies, then use an ADMIN fixture only for a disposable create/edit/delete demonstration. | `batch-3-verification.md`, catalog tests, and the standalone ADMIN browser proof. |
| User service operations and stored ratings | Register a disposable user, open Profile, and inspect only the safe audit projection. | `batch-2-verification.md`, `batch-11-browser-failure-verification.md`, and `USABILITY_TEST.md`. |
| Rating service operations | Create, update, view, and remove a rating, then refresh rating history. | Rating browser contract and `batch-4-verification.md`. |
| Spring Data Neo4j/OGM mapping | Show the repository/entity classes and the service health endpoint; do not edit data during the explanation. | Backend mapping tests and module source. |
| Recommendation algorithm and Cypher/GDS | Use the audit personas, open recommendations, and explain the strategy/signal/reason from `RECOMMENDATION_EXPLANATION.md`. | `RecommendationGoldenFixtureIT`, `GRAPH_DEMO.md`, and the GDS cosine proof. |

## User-facing feature questions

Execute these as a single browser journey and note whether the participant
needs help:

- Search by title, genre, year, and rating; verify empty and retry states.
- Open movie details and verify title, release year, genres, and rating summary.
- Rate a movie and confirm the visible state and profile history.
- Open recommendations, apply filters, and explain one result.
- Add a movie to the watchlist, open it, and remove it.
- Create a recommendation share and open the anonymous public share URL.
- Visit login, registration, home, details, rating, and recommendations pages.

The automated contract covers the repeatable paths. Record human observations
in [USABILITY_TEST.md](USABILITY_TEST.md) when they are available. The user
confirmed the manual human audit was completed; this repository does not invent
participant details that were not supplied.

## Security questions

Run `make security` (or `pwsh -NoProfile -File scripts/security.ps1`) and use
the following live checks:

- Register/login with a disposable account; verify JWT-backed access and logout.
- Enroll 2FA and complete the password-only challenge plus TOTP verification.
- Register with weak and strong candidate passwords; verify the documented
  complexity policy rejects weak input and accepts only compliant passwords.
  Supporting proof: `PasswordPolicyTest`, auth DTO validation tests, and the
  password-policy row in `SECURITY_CHECKLIST.md`.
- Try a USER request against an ADMIN-only route and record the denial.
- Submit malformed/bounded input and verify generic Problem Details plus a
  request ID; do not paste tokens into the report.
- Inspect cookie attributes, CORS/origin behavior, response headers, and rate
  limiting using `SECURITY_CHECKLIST.md`.
- Treat HTTPS/certificate/redirect questions as deployment evidence. This
  project is intentionally local-only and will not be deployed, so mark those
  production-only rows `N/A`; local HTTP security headers, cookies, JWT, 2FA,
  and rate-limit checks remain in scope.

## Testing, errors, and stress questions

Use the root `make verify-all` gate (or its documented equivalent), then run
the Playwright contract and the pinned k6 profiles in `STRESS_TEST.md`. Record
the actual output, including skips and explicit 429 rate-limit responses. The
current bounded evidence is green, but it is not a production capacity claim.

## Completing the checklist

For each question, write one of `PASS`, `PARTIAL`, `BLOCKED`, or `N/A`, attach the
corresponding local evidence file/command, and add one short observation. Do not
mark HTTPS or release-capacity questions as passed when they are out of scope;
mark them `N/A` with the local-only rationale. The manual human audit is
user-confirmed, while detailed participant notes remain optional.
