# Neo4flix Recommendation Explanation

This guide maps the deterministic audit fixture to the recommendation service’s strategy, signals, reasons, and exclusions. The implementation sources are `RecommendationScoringService`, `RecommendationApplicationService`, `RecommendationNeo4jRepository`, and `RecommendationGoldenFixtureIT`.

## Fixture personas and strategy selection

`RecommendationScoringService.selectStrategy(ratingCount, qualifyingPeer)` applies the following rules:

| Persona | History | Strategy | Why |
|---|---:|---|---|
| `audit-fresh` | 0 ratings | `POPULARITY` | No personal history is available. |
| `audit-sparse` | 1 rating | `CONTENT_PLUS_POPULARITY` | Fewer than three ratings cannot support mature collaborative ranking. |
| `audit-alice` | 4 ratings and qualifying peers | `HYBRID` | Enough history and overlap support collaborative, content, and popularity signals. |

The golden fixture asserts these strategy outcomes. `audit-negative` has a negative-only signal for the Action genre; its result is not allowed to turn that negative preference into a positive genre recommendation.

## Signals and score bounds

The repository builds a signal row for every candidate that the current user has not rated:

- **Collaborative:** selected peers are matched through common `RATED` movies, aligned vectors are compared with `gds.similarity.cosine`, and highly rated peer-only movies receive a normalized peer score.
- **Content:** each rated movie’s genre contributes a preference of `-1.0`, `-0.5`, `0.0`, `0.5`, or `1.0` for scores 1 through 5. Candidate genre overlap is then normalized and clamped.
- **Popularity:** average rating is normalized by 5 and multiplied by confidence `ratingCount / (ratingCount + priorCount)`. The default prior count is `5.0`.

Default weights are collaborative `0.60`, content `0.30`, and popularity `0.10`. `POPULARITY` uses the popularity signal directly; `CONTENT_PLUS_POPULARITY` uses the weighted content/popularity average; `HYBRID` uses all three weighted signals. Every signal and final score is clamped to `[0,1]`.

The application sorts final results by descending score and then ascending movie ID, so equal scores have a deterministic tie-break. It limits the result after sorting.

## Exclusion and filters

`CANDIDATE_QUERY` enforces `WHERE NOT (me)-[:RATED]->(movie)`, so every personalized strategy excludes all movies already rated by the current user. Watchlisted movies are not excluded because a watchlist is a separate intent from a rating.

The same candidate query applies the requested genre, release-year range, minimum average rating, and bounded candidate limit before the scoring service sorts the final results. There is one recommendation engine; filters do not select a different algorithm.

For Alice, the golden test requires Blade Runner to appear and excludes The Matrix, Inception, Spirited Away, and Arrival—the four movies Alice already rated. Re-running the same query must return the same ordered list.

## Public reasons

The API maps the scoring service’s reason text to the public reason types:

| Public type | Current text | Condition |
|---|---|---|
| `SIMILAR_USERS` | `Users with similar ratings also liked this movie.` | Hybrid strategy with a positive collaborative signal. |
| `GENRE_MATCH` | `Matches genres from movies you rated highly.` | Non-popularity strategy with a positive content signal. |
| `POPULAR` | `Highly rated by Neo4flix users.` | Fallback when the other signals do not explain the result. |

The API never exposes peer email/name, peer rating history, password/authentication data, or identity-bearing vectors. Normalized signal values and bounded aggregate fields are safe explanation data; private graph identities are not.

## Evidence mapping

- `RecommendationScoringServiceTest` proves strategy thresholds, preference conversion, weighted scores, clamping, and reason precedence.
- `RecommendationGoldenFixtureIT` proves deterministic ranking, Blade Runner’s collaborative candidacy, already-rated exclusion, cold-start strategies, filters, negative-only behavior, score bounds, and live GDS execution.
- `RecommendationNeo4jRepository` contains the parameterized profile, collaborative, genre, and candidate Cypher used at runtime.
- `RecommendationApplicationService` applies the score, reason, deterministic ordering, and result limit.
