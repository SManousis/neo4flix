package com.neo4flix.user.watchlist;

import com.neo4flix.platform.common.test.Neo4jGdsContainer;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistConcurrencyIT {

    private static final String CREATE_GRAPH = """
            MERGE (first:User {id: 'watch-user-1'})
            MERGE (second:User {id: 'watch-user-2'})
            SET first.email = 'watch-user-1@example.test', first.normalizedEmail = 'watch-user-1@example.test',
                first.displayName = 'Watch User 1', first.passwordHash = 'test', first.role = 'USER', first.enabled = true,
                first.twoFactorEnabled = false, first.createdAt = datetime(), first.updatedAt = datetime()
            SET second.email = 'watch-user-2@example.test', second.normalizedEmail = 'watch-user-2@example.test',
                second.displayName = 'Watch User 2', second.passwordHash = 'test', second.role = 'USER', second.enabled = true,
                second.twoFactorEnabled = false, second.createdAt = datetime(), second.updatedAt = datetime()
            MERGE (firstMovie:Movie {id: 'watch-movie-1'})
            SET firstMovie.title = 'Watch Arrival', firstMovie.overview = 'First contact', firstMovie.releaseYear = 2016,
                firstMovie.posterUrl = ''
            MERGE (secondMovie:Movie {id: 'watch-movie-2'})
            SET secondMovie.title = 'Watch Matrix', secondMovie.overview = 'Simulation', secondMovie.releaseYear = 1999,
                secondMovie.posterUrl = ''
            """;
    private static final String COUNT = """
            MATCH (:User {id: $userId})-[watchlisted:WATCHLISTED {key: $key}]->(:Movie {id: $movieId})
            RETURN count(watchlisted) AS count
            """;

    @Test
    void concurrentAddsAreIdempotentAndUsersRemainIsolated() throws Exception {
        try (Neo4jGdsContainer neo4j = Neo4jGdsContainer.start()) {
            neo4j.runMigrator("migrate");
            neo4j.runCypher(CREATE_GRAPH, Map.of());
            WatchlistRepository repository = new WatchlistRepository.Neo4j(Neo4jClient.create(neo4j.driver()));

            List<Boolean> results = concurrently(() -> repository.add("watch-user-1", "watch-movie-1"));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(neo4j.runCypher(COUNT, Map.of(
                    "userId", "watch-user-1", "movieId", "watch-movie-1", "key", "watch-user-1:watch-movie-1")))
                    .singleElement()
                    .extracting(record -> record.get("count").asLong())
                    .isEqualTo(1L);
            assertThat(repository.findMine("watch-user-1", 0, 24).content())
                    .extracting(WatchlistDtos.MovieEntry::movieId)
                    .containsExactly("watch-movie-1");
            repository.add("watch-user-2", "watch-movie-2");
            assertThat(repository.findMine("watch-user-1", 0, 24).content())
                    .extracting(WatchlistDtos.MovieEntry::movieId)
                    .doesNotContain("watch-movie-2");
            repository.remove("watch-user-1", "watch-movie-1");
            assertThat(repository.findMine("watch-user-2", 0, 24).content())
                    .extracting(WatchlistDtos.MovieEntry::movieId)
                    .containsExactly("watch-movie-2");
        }
    }

    private static List<Boolean> concurrently(ConcurrentAdd operation) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = List.of(
                    executor.submit(() -> awaitStart(ready, start, operation)),
                    executor.submit(() -> awaitStart(ready, start, operation)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    private static boolean awaitStart(CountDownLatch ready, CountDownLatch start, ConcurrentAdd operation)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return operation.add();
    }

    @FunctionalInterface
    private interface ConcurrentAdd {
        boolean add();
    }
}
