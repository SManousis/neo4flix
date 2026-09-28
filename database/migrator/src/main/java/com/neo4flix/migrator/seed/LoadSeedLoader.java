package com.neo4flix.migrator.seed;

import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryConfig;

import java.time.Duration;
import java.util.Map;

/** Loads a small deterministic, credential-free scaffold for local load tooling. */
public final class LoadSeedLoader {

    private static final String LOAD_MOVIE_ONE_ID = "66666666-6666-6666-6666-666666666666";
    private static final String LOAD_MOVIE_TWO_ID = "77777777-7777-7777-7777-777777777777";
    private static final String LOAD_MOVIE_THREE_ID = "88888888-8888-8888-8888-888888888888";
    private static final String MERGE_MOVIE = """
            UNWIND $movies AS fixture
            MERGE (movie:Movie {id: fixture.id})
            SET movie.title = fixture.title,
                movie.normalizedTitle = fixture.normalizedTitle,
                movie.releaseYear = fixture.releaseYear
            """;

    public void load(Driver driver, String database) {
        driver.executableQuery(MERGE_MOVIE)
                .withParameters(Map.of("movies", java.util.List.of(
                        Map.of("id", LOAD_MOVIE_ONE_ID, "title", "Load Fixture 001", "normalizedTitle", "load fixture 001", "releaseYear", 2020),
                        Map.of("id", LOAD_MOVIE_TWO_ID, "title", "Load Fixture 002", "normalizedTitle", "load fixture 002", "releaseYear", 2021),
                        Map.of("id", LOAD_MOVIE_THREE_ID, "title", "Load Fixture 003", "normalizedTitle", "load fixture 003", "releaseYear", 2022))))
                .withConfig(QueryConfig.builder().withDatabase(database).withTimeout(Duration.ofSeconds(10)).build())
                .execute();
    }
}
