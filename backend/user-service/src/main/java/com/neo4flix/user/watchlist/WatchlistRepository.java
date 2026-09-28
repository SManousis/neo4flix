package com.neo4flix.user.watchlist;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

public interface WatchlistRepository {

    boolean add(String userId, String movieId);

    boolean remove(String userId, String movieId);

    WatchlistDtos.PageResponse findMine(String userId, int page, int size);

    static String keyFor(String userId, String movieId) {
        return userId + ":" + movieId;
    }

    @Repository("watchlistRepository")
    class Neo4j implements WatchlistRepository {

        private static final String ADD = """
                WITH randomUUID() AS marker
                MATCH (user:User {id: $userId})
                MATCH (movie:Movie {id: $movieId})
                MERGE (user)-[watchlisted:WATCHLISTED {key: $key}]->(movie)
                ON CREATE SET watchlisted.createdAt = datetime(), watchlisted._createdMarker = marker
                WITH watchlisted, marker, coalesce(watchlisted._createdMarker = marker, false) AS added
                REMOVE watchlisted._createdMarker
                RETURN added
                """;
        private static final String REMOVE = """
                MATCH (user:User {id: $userId})-[watchlisted:WATCHLISTED {key: $key}]->(movie:Movie {id: $movieId})
                DELETE watchlisted
                RETURN true AS removed
                """;
        private static final String COUNT = """
                MATCH (user:User {id: $userId})-[:WATCHLISTED]->(movie:Movie)
                RETURN count(movie) AS total
                """;
        private static final String PAGE = """
                MATCH (user:User {id: $userId})-[watchlisted:WATCHLISTED]->(movie:Movie)
                RETURN movie.id AS movieId, movie.title AS title, movie.overview AS overview,
                       movie.releaseYear AS releaseYear, movie.posterUrl AS posterUrl,
                       watchlisted.createdAt AS createdAt
                ORDER BY watchlisted.createdAt DESC, movie.title ASC
                SKIP $skip LIMIT $size
                """;

        private final Neo4jClient client;

        public Neo4j(Neo4jClient client) {
            this.client = client;
        }

        @Override
        public boolean add(String userId, String movieId) {
            return client.query(ADD)
                    .bindAll(parameters(userId, movieId))
                    .fetchAs(Boolean.class)
                    .mappedBy((typeSystem, record) -> record.get("added").asBoolean())
                    .one()
                    .orElseThrow(MovieNotFoundException::new);
        }

        @Override
        public boolean remove(String userId, String movieId) {
            return client.query(REMOVE)
                    .bindAll(parameters(userId, movieId))
                    .fetchAs(Boolean.class)
                    .mappedBy((typeSystem, record) -> record.get("removed").asBoolean())
                    .one()
                    .orElse(false);
        }

        @Override
        public WatchlistDtos.PageResponse findMine(String userId, int page, int size) {
            long total = client.query(COUNT)
                    .bind(userId).to("userId")
                    .fetchAs(Long.class)
                    .mappedBy((typeSystem, record) -> record.get("total").asLong())
                    .one()
                    .orElse(0L);
            List<WatchlistDtos.MovieEntry> entries = List.copyOf(client.query(PAGE)
                    .bindAll(Map.of("userId", userId, "skip", page * size, "size", size))
                    .fetchAs(WatchlistDtos.MovieEntry.class)
                    .mappedBy((typeSystem, record) -> mapEntry(record))
                    .all());
            int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
            return new WatchlistDtos.PageResponse(entries, page, size, total, totalPages);
        }

        private static Map<String, Object> parameters(String userId, String movieId) {
            return Map.of("userId", userId, "movieId", movieId, "key", keyFor(userId, movieId));
        }

        private static WatchlistDtos.MovieEntry mapEntry(Record record) {
            return new WatchlistDtos.MovieEntry(
                    record.get("movieId").asString(),
                    record.get("title").asString(),
                    nullableString(record.get("overview")),
                    nullableInteger(record.get("releaseYear")),
                    nullableString(record.get("posterUrl")),
                    record.get("createdAt").asZonedDateTime().toInstant());
        }

        private static String nullableString(Value value) {
            return value.isNull() ? null : value.asString();
        }

        private static Integer nullableInteger(Value value) {
            return value.isNull() ? null : value.asInt();
        }

    }

    final class MovieNotFoundException extends RuntimeException {
    }
}
