package com.neo4flix.migrator.seed;

import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryConfig;

import java.time.Duration;
import java.util.Map;

/** Loads a small, credential-free graph for local UI development. */
public final class DemoSeedLoader {

    private static final String GENRE_ID = "44444444-4444-4444-4444-444444444444";
    private static final String MOVIE_ID = "55555555-5555-5555-5555-555555555555";
    private static final String MERGE_GENRE = """
            MERGE (genre:Genre {id: $id})
            SET genre.name = $name,
                genre.normalizedName = $normalizedName
            """;
    private static final String MERGE_MOVIE = """
            MERGE (movie:Movie {id: $id})
            SET movie.title = $title,
                movie.normalizedTitle = $normalizedTitle,
                movie.releaseYear = $releaseYear
            WITH movie
            MATCH (genre:Genre {id: $genreId})
            MERGE (movie)-[:IN_GENRE]->(genre)
            """;

    public void load(Driver driver, String database) {
        QueryConfig config = queryConfig(database);
        driver.executableQuery(MERGE_GENRE)
                .withParameters(Map.of("id", GENRE_ID, "name", "Science Fiction", "normalizedName", "science fiction"))
                .withConfig(config)
                .execute();
        driver.executableQuery(MERGE_MOVIE)
                .withParameters(Map.of(
                        "id", MOVIE_ID, "title", "Orbit Station", "normalizedTitle", "orbit station",
                        "releaseYear", 2026, "genreId", GENRE_ID))
                .withConfig(config)
                .execute();
    }

    private static QueryConfig queryConfig(String database) {
        return QueryConfig.builder().withDatabase(database).withTimeout(Duration.ofSeconds(10)).build();
    }
}
