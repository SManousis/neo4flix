# 2. How a user uses Neo4flix

Before studying the code, it helps to understand the application from the
user's point of view. This chapter follows the normal browser journey and
connects each visible action to the part of the system that supports it.

## Opening the application

After the local stack has started, the main application is available at:

```text
http://localhost:8080/
```

The browser downloads the Angular application from Nginx. Angular reads the
current route and displays the correct page.

## 1. Registering

A new person opens `/auth/register` and enters:

- an email address;
- a display name; and
- a password that satisfies the password policy.

The frontend sends the form to `POST /api/v1/auth/register`. User Service
validates the input, normalizes the email address, hashes the password, creates
a `User` node in Neo4j, and returns only safe public account fields.

The implementation begins in
[`AuthController.register`](../../backend/user-service/src/main/java/com/neo4flix/user/auth/AuthController.java)
and continues in
[`AuthApplicationService.register`](../../backend/user-service/src/main/java/com/neo4flix/user/auth/AuthApplicationService.java).

## 2. Signing in

The login page sends an email and password to `POST /api/v1/auth/login`.

For an account without two-factor authentication, the response contains a
short-lived access token and sets a longer-lived refresh token as an HTTP-only
cookie. The Angular application keeps the access token in memory and attaches
it to protected API requests.

For an account with 2FA enabled, the first response is a temporary challenge.
The browser moves to `/auth/2fa`, the user enters a six-digit TOTP code, and the
application completes the login only after verifying that code.

## 3. Browsing and searching

The movie catalog is available at `/movies`. A user can:

- browse a page of movies;
- search by title;
- open an individual movie at `/movies/{id}`.

The visible catalog and search forms currently expose title search. The catalog
component can also read `genre`, `minYear`, `maxYear`, `sort`, `direction`,
`page`, and `size` from URL query parameters and forward them to the API. Movie
Service implements title, genre, year-range, sorting, and pagination query
logic, but those extra controls are not all rendered as form fields in the
current UI.

The Angular catalog calls Movie Service through `/api/v1/movies`. Movie Service
runs parameterized Cypher queries against Neo4j and returns response objects
rather than raw database nodes. On the detail page, Angular separately calls
Rating Service for the movie's rating summary.

The page logic is in
[`CatalogComponent`](../../frontend/src/app/features/catalog/catalog.component.ts),
and its backend entry point is
[`MovieCatalogController`](../../backend/movie-service/src/main/java/com/neo4flix/movie/catalog/MovieCatalogController.java).

## 4. Rating a movie

An authenticated user can open `/movies/{id}/rate` and assign an integer score
from 1 to 5.

Neo4flix stores that score as a `RATED` relationship from the user to the
movie. The relationship can be created, updated, or deleted. There is only one
rating per user/movie pair.

Rating Service owns these writes. The main API entry point is
[`RatingController`](../../backend/rating-service/src/main/java/com/neo4flix/rating/api/RatingController.java).

## 5. Managing a watchlist

A signed-in user can save a movie to the watchlist, browse `/watchlist`, open a
saved movie, and remove it later.

The graph represents this with a `WATCHLISTED` relationship. Adding the same
movie twice does not create duplicate relationships.

The relevant frontend page is
[`WatchlistComponent`](../../frontend/src/app/features/watchlist/watchlist.component.ts),
while User Service owns the operation through
[`WatchlistController`](../../backend/user-service/src/main/java/com/neo4flix/user/watchlist/WatchlistController.java).

## 6. Receiving recommendations

The `/recommendations` page asks Recommendation Service for a ranked list. The
service considers the user's ratings, movie genres, the ratings of similar
users, and overall popularity. It excludes movies the current user has already
rated.

Every result includes a human-readable reason. The user can filter the results,
save a movie to the watchlist, or create a share link.

Chapter 10 explains this process in detail.

## 7. Sharing a recommendation

When the user selects **Share**, Recommendation Service creates a random public
token. The browser produces a path such as:

```text
/share/a-random-public-token
```

A visitor can open that page without signing in. The API returns only safe
movie information. The database stores a hash of the token rather than the raw
token, and links can expire or be revoked.

## 8. Managing the profile

The `/profile` page lets a user view account information, update the display
name, inspect rating history, change the password, configure 2FA, or delete the
account after reauthentication.

Deleting an account also removes related authentication sessions, challenges,
shares, ratings, and watchlist relationships according to the service's cleanup
rules.

## 9. Admin-only catalog work

An account with the `ADMIN` role can use `/admin` to manage movies and genres.
Angular hides this route from ordinary users, but the backend permission check
is the authoritative protection. A frontend guard alone is never considered
security.

## Which service handles each action?

| User action | Main backend owner |
| --- | --- |
| Register, login, profile, 2FA | User Service |
| Add or remove a watchlist item | User Service |
| Browse, search, or administer movies | Movie Service |
| Create, update, or delete a rating | Rating Service |
| Produce recommendations | Recommendation Service |
| Create or open recommendation shares | Recommendation Service |

## When something goes wrong

The frontend provides loading, empty, success, and failure states rather than
assuming every request succeeds. Backend errors use a consistent Problem
Details shape and include a request ID. The request ID helps connect a browser
error to server logs without exposing secrets.

## Recap

The application is easiest to understand as a sequence of user actions. Each
action becomes an HTTP request, one service owns the operation, and Neo4j stores
the resulting data or relationship.

Next: [Chapter 3 follows one request through the complete architecture](03-how-it-works-at-a-glance.md).
