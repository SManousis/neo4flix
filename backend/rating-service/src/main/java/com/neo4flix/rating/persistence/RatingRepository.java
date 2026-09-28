package com.neo4flix.rating.persistence;

import com.neo4flix.rating.api.RatingHistoryEntry;
import com.neo4flix.rating.api.RatingPageResponse;
import com.neo4flix.rating.api.RatingResponse;
import com.neo4flix.rating.api.RatingSummaryResponse;
import com.neo4flix.rating.api.RatingWriteRequest;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.ClientException;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RatingRepository {

    RatingResponse create(String userId, RatingWriteRequest request);

    Optional<RatingResponse> findOwn(String userId, String movieId);

    Optional<RatingResponse> updateOwn(String userId, String movieId, int score);

    void deleteOwn(String userId, String movieId);

    RatingPageResponse findHistory(String userId, int page, int size);

    RatingSummaryResponse findSummary(String movieId);

    @Repository("ratingRepository")
    class Neo4j implements RatingRepository {

        private static final String CREATE = """
                MATCH (user:User {id: $userId})
                MATCH (movie:Movie {id: $movieId})
                CREATE (user)-[rated:RATED {key: $key, score: $score, createdAt: $now, updatedAt: $now}]->(movie)
                RETURN movie.id AS movieId, rated.score AS score, rated.createdAt AS createdAt, rated.updatedAt AS updatedAt
                """;
        private static final String FIND_OWN = """
                MATCH (user:User {id: $userId})-[rated:RATED {key: $key}]->(movie:Movie {id: $movieId})
                RETURN movie.id AS movieId, rated.score AS score, rated.createdAt AS createdAt, rated.updatedAt AS updatedAt
                """;
        private static final String UPDATE = """
                MATCH (user:User {id: $userId})-[rated:RATED {key: $key}]->(movie:Movie {id: $movieId})
                SET rated.score = $score, rated.updatedAt = $now
                RETURN movie.id AS movieId, rated.score AS score, rated.createdAt AS createdAt, rated.updatedAt AS updatedAt
                """;
        private static final String DELETE = """
                MATCH (user:User {id: $userId})-[rated:RATED {key: $key}]->(movie:Movie {id: $movieId})
                DELETE rated
                """;
        private static final String HISTORY = """
                MATCH (user:User {id: $userId})-[rated:RATED]->(movie:Movie)
                RETURN movie.id AS movieId, movie.title AS movieTitle, rated.score AS score,
                       rated.createdAt AS createdAt, rated.updatedAt AS updatedAt
                ORDER BY rated.updatedAt DESC, movie.normalizedTitle ASC
                SKIP $skip LIMIT $size
                """;
        private static final String HISTORY_COUNT = """
                MATCH (user:User {id: $userId})-[rated:RATED]->(movie:Movie)
                RETURN count(rated) AS total
                """;
        private static final String SUMMARY = """
                MATCH (movie:Movie {id: $movieId})
                OPTIONAL MATCH (:User)-[rated:RATED]->(movie)
                RETURN movie.id AS movieId, avg(rated.score) AS averageRating, count(rated) AS ratingCount
                """;

        private final Neo4jClient client;

        public Neo4j(Neo4jClient client) {
            this.client = client;
        }

        @Override
        public RatingResponse create(String userId, RatingWriteRequest request) {
            Instant now = Instant.now();
            try {
                return client.query(CREATE)
                        .bindAll(Map.of(
                                "userId", userId,
                                "movieId", request.movieId(),
                                "key", RatedRelationship.keyFor(userId, request.movieId()),
                                "score", request.score(),
                                "now", utc(now)))
                        .fetchAs(RatingResponse.class)
                        .mappedBy((types, record) -> mapResponse(record))
                        .one()
                        .orElseThrow(() -> new TargetNotFoundException(request.movieId()));
            } catch (ClientException exception) {
                if ("Neo.ClientError.Schema.ConstraintValidationFailed".equals(exception.code())) {
                    throw new DuplicateRatingException("Rating already exists", exception);
                }
                throw exception;
            }
        }

        @Override
        public Optional<RatingResponse> findOwn(String userId, String movieId) {
            return client.query(FIND_OWN)
                    .bindAll(parameters(userId, movieId))
                    .fetchAs(RatingResponse.class)
                    .mappedBy((types, record) -> mapResponse(record))
                    .one();
        }

        @Override
        public Optional<RatingResponse> updateOwn(String userId, String movieId, int score) {
            Map<String, Object> parameters = new HashMap<>(parameters(userId, movieId));
            parameters.put("score", score);
            parameters.put("now", utc(Instant.now()));
            return client.query(UPDATE)
                    .bindAll(parameters)
                    .fetchAs(RatingResponse.class)
                    .mappedBy((types, record) -> mapResponse(record))
                    .one();
        }

        @Override
        public void deleteOwn(String userId, String movieId) {
            client.query(DELETE).bindAll(parameters(userId, movieId)).run();
        }

        @Override
        public RatingPageResponse findHistory(String userId, int page, int size) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("userId", userId);
            parameters.put("skip", (long) page * size);
            parameters.put("size", size);
            List<RatingHistoryEntry> content = new ArrayList<>(client.query(HISTORY)
                    .bindAll(parameters)
                    .fetchAs(RatingHistoryEntry.class)
                    .mappedBy((types, record) -> mapHistory(record))
                    .all());
            long total = client.query(HISTORY_COUNT)
                    .bind(userId).to("userId")
                    .fetchAs(Long.class)
                    .mappedBy((types, record) -> record.get("total").asLong())
                    .one().orElse(0L);
            return new RatingPageResponse(content, page, size, total, (int) Math.ceil((double) total / size));
        }

        @Override
        public RatingSummaryResponse findSummary(String movieId) {
            return client.query(SUMMARY)
                    .bind(movieId).to("movieId")
                    .fetchAs(RatingSummaryResponse.class)
                    .mappedBy((types, record) -> new RatingSummaryResponse(
                            record.get("movieId").asString(),
                            record.get("averageRating").isNull() ? null : record.get("averageRating").asDouble(),
                            record.get("ratingCount").asLong()))
                    .one()
                    .orElseGet(() -> new RatingSummaryResponse(movieId, null, 0));
        }

        private static Map<String, Object> parameters(String userId, String movieId) {
            return Map.of("userId", userId, "movieId", movieId, "key", RatedRelationship.keyFor(userId, movieId));
        }

        private static RatingResponse mapResponse(Record record) {
            return new RatingResponse(
                    record.get("movieId").asString(),
                    record.get("score").asInt(),
                    record.get("createdAt").asZonedDateTime().toInstant(),
                    record.get("updatedAt").asZonedDateTime().toInstant());
        }

        private static RatingHistoryEntry mapHistory(Record record) {
            return new RatingHistoryEntry(
                    record.get("movieId").asString(),
                    record.get("movieTitle").asString(),
                    record.get("score").asInt(),
                    record.get("createdAt").asZonedDateTime().toInstant(),
                    record.get("updatedAt").asZonedDateTime().toInstant());
        }

        private static Object utc(Instant value) {
            return value.atZone(ZoneOffset.UTC);
        }
    }

    class TargetNotFoundException extends RuntimeException {
        public TargetNotFoundException(String movieId) {
            super("Movie not found: " + movieId);
        }
    }

    class DuplicateRatingException extends RuntimeException {
        public DuplicateRatingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
