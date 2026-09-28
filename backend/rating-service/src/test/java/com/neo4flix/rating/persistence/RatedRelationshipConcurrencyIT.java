package com.neo4flix.rating.persistence;

import com.neo4flix.platform.common.test.Neo4jGdsContainer;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ClientException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RatedRelationshipConcurrencyIT {

    private static final String USER_ID = "u-1";
    private static final String MOVIE_ID = "m-1";
    private static final String KEY = "u-1:m-1";
    private static final int SCORE = 5;
    private static final String CREATE_RATED = """
            MATCH (user:User {id: $userId})
            MATCH (movie:Movie {id: $movieId})
            CREATE (user)-[:RATED {key: $key, score: $score, createdAt: datetime(), updatedAt: datetime()}]->(movie)
            RETURN $key AS key
            """;
    private static final String CREATE_NODES = """
            MERGE (:User {id: $userId})
            MERGE (:Movie {id: $movieId})
            """;
    private static final String COUNT_RATED = """
            MATCH (:User {id: $userId})-[rated:RATED {key: $key}]->(:Movie {id: $movieId})
            RETURN count(rated) AS count
            """;

    @Test
    void concurrentCreatesMapTheConstraintViolationToDuplicateAndLeaveOneRatedRelationship() throws Exception {
        try (Neo4jGdsContainer neo4j = Neo4jGdsContainer.start()) {
            neo4j.runMigrator("migrate");
            neo4j.runCypher(CREATE_NODES, parameters());

            List<CreateResult> results = concurrently(() -> createRated(neo4j));

            assertThat(results).containsExactlyInAnyOrder(CreateResult.CREATED, CreateResult.DUPLICATE);
            assertThat(neo4j.runCypher(COUNT_RATED, parameters()))
                    .singleElement()
                    .extracting(record -> record.get("count").asLong())
                    .isEqualTo(1L);
        }
    }

    private static CreateResult createRated(Neo4jGdsContainer neo4j) {
        try {
            neo4j.runWriteCypher(CREATE_RATED, parameters());
            return CreateResult.CREATED;
        }
        catch (ClientException exception) {
            if ("Neo.ClientError.Schema.ConstraintValidationFailed".equals(exception.code())) {
                return CreateResult.DUPLICATE;
            }
            throw exception;
        }
    }

    private static List<CreateResult> concurrently(ConcurrentCreate operation) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CreateResult>> futures = List.of(
                    executor.submit(() -> awaitStart(ready, start, operation)),
                    executor.submit(() -> awaitStart(ready, start, operation)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    private static CreateResult awaitStart(CountDownLatch ready, CountDownLatch start, ConcurrentCreate operation)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return operation.create();
    }

    private static Map<String, Object> parameters() {
        return Map.of("userId", USER_ID, "movieId", MOVIE_ID, "key", KEY, "score", SCORE);
    }

    @FunctionalInterface
    private interface ConcurrentCreate {
        CreateResult create();
    }

    private enum CreateResult {
        CREATED,
        DUPLICATE
    }
}
