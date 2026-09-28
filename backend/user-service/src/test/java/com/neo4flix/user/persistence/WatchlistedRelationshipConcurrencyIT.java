package com.neo4flix.user.persistence;

import com.neo4flix.platform.common.test.Neo4jGdsContainer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistedRelationshipConcurrencyIT {

    private static final String USER_ID = "u-1";
    private static final String MOVIE_ID = "m-1";
    private static final String KEY = "u-1:m-1";
    private static final int SCORE = 5;
    private static final String MERGE_WATCHLISTED = """
            MATCH (user:User {id: $userId})
            MATCH (movie:Movie {id: $movieId})
            MERGE (user)-[watchlisted:WATCHLISTED {key: $key}]->(movie)
            ON CREATE SET watchlisted.createdAt = datetime()
            RETURN $key AS key
            """;
    private static final String CREATE_NODES = """
            MERGE (:User {id: $userId})
            MERGE (:Movie {id: $movieId})
            """;
    private static final String COUNT_WATCHLISTED = """
            MATCH (:User {id: $userId})-[watchlisted:WATCHLISTED {key: $key}]->(:Movie {id: $movieId})
            RETURN count(watchlisted) AS count
            """;

    @Test
    void concurrentMergesSucceedTwiceAndLeaveOneWatchlistedRelationship() throws Exception {
        try (Neo4jGdsContainer neo4j = Neo4jGdsContainer.start()) {
            neo4j.runMigrator("migrate");
            neo4j.runCypher(CREATE_NODES, parameters());

            List<String> results = concurrently(() -> mergeWatchlisted(neo4j));

            assertThat(results).containsExactly("u-1:m-1", "u-1:m-1");
            assertThat(neo4j.runCypher(COUNT_WATCHLISTED, parameters()))
                    .singleElement()
                    .extracting(record -> record.get("count").asLong())
                    .isEqualTo(1L);
        }
    }

    private static String mergeWatchlisted(Neo4jGdsContainer neo4j) {
        var records = neo4j.runWriteCypher(MERGE_WATCHLISTED, parameters());
        assertThat(records).hasSize(1);
        return records.getFirst()
                .get("key")
                .asString();
    }

    private static List<String> concurrently(ConcurrentMerge operation) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = List.of(
                    executor.submit(() -> awaitStart(ready, start, operation)),
                    executor.submit(() -> awaitStart(ready, start, operation)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    private static String awaitStart(CountDownLatch ready, CountDownLatch start, ConcurrentMerge operation)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return operation.merge();
    }

    private static Map<String, Object> parameters() {
        return Map.of("userId", USER_ID, "movieId", MOVIE_ID, "key", KEY, "score", SCORE);
    }

    @FunctionalInterface
    private interface ConcurrentMerge {
        String merge();
    }
}
