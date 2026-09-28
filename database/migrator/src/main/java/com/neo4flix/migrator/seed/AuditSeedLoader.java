package com.neo4flix.migrator.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryConfig;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Loads the small, fixed graph used for audit and recommendation demonstrations. */
public final class AuditSeedLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FIXTURE_RESOURCE = "/database/seeds/audit/audit-fixture.json";

    private static final String MERGE_USER = """
            MERGE (user:User {id: $id})
            SET user.slug = $slug,
                user.displayName = $displayName,
                user.email = $email,
                user.normalizedEmail = $normalizedEmail,
                user.createdAt = datetime($createdAt),
                user.updatedAt = datetime($updatedAt)
            """;
    private static final String MERGE_MOVIE = """
            MERGE (movie:Movie {id: $id})
            SET movie.slug = $slug,
                movie.title = $title,
                movie.normalizedTitle = $normalizedTitle,
                movie.releaseYear = $releaseYear,
                movie.createdAt = datetime($createdAt),
                movie.updatedAt = datetime($updatedAt)
            """;
    private static final String MERGE_GENRE = """
            MERGE (genre:Genre {id: $id})
            SET genre.name = $name,
                genre.normalizedName = $normalizedName
            """;
    private static final String MERGE_MOVIE_GENRE = """
            MATCH (movie:Movie {id: $movieId})
            MATCH (genre:Genre {id: $genreId})
            MERGE (movie)-[:IN_GENRE]->(genre)
            """;
    private static final String MERGE_RATING = """
            MATCH (user:User {id: $userId})
            MATCH (movie:Movie {id: $movieId})
            MERGE (user)-[rated:RATED {key: $key}]->(movie)
            SET rated.score = $score,
                rated.createdAt = datetime($createdAt),
                rated.updatedAt = datetime($updatedAt)
            """;

    public SeedResult load(Driver driver, String database) {
        AuditFixture fixture = fixture();
        QueryConfig config = withDatabase(database);
        fixture.users().forEach(user -> execute(driver, MERGE_USER, user.parameters(fixture.timestamp()), config));
        fixture.movies().forEach(movie -> execute(driver, MERGE_MOVIE, movie.parameters(fixture.timestamp()), config));
        fixture.genres().forEach(genre -> execute(driver, MERGE_GENRE, genre.parameters(), config));
        fixture.movieGenres().forEach(movieGenre -> execute(driver, MERGE_MOVIE_GENRE, movieGenre.parameters(), config));
        fixture.ratings().forEach(rating -> execute(driver, MERGE_RATING, rating.parameters(fixture.timestamp()), config));
        return new SeedResult(
                fixture.users().stream().map(FixtureUser::id).toList(),
                fixture.movies().stream().map(FixtureMovie::id).toList(),
                fixture.ratings().stream().map(rating -> new AuditRating(rating.userId(), rating.movieId(), rating.score())).toList());
    }

    private static AuditFixture fixture() {
        try (InputStream input = AuditSeedLoader.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Audit seed fixture is missing: " + FIXTURE_RESOURCE);
            }
            return OBJECT_MAPPER.readValue(input, AuditFixture.class);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not read audit seed fixture", exception);
        }
    }

    private static QueryConfig withDatabase(String database) {
        return QueryConfig.builder()
                .withDatabase(database)
                .withTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static void execute(Driver driver, String query, Map<String, Object> parameters, QueryConfig config) {
        driver.executableQuery(query)
                .withParameters(parameters)
                .withConfig(config)
                .execute();
    }

    public record SeedResult(List<String> userIds, List<String> movieIds, List<AuditRating> ratings) {
    }

    public record AuditRating(String userId, String movieId, int score) {
    }

    private record AuditFixture(
            String timestamp,
            List<FixtureUser> users,
            List<FixtureMovie> movies,
            List<FixtureGenre> genres,
            List<FixtureMovieGenre> movieGenres,
            List<FixtureRating> ratings) {
    }

    private record FixtureUser(String id, String slug, String displayName, String email) {
        private Map<String, Object> parameters(String timestamp) {
            return Map.of(
                    "id", id,
                    "slug", slug,
                    "displayName", displayName,
                    "email", email,
                    "normalizedEmail", email.toLowerCase(),
                    "createdAt", timestamp,
                    "updatedAt", timestamp);
        }
    }

    private record FixtureMovie(String id, String slug, String title, String normalizedTitle, int releaseYear) {
        private Map<String, Object> parameters(String timestamp) {
            return Map.of(
                    "id", id,
                    "slug", slug,
                    "title", title,
                    "normalizedTitle", normalizedTitle,
                    "releaseYear", releaseYear,
                    "createdAt", timestamp,
                    "updatedAt", timestamp);
        }
    }

    private record FixtureGenre(String id, String name, String normalizedName) {
        private Map<String, Object> parameters() {
            return Map.of("id", id, "name", name, "normalizedName", normalizedName);
        }
    }

    private record FixtureMovieGenre(String movieId, String genreId) {
        private Map<String, Object> parameters() {
            return Map.of("movieId", movieId, "genreId", genreId);
        }
    }

    private record FixtureRating(String userId, String movieId, int score) {
        private Map<String, Object> parameters(String timestamp) {
            return Map.of(
                    "userId", userId,
                    "movieId", movieId,
                    "key", userId + ":" + movieId,
                    "score", score,
                    "createdAt", timestamp,
                    "updatedAt", timestamp);
        }
    }
}
