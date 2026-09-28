package com.neo4flix.recommendation.persistence;

import com.neo4flix.recommendation.core.RecommendationDtos;
import com.neo4flix.recommendation.core.RecommendationConfiguration;
import com.neo4flix.recommendation.core.RecommendationScoringService;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RecommendationNeo4jRepository implements RecommendationRepository {

    static final String PROFILE_QUERY = """
            MATCH (:User {id: $userId})-[rated:RATED]->(:Movie)
            RETURN count(rated) AS ratingCount
            """;

    static final String COLLABORATIVE_QUERY = """
            MATCH (me:User {id: $userId})-[mine:RATED]->(movie:Movie)<-[theirs:RATED]-(peer:User)
            WHERE peer <> me
            WITH me, peer, movie, mine, theirs
            ORDER BY peer.id, movie.id
            WITH me, peer,
                 collect(toFloat(mine.score)) AS myScores,
                 collect(toFloat(theirs.score)) AS peerScores,
                 count(movie) AS commonMovies
            WHERE commonMovies >= $minimumOverlap
            WITH me, peer, commonMovies,
                 gds.similarity.cosine(myScores, peerScores) AS similarity
            ORDER BY similarity DESC
            LIMIT $peerLimit
            MATCH (peer)-[peerRating:RATED]->(movie:Movie)
            WHERE peerRating.score >= 4 AND NOT (me)-[:RATED]->(movie)
            WITH movie,
                 CASE WHEN sum(similarity) = 0 THEN 0.0
                      ELSE sum(similarity * ((toFloat(peerRating.score) - 3.0) / 2.0)) / sum(similarity)
                 END AS collaborativeScore,
                 count(DISTINCT peer) AS peerCount,
                 max(commonMovies) AS commonMovies
            RETURN movie.id AS movieId, collaborativeScore, peerCount, commonMovies
            ORDER BY movie.id
            LIMIT $candidateLimit
            """;

    static final String QUALIFYING_PEER_QUERY = """
            MATCH (me:User {id: $userId})-[:RATED]->(movie:Movie)<-[:RATED]-(peer:User)
            WHERE peer <> me
            WITH peer, count(movie) AS commonMovies
            WHERE commonMovies >= $minimumOverlap
            RETURN count(peer) AS qualifyingPeerCount
            """;

    static final String GENRE_QUERY = """
            MATCH (:User {id: $userId})-[rated:RATED]->(:Movie)-[:IN_GENRE]->(genre:Genre)
            RETURN genre.name AS genre,
                   avg(CASE rated.score
                         WHEN 1 THEN -1.0
                         WHEN 2 THEN -0.5
                         WHEN 3 THEN 0.0
                         WHEN 4 THEN 0.5
                         WHEN 5 THEN 1.0
                       END) AS preference
            """;

    static final String CANDIDATE_QUERY = """
            MATCH (me:User {id: $userId})
            MATCH (movie:Movie)
            WHERE NOT (me)-[:RATED]->(movie)
            OPTIONAL MATCH (movie)-[:IN_GENRE]->(genre:Genre)
            OPTIONAL MATCH (movie)<-[rated:RATED]-(:User)
            WITH movie, collect(DISTINCT CASE WHEN genre IS NULL THEN null ELSE {id: genre.id, name: genre.name} END) AS genres,
                 avg(rated.score) AS averageRating, count(rated) AS ratingCount
            WHERE ($genre IS NULL OR $genre IN [g IN genres WHERE g IS NOT NULL | g.name])
              AND ($fromYear IS NULL OR movie.releaseYear >= $fromYear)
              AND ($toYear IS NULL OR movie.releaseYear <= $toYear)
              AND ($minimumAverageRating IS NULL OR averageRating >= $minimumAverageRating)
            RETURN movie.id AS movieId, movie.title AS title, movie.overview AS overview,
                   movie.releaseYear AS releaseYear, movie.releaseDate AS releaseDate,
                   movie.posterUrl AS posterUrl, genres, averageRating, ratingCount
            ORDER BY movie.id ASC
            LIMIT $candidateLimit
            """;

    private final Neo4jClient client;
    private final RecommendationScoringService scoring;
    private final double popularityPriorCount;

    public RecommendationNeo4jRepository(Neo4jClient client) {
        this(client, new RecommendationScoringService(), RecommendationConfiguration.defaults());
    }

    @Autowired
    public RecommendationNeo4jRepository(Neo4jClient client,
                                          RecommendationScoringService scoring,
                                          RecommendationConfiguration configuration) {
        this.client = client;
        this.scoring = scoring;
        this.popularityPriorCount = configuration.popularityPriorCount();
    }

    @Override
    public Snapshot snapshot(RecommendationDtos.Query query) {
        Map<String, Object> parameters = parameters(query, popularityPriorCount);
        int ratingCount = client.query(PROFILE_QUERY)
                .bindAll(parameters)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("ratingCount").asLong())
                .one()
                .orElse(0L)
                .intValue();

        boolean qualifyingPeer = client.query(QUALIFYING_PEER_QUERY)
                .bindAll(parameters)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("qualifyingPeerCount").asLong())
                .one()
                .orElse(0L) > 0;

        Map<String, Map<String, Object>> collaborative = new HashMap<>();
        client.query(COLLABORATIVE_QUERY)
                .bindAll(parameters)
                .fetch()
                .all()
                .forEach(row -> collaborative.put(String.valueOf(row.get("movieId")), row));

        Map<String, Double> genrePreferences = new HashMap<>();
        client.query(GENRE_QUERY)
                .bindAll(parameters)
                .fetch()
                .all()
                .forEach(row -> genrePreferences.put(String.valueOf(row.get("genre")), number(row.get("preference"))));

        List<RecommendationDtos.SignalRow> rows = new ArrayList<>();
        client.query(CANDIDATE_QUERY)
                .bindAll(parameters)
                .fetch()
                .all()
                .forEach(row -> rows.add(enrich(row, collaborative.get(String.valueOf(row.get("movieId"))),
                        genrePreferences, popularityPriorCount)));
        return new Snapshot(ratingCount, qualifyingPeer, rows);
    }

    static Map<String, Object> parameters(RecommendationDtos.Query query, double priorCount) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", query.userId());
        parameters.put("minimumOverlap", query.minimumOverlap());
        parameters.put("peerLimit", query.peerLimit());
        parameters.put("candidateLimit", query.candidateLimit());
        parameters.put("genre", query.genre());
        parameters.put("fromYear", query.fromYear());
        parameters.put("toYear", query.toYear());
        parameters.put("minimumAverageRating", query.minimumAverageRating());
        parameters.put("priorCount", priorCount);
        return parameters;
    }

    static RecommendationDtos.SignalRow mapSignalRow(Map<String, Object> row) {
        return new RecommendationDtos.SignalRow(
                string(row.get("movieId")),
                string(row.get("title")),
                string(row.get("overview")),
                integer(row.get("releaseYear")),
                string(row.get("posterUrl")),
                strings(row.get("genres")),
                number(row.get("collaborativeScore")),
                number(row.get("contentScore")),
                number(row.get("popularityScore")),
                integerOrZero(row.get("peerCount")),
                integerOrZero(row.get("commonMovies")),
                number(row.get("averageRating")),
                longOrZero(row.get("ratingCount")),
                string(row.get("releaseDate")),
                genreIds(row.get("genres")));
    }

    private RecommendationDtos.SignalRow enrich(Map<String, Object> movie,
                                                Map<String, Object> collaborative,
                                                Map<String, Double> genrePreferences,
                                                double priorCount) {
        Map<String, Object> row = new HashMap<>(movie);
        List<String> genres = genreNames(movie.get("genres"));
        row.put("contentScore", contentScore(genres, genrePreferences));
        row.put("popularityScore", scoring.popularityScore(
                number(movie.get("averageRating")), longOrZero(movie.get("ratingCount")), priorCount));
        if (collaborative != null) {
            row.put("collaborativeScore", number(collaborative.get("collaborativeScore")));
            row.put("peerCount", collaborative.get("peerCount"));
            row.put("commonMovies", collaborative.get("commonMovies"));
        }
        return mapSignalRow(row);
    }

    static double contentScore(List<String> genres, Map<String, Double> genrePreferences) {
        List<Double> knownPreferences = genres.stream()
                .map(genrePreferences::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (knownPreferences.isEmpty()) {
            return 0.0;
        }
        double averagePreference = knownPreferences.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return RecommendationScoringService.clamp((averagePreference + 1.0) / 2.0);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        return value == null ? null : integerOrZero(value);
    }

    private static int integerOrZero(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long longOrZero(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static List<String> genreNames(Object value) {
        if (value instanceof List<?> values && values.stream().allMatch(String.class::isInstance)) {
            return strings(value);
        }
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(row -> row.get("name"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private static List<String> genreIds(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(row -> row.get("id"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
