# Neo4flix — Recommendation Engine and Audit Validation

> **Authority:** This document owns recommendation strategy/scoring/candidate rules, deterministic validation fixtures, Cypher/GDS explanation expectations, audit mapping, usability validation, and final evaluator evidence requirements.

## 1. Purpose

Neo4flix must not merely store graph-shaped data. The recommendation engine must visibly use the graph in a way that can be tested and explained during the 01-edu audit.

The implementation goal is a deterministic, inspectable **hybrid graph recommender**, not an opaque ML model.

## 2. Recommendation Signals

Mature-user default weights:

```text
collaborative = 0.60
content       = 0.30
popularity    = 0.10
```

These are configurable defaults; configuration validates a 1.0 sum.

## 3. Rating Preference Mapping

For content preference:

```text
1★ → -1.0
2★ → -0.5
3★ →  0.0
4★ → +0.5
5★ → +1.0
```

Low ratings are not positive engagement.

## 4. Collaborative Signal

1. find current user's rated movies
2. find peers rating overlapping movies
3. align rating vectors on common movies
4. compute similarity using GDS cosine baseline
5. enforce minimum overlap
6. keep bounded top peers
7. collect movies peers rated highly
8. exclude already-rated movies
9. normalize collaborative score

Example:

```text
Alice: Matrix 5, Inception 5, Interstellar 4
Bob:   Matrix 5, Inception 4, Interstellar 5, Blade Runner 5
```

Bob is a strong peer; Blade Runner becomes a candidate for Alice.

## 5. GDS Usage

Use Graph Data Science 2026.07.

A clear MVP approach is `gds.similarity.cosine(...)` over aligned rating vectors inside parameterized Cypher instead of maintaining a stale named graph projection after every rating mutation.

GDS must genuinely execute in tests/runtime.

## 6. Illustrative Collaborative Cypher

Production query may differ but should remain explainable:

```cypher
MATCH (me:User {id: $userId})-[mine:RATED]->(m:Movie)
MATCH (peer:User)-[theirs:RATED]->(m)
WHERE peer <> me
WITH peer, m, mine, theirs
ORDER BY m.id
WITH peer,
     collect(toFloat(mine.score)) AS myScores,
     collect(toFloat(theirs.score)) AS peerScores,
     count(m) AS commonMovies
WHERE commonMovies >= $minimumOverlap
WITH peer,
     commonMovies,
     gds.similarity.cosine(myScores, peerScores) AS similarity
RETURN peer.id AS peerId, commonMovies, similarity
ORDER BY similarity DESC
LIMIT $peerLimit
```

All user/config values parameterized; limits bounded.

## 7. Collaborative Candidate Rules

Candidate:

- liked strongly by sufficiently similar peers
- not already rated by current user
- later satisfies requested filters
- score reflects peer similarity, peer rating, and confidence/overlap

Exact normalization must be deterministic/documented.

## 8. Content Signal

MVP content graph is Genre.

1. derive weighted genre preferences from explicit ratings
2. traverse candidate `IN_GENRE`
3. reward overlap with positive preference
4. reduce affinity for repeatedly negative genres
5. normalize 0–1

Important for sparse/cold users.

## 9. Optional Jaccard

GDS Jaccard may be used for genre-set overlap if it improves clarity/performance, but is not mandatory. Do not add complexity only for vocabulary.

## 10. Popularity/Confidence

Derived from average rating + rating count/confidence, normalized 0–1.

Purpose:

- useful zero-rating fallback
- stabilizer against a single isolated 5★ rating

Weak mature-user weight.

## 11. Final Score

```text
finalScore =
  collaborativeWeight * collaborativeScore
+ contentWeight       * contentScore
+ popularityWeight    * popularityScore
```

Bound 0–1; default sort descending.

## 12. Cold Start

### 0 ratings
`POPULARITY`.

### 1–2 ratings baseline
`CONTENT_PLUS_POPULARITY`.

### sufficient history/peer overlap
`HYBRID`.

Thresholds configurable, behavior deterministic/tested.

## 13. Already-Rated Exclusion

Every personalized strategy excludes all movies already rated by current user.

Watchlisted movies are not automatically excluded.

## 14. Filters

Apply to recommendation candidates:

- genre
- year/date
- min aggregate rating
- paging/sort

One engine; no algorithm per filter.

## 15. Explanation Model

Canonical types:

### SIMILAR_USERS
“Users with similar ratings also liked this movie.”

### GENRE_MATCH
“Matches genres from movies you rated highly.”

### POPULAR
“Highly rated by Neo4flix users.”

Reason must correspond to actual signal/strategy.

## 16. Privacy

Never expose:

- peer email/name
- peer rating history
- auth data
- identity-bearing vectors

Normalized signal scores are acceptable.

## 17. Deterministic Audit Fixture

Minimum clusters:

- sci-fi
- romance/drama
- action
- mixed
- fresh zero-rating user

Illustrative:

```text
Alice (sci-fi)
Bob   (sci-fi peer)
Carol (romance)
Diana (romance peer)
Eric  (action)
Grace (mixed)
Fresh (zero ratings)
```

Use enough movie/genre overlap to demonstrate collaborative and content signals.

## 18. Golden Tests

Mandatory:

- Alice/Bob similarity > Alice/Carol by meaningful margin
- Bob-only highly rated sci-fi candidate ranks for Alice
- already-rated excluded
- rating candidate makes it disappear
- repeated low-rated genre does not become positive
- Fresh → POPULARITY
- sparse → CONTENT_PLUS_POPULARITY
- mature → HYBRID
- genre/year/min-rating filters work
- reason matches signal
- score stays in range

## 19. Performance Bounds

Bound:

- peer count
- overlap/candidate work where practical
- candidate count
- final page/limit

Use `EXPLAIN`/`PROFILE` during implementation on representative load data.

## 20. Audit Requirement Mapping

| Audit area | Proof |
|---|---|
| graph representation | Neo4j Browser User-RATED-Movie-IN_GENRE-Genre |
| nodes/relationships | graph spec + visual graph |
| Spring Data Neo4j mapping | entity/relationship-property code/tests |
| four microservices | Docker services + OpenAPI/code |
| CRUD | Movie/User/Rating + RecommendationShare |
| Movie recommendation responsibility | facade delegates to Recommendation Service |
| User rating responsibility | User rating-history read facade; Rating Service writes |
| recommendation algorithm | deterministic hybrid + GDS |
| Cypher understanding | documented/live query explanation |
| login/register/UI | Playwright/manual |
| search | combined filter demo |
| ratings | rating page + graph relation |
| recommendations/filtering | recommendations page |
| watchlist | relation add/remove/list |
| sharing | public link |
| HTTPS | deployed certificate/redirect |
| strong password | negative registration test |
| JWT | valid/invalid/role demo |
| 2FA | enrollment + challenge |
| functionality | automated suites |
| usability | human walkthrough |
| security/malicious input | security matrix |
| stress | k6 report + post-test integrity |

## 21. Graph Demo Query

```cypher
MATCH (u:User)-[r:RATED]->(m:Movie)-[:IN_GENRE]->(g:Genre)
RETURN u, r, m, g
LIMIT 100;
```

Explain why score belongs on `RATED` and why `IN_GENRE` enables content traversal.

## 22. Cypher Concepts Team Must Understand

Actual project usage of:

- MATCH
- OPTIONAL MATCH
- WHERE
- WITH
- collect
- avg
- count
- MERGE for idempotent state
- CREATE + uniqueness for true rating create
- existence subquery
- ORDER BY
- LIMIT
- parameters
- GDS similarity

## 23. Audit Runbook

### A. Startup
Clone/configure/start, show containers/migrations/GDS.

### B. Graph
Open safe local/tunneled Neo4j Browser, run graph demo, explain.

### C. Services
Show four separate Spring services and OpenAPI/health.

### D. Auth
Weak password rejection → register → login → TOTP enrollment → logout → 2FA login.

### E. Movie/Genre CRUD
ADMIN CRUD; USER mutation 403.

### F. Rating CRUD
Create/read/update/delete and inspect `RATED`.

### G. Search
Title + genre + year + rating, including combined filters.

### H. Recommendations
Known user → history → results → explain signals → change rating → show change.

### I. Cold Start
Fresh → sparse → mature strategies.

### J. Watchlist/Share
Add/remove watchlist; create share; anonymous open.

### K. Security
Invalid JWT 401; USER admin 403; Cypher-looking search harmless; controlled errors.

### L. Stress
Run k6; show metrics; verify app/graph afterward.

### M. Verification
Run fresh `make verify`/`make verify-all`.

## 24. Usability Test

One person unfamiliar with implementation attempts without button-by-button coaching:

1. create account
2. find a movie
3. rate it
4. add another to watchlist
5. find recommendations
6. filter
7. share

Record completion, confusion, dead ends, missing feedback, fixes/deferred items.

## 25. Audit Evidence Files

Implementation creates/updates actual evidence:

```text
docs/audit/
├── AUDIT_RUNBOOK.md
├── GRAPH_DEMO.md
├── RECOMMENDATION_EXPLANATION.md
├── SECURITY_CHECKLIST.md
├── TEST_EVIDENCE.md
├── USABILITY_TEST.md
└── STRESS_TEST.md
```

No fake pre-completed evidence.

## 26. Definition of Done

- actual graph/GDS algorithm
- deterministic fixture proof
- cold-start and hybrid separately proven
- negative rating semantics correct
- parameterized/explainable Cypher
- evidence-based reasons
- full runbook usable
- real usability/security/stress evidence
