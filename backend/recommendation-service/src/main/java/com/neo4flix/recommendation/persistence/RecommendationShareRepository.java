package com.neo4flix.recommendation.persistence;

import com.neo4flix.recommendation.share.RecommendationShareModels;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.neo4flix.recommendation.share.RecommendationShareModels.*;

public interface RecommendationShareRepository {

    String CREATE_QUERY = """
            MATCH (u:User {id: $ownerId}), (m:Movie {id: $movieId})
            CREATE (s:RecommendationShare {id: $shareId, publicTokenHash: $tokenHash,
                    createdAt: $createdAt, expiresAt: $expiresAt, revokedAt: null})
            CREATE (u)-[:CREATED_SHARE]->(s)
            CREATE (s)-[:SHARES]->(m)
            RETURN s.id AS id, m.id AS movieId, s.createdAt AS createdAt,
                   s.expiresAt AS expiresAt, s.revokedAt AS revokedAt
            """;

    String OWNER_LIST_QUERY = """
            MATCH (u:User {id: $ownerId})-[:CREATED_SHARE]->(s:RecommendationShare)-[:SHARES]->(m:Movie)
            RETURN s.id AS id, m.id AS movieId, s.createdAt AS createdAt,
                   s.expiresAt AS expiresAt, s.revokedAt AS revokedAt
            ORDER BY s.createdAt DESC
            """;

    String OWNER_GET_QUERY = """
            MATCH (u:User {id: $ownerId})-[:CREATED_SHARE]->(s:RecommendationShare {id: $shareId})-[:SHARES]->(m:Movie)
            RETURN s.id AS id, m.id AS movieId, s.createdAt AS createdAt,
                   s.expiresAt AS expiresAt, s.revokedAt AS revokedAt
            """;

    String OWNER_UPDATE_QUERY = """
            MATCH (u:User {id: $ownerId})-[:CREATED_SHARE]->(s:RecommendationShare {id: $shareId})
            SET s.expiresAt = $expiresAt,
                s.revokedAt = CASE WHEN $revoke THEN $now ELSE s.revokedAt END
            WITH u, s
            MATCH (s)-[:SHARES]->(m:Movie)
            RETURN s.id AS id, m.id AS movieId, s.createdAt AS createdAt,
                   s.expiresAt AS expiresAt, s.revokedAt AS revokedAt
            """;

    String OWNER_DELETE_QUERY = """
            MATCH (u:User {id: $ownerId})-[:CREATED_SHARE]->(s:RecommendationShare {id: $shareId})
            DETACH DELETE s
            RETURN count(s) AS deleted
            """;

    String PUBLIC_QUERY = """
            MATCH (s:RecommendationShare {publicTokenHash: $tokenHash})-[:SHARES]->(m:Movie)
            WHERE s.revokedAt IS NULL
              AND (s.expiresAt IS NULL OR s.expiresAt > datetime($now))
            OPTIONAL MATCH (m)-[:IN_GENRE]->(g:Genre)
            OPTIONAL MATCH (m)<-[rating:RATED]-(:User)
            WITH s, m, collect(DISTINCT CASE WHEN g IS NULL THEN null ELSE {id: g.id, name: g.name} END) AS genres,
                 avg(rating.score) AS averageRating, count(DISTINCT rating) AS ratingCount
            RETURN s.id AS id, m.id AS movieId, s.expiresAt AS expiresAt,
                   m.title AS title, m.overview AS overview,
                   m.releaseYear AS releaseYear, m.releaseDate AS releaseDate,
                   m.posterUrl AS posterUrl, genres, averageRating, ratingCount
            """;

    Optional<OwnerView> create(String ownerId, String movieId, String shareId, String tokenHash,
                                Instant createdAt, Instant expiresAt);

    List<OwnerView> listOwned(String ownerId);

    Optional<OwnerView> findOwned(String ownerId, String shareId);

    Optional<OwnerView> updateOwned(String ownerId, String shareId, Instant expiresAt, boolean revoke, Instant now);

    boolean deleteOwned(String ownerId, String shareId);

    Optional<PublicView> findPublic(String tokenHash, Instant now);

    static ZonedDateTime utc(Instant value) {
        return value.atZone(ZoneOffset.UTC);
    }

    static Map<String, Object> parameters(String ownerId, String movieId, String shareId, String tokenHash,
                                           Instant createdAt, Instant expiresAt) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("ownerId", ownerId);
        parameters.put("movieId", movieId);
        parameters.put("shareId", shareId);
        parameters.put("tokenHash", tokenHash);
        parameters.put("createdAt", utc(createdAt));
        parameters.put("expiresAt", utc(expiresAt));
        return parameters;
    }

    @Repository("recommendationShareRepository")
    class Neo4j implements RecommendationShareRepository {
        private final Neo4jClient client;

        public Neo4j(Neo4jClient client) {
            this.client = client;
        }

        @Override
        public Optional<OwnerView> create(String ownerId, String movieId, String shareId, String tokenHash,
                                          Instant createdAt, Instant expiresAt) {
            return client.query(CREATE_QUERY)
                    .bindAll(parameters(ownerId, movieId, shareId, tokenHash, createdAt, expiresAt))
                    .fetch()
                    .all().stream().map(RecommendationShareRepository.Neo4j::mapOwner).findFirst();
        }

        @Override
        public List<OwnerView> listOwned(String ownerId) {
            return client.query(OWNER_LIST_QUERY)
                    .bind(ownerId).to("ownerId")
                    .fetch()
                    .all().stream().map(RecommendationShareRepository.Neo4j::mapOwner).toList();
        }

        @Override
        public Optional<OwnerView> findOwned(String ownerId, String shareId) {
            return client.query(OWNER_GET_QUERY)
                    .bindAll(Map.of("ownerId", ownerId, "shareId", shareId))
                    .fetch()
                    .all().stream().map(RecommendationShareRepository.Neo4j::mapOwner).findFirst();
        }

        @Override
        public Optional<OwnerView> updateOwned(String ownerId, String shareId, Instant expiresAt,
                                               boolean revoke, Instant now) {
            return client.query(OWNER_UPDATE_QUERY)
                    .bindAll(Map.of("ownerId", ownerId, "shareId", shareId, "expiresAt", utc(expiresAt),
                            "revoke", revoke, "now", utc(now)))
                    .fetch()
                    .all().stream().map(RecommendationShareRepository.Neo4j::mapOwner).findFirst();
        }

        @Override
        public boolean deleteOwned(String ownerId, String shareId) {
            return client.query(OWNER_DELETE_QUERY)
                    .bindAll(Map.of("ownerId", ownerId, "shareId", shareId))
                    .fetchAs(Long.class)
                    .mappedBy((typeSystem, record) -> record.get("deleted").asLong())
                    .one()
                    .orElse(0L) == 1L;
        }

        @Override
        public Optional<PublicView> findPublic(String tokenHash, Instant now) {
            return client.query(PUBLIC_QUERY)
                    .bindAll(Map.of("tokenHash", tokenHash, "now", utc(now)))
                    .fetch()
                    .all().stream().map(RecommendationShareRepository.Neo4j::mapPublic).findFirst();
        }

        private static OwnerView mapOwner(Map<String, Object> row) {
            return new OwnerView(string(row.get("id")), string(row.get("movieId")), instant(row.get("createdAt")),
                    instant(row.get("expiresAt")), row.get("revokedAt") != null);
        }

        private static PublicView mapPublic(Map<String, Object> row) {
            List<Genre> genres = row.get("genres") instanceof List<?> values
                    ? values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .map(value -> new Genre(string(value.get("id")), string(value.get("name")))).toList()
                    : List.of();
            PublicMovie movie = new PublicMovie(string(row.get("movieId")), string(row.get("title")),
                    string(row.get("overview")), integer(row.get("releaseYear")), string(row.get("releaseDate")),
                    string(row.get("posterUrl")), genres, number(row.get("averageRating")), longValue(row.get("ratingCount")));
            return new PublicView(string(row.get("id")), string(row.get("movieId")), instant(row.get("expiresAt")), movie);
        }

        private static String string(Object value) { return value == null ? null : String.valueOf(value); }

        private static Integer integer(Object value) { return value instanceof Number number ? number.intValue() : null; }

        private static long longValue(Object value) { return value instanceof Number number ? number.longValue() : 0L; }

        private static double number(Object value) { return value instanceof Number number ? number.doubleValue() : 0.0; }

        private static Instant instant(Object value) {
            if (value instanceof Instant instant) return instant;
            if (value instanceof ZonedDateTime dateTime) return dateTime.toInstant();
            if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
            if (value == null) return null;
            return Instant.parse(String.valueOf(value));
        }
    }
}
