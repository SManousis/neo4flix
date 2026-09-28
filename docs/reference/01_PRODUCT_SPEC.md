# Neo4flix — Product Specification

> **Authority:** This document owns product scope, roles, user-visible behavior, business rules, MVP exclusions, and success criteria. Technical implementation details belong to the architecture/database/API/frontend/security documents.

## 1. Product Goal

Neo4flix is a multi-user movie discovery and recommendation web application built for the 01-edu Neo4flix assignment.

Its core value is to demonstrate that a Neo4j graph can represent movie/user preference relationships and generate explainable personalized recommendations from those relationships.

The product centers on:

```text
MOVIE DISCOVERY
      +
USER RATINGS
      +
NEO4J GRAPH
      +
PERSONALIZED RECOMMENDATIONS
```

## 2. External Assignment Requirements

The MVP must preserve the official assignment requirements:

- Neo4j data modeling
- Spring Boot microservices
- Angular frontend
- Docker deployment
- Movie microservice
- User microservice
- Rating microservice
- Recommendation microservice
- CRUD capability for the microservices' owned resources
- JWT or OAuth2 authentication; this design uses JWT
- login and registration
- home/movie listing
- search
- movie details
- rating page
- recommendations page
- search by title, genre, release date/year, and other useful criteria
- filter recommendations
- watchlist
- recommendation sharing
- HTTPS
- strong password policy
- 2FA
- functionality, usability, and security testing

The audit additionally drives explicit graph/recommendation/Cypher demonstration, malicious-input testing, and stress testing.

## 3. Product Identity and Non-Goals

Neo4flix **is**:

- a movie catalog browser
- a rating application
- a personal watchlist
- a graph recommendation engine
- an explainable recommendation demo
- an educational microservice application

Neo4flix is **not**:

- a video streaming service
- a Netflix UI clone
- a commercial subscription product
- a movie review/comment social network
- a friend/follower social network
- an AI/LLM recommendation product
- a machine-learning training platform
- an infrastructure showcase

## 4. Roles

### 4.1 USER

A normal authenticated user can:

- register and authenticate
- complete TOTP 2FA when enabled
- view/update basic profile data
- change password
- enable/confirm/disable 2FA
- browse movies
- search/filter/sort movies
- view movie details
- create/read/update/delete **their own** rating
- add/remove/list movies in **their own** watchlist
- view personalized recommendations
- filter/sort recommendations
- see a short recommendation reason
- create/list/update/revoke/delete **their own** recommendation share links
- open public share links
- delete their account with strong reauthentication

A USER cannot:

- create/update/delete movies
- create/update/delete genres
- mutate another user's data
- see another user's private recommendation inputs or rating history
- bypass 2FA once enabled

### 4.2 ADMIN

An ADMIN has normal user capabilities plus catalog management:

- create movies
- update movies
- delete movies
- create/update/delete unused genres

MVP does not require a broad user-management admin console.

## 5. Registration and Authentication Journey

### Registration

```text
Registration form
      ↓
validate email/display name/password
      ↓
create USER account
      ↓
login page
```

Registration does not automatically create an authenticated session.

### Login without 2FA

```text
email + password
      ↓
valid
      ↓
access JWT + refresh session
      ↓
Home
```

### Login with 2FA

```text
email + password
      ↓
valid + 2FA enabled
      ↓
short-lived one-time challenge
      ↓
TOTP page
      ↓
valid code
      ↓
access JWT + refresh session
```

Password validation alone must never produce a normal authenticated session for a 2FA-enabled account.

## 6. Password Policy

MVP policy:

- minimum 10 characters
- bounded maximum length
- at least one uppercase character
- at least one lowercase character
- at least one digit
- at least one special character

Passwords are always hashed using an adaptive password encoder. Passwords are never logged, returned, or stored plaintext.

## 7. 2FA Product Behavior

Neo4flix uses TOTP authenticator-app 2FA, not SMS.

### Enable

```text
Profile → Security
      ↓
Begin setup
      ↓
QR / manual secret shown
      ↓
user enters current TOTP
      ↓
server confirms
      ↓
2FA becomes active
```

An unconfirmed setup is not active 2FA.

### Disable

Requires:

- current password
- current valid TOTP code

## 8. Home Page

Authenticated route: `/home`.

Primary sections:

- search entry
- Recommended for You
- Popular Movies
- Recently Released
- Browse Movies

For a zero-rating user, personalized space uses popularity/high-quality fallback and clearly suggests rating movies to improve recommendations.

## 9. Movie Browsing

A movie card should present enough information to decide whether to open it:

- poster when available
- title
- release year
- genres
- average rating/rating count when available
- watchlist state/action for authenticated users

## 10. Search and Filtering

Movie discovery supports:

- title query
- genre
- release year range
- optional exact release-date range where dates exist
- minimum average rating
- pagination
- sorting

Sort choices include at least:

- relevance/title match
- title ascending
- newest
- oldest
- highest rated

Empty query is valid browse/filter mode.

## 11. Movie Details

Route: `/movies/:movieId`.

Display:

- poster
- title
- overview
- release year
- exact release date when known
- runtime when known
- genres
- average rating
- rating count
- current viewer's rating
- current viewer's watchlist state
- related movies
- recommendation context/reason when appropriate

Actions:

- rate/update/delete rating
- add/remove watchlist
- share movie/recommendation context

## 12. Movie Date Rule

`releaseYear` is required catalog data.

`releaseDate` is optional because imported datasets may know the year but not a trustworthy exact day/month.

Rules:

- never synthesize a fake January 1 release date
- if `releaseDate` exists, its year must equal `releaseYear`
- search/filtering must work from `releaseYear` even when `releaseDate` is absent

## 13. Rating Model

Ratings are integer stars:

```text
1★ 2★ 3★ 4★ 5★
```

Business invariant:

```text
one User + one Movie = zero or one rating
```

Lifecycle:

- create rating
- read rating
- update rating
- delete rating

`POST` create is **not** an upsert. If a rating already exists, the API returns a conflict and the client uses update.

Rating intent:

- 4–5: positive signal
- 3: mostly neutral
- 1–2: negative signal

The recommendation engine must not interpret repeated low ratings as positive affinity.

## 14. Rating Page

The assignment explicitly calls for a rating page. Neo4flix supports:

- `/movies/:movieId/rate`

The detail page may also provide an inline rating control, but the dedicated route must remain available and usable.

## 15. Watchlist

MVP has one implicit personal watchlist per user.

Actions:

- add a movie
- list watchlist
- remove a movie
- open movie detail

Adding the same movie repeatedly is idempotent and must not create duplicate graph relationships.

Watchlisting means “I want to remember/watch this.” It is **not** treated as the same preference strength as an explicit rating.

No multiple named lists in MVP.

## 16. Personalized Recommendations

Route: `/recommendations`.

Recommendations are based on:

1. collaborative rating similarity as the main mature-user signal
2. content/genre similarity as a secondary/fallback signal
3. popularity/confidence as a stabilizer and cold-start fallback

Already-rated movies are excluded.

Each result exposes a concise human-readable reason, for example:

- “Users with similar ratings also liked this movie.”
- “Matches Science Fiction movies you rated highly.”
- “Highly rated by Neo4flix users.”

The UI may show a normalized recommendation score, but it must not expose private identities of similar users.

## 17. Recommendation Cold Start

### Zero ratings

Strategy: `POPULARITY`.

### Very few ratings

Strategy: `CONTENT_PLUS_POPULARITY`.

### Sufficient history

Strategy: `HYBRID`.

The API reports the active strategy so behavior is testable and demonstrable.

## 18. Recommendation Filters

At minimum:

- genre
- release year/date range
- minimum average rating
- sort by recommendation strength/rating/newest where appropriate

Filters refine the candidate results; they do not create separate recommendation engines.

## 19. Related Movies

Movie Service provides non-personalized content-oriented similarity such as shared genre relationships.

This is distinct from personalized recommendations.

## 20. Sharing

“Share recommendations with friends” is implemented through shareable links, not a social graph.

Flow:

```text
Recommendation/movie
      ↓
Share
      ↓
RecommendationShare resource
      ↓
public token URL
      ↓
friend opens public movie/share page
```

Public share responses must not expose:

- creator email
- creator watchlist
- creator ratings
- similar-user identities
- auth/session details

No friend requests, follows, messaging, or social feed in MVP.

## 21. Profile

Route: `/profile`.

Sections:

- Profile
- Security
- Ratings
- Watchlist shortcut

Editable MVP profile field:

- display name

Email remains immutable in v1 to avoid introducing an email-verification/change workflow that is not required by the assignment.

## 22. Admin Catalog

Routes:

- `/admin`
- `/admin/movies`
- `/admin/movies/new`
- `/admin/movies/:id/edit`
- `/admin/genres`

Movie fields:

- title (required)
- overview
- releaseYear (required)
- releaseDate (optional)
- genres (required; at least one)
- posterUrl
- runtimeMinutes
- optional external source/id metadata for imports

Delete is hard delete for MVP, with explicit cleanup of connected share nodes and automatic removal of relationships.

Genre deletion fails with conflict while any Movie uses the Genre.

## 23. Service Responsibility Model

### Movie Service

Owns:

- Movie CRUD
- Genre CRUD
- movie list/search/filter/sort
- movie detail
- related movies
- personalized recommendation **facade** required by assignment wording

It does not own the personalized ranking algorithm.

### User Service

Owns:

- registration/authentication identity
- profile CRUD/self-delete
- password
- 2FA
- refresh sessions/challenges
- watchlist mutation/read
- user-centric rating-history read facade

It does not mutate `RATED`.

### Rating Service

Only writer of rating relationships.

Owns:

- rating create/read/update/delete
- current user's rating history
- movie rating aggregates

### Recommendation Service

Owns:

- personalized recommendation generation
- collaborative/content/popularity ranking
- cold start
- recommendation filtering/explanations
- RecommendationShare CRUD
- public share retrieval

## 24. Public vs Authenticated Access

Public:

- login
- registration
- movie browse/search/details
- public share links

Authenticated:

- ratings
- watchlist
- personalized recommendations
- profile/security
- share management

ADMIN additionally required for catalog mutation.

## 25. Navigation

Normal authenticated top navigation:

```text
Neo4flix | Home | Movies | Recommendations | Watchlist | Search | Profile | Logout
```

ADMIN additionally sees `Admin`.

## 26. Critical User Flows

### A. First-time user

```text
Register → Login → Home → Browse → Rate movies → Personalized recommendations
```

### B. Search

```text
Search/filter → Results → Movie details → Rate/Watchlist
```

### C. Rating → recommendation change

```text
Movie → Rate → graph RATED change → recommendation request → ranking changes
```

### D. Watchlist

```text
Movie → Add → Watchlist page → Remove
```

### E. Sharing

```text
Recommendation → Share → copy public URL → anonymous browser opens movie
```

### F. Admin CRUD

```text
Admin → Movies → Create → Read/Search → Edit → Delete
```

### G. 2FA

```text
Credentials → TOTP required → code → authenticated session
```

## 27. MVP Exclusions

Explicitly out of scope unless the product spec is revised:

- video streaming
- subscriptions/payments
- reviews/comments
- review likes
- formal friends/followers
- social feed
- messaging
- push notifications
- email recommendation campaigns
- multiple watchlists
- actor/director/studio/tag graph expansion unless specifically approved after MVP
- ML model training
- LLM/AI recommendations
- GraphQL
- Kafka/RabbitMQ
- Redis
- Kubernetes
- service mesh
- native mobile apps
- Google/Facebook social login
- complex admin analytics
- content moderation

## 28. Product Success Criteria

Neo4flix MVP is product-complete only when a real user can:

- register
- login securely
- use TOTP 2FA
- browse and search movies
- open movie details
- create/update/delete a rating
- manage a watchlist
- receive recommendations that respond to ratings
- filter recommendations
- understand a recommendation reason
- share a recommendation/movie safely

and an ADMIN can perform movie/genre catalog CRUD with backend authorization.

The graph and recommendation behavior must also be visibly explainable for the audit.
