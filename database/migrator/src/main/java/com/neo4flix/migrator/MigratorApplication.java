package com.neo4flix.migrator;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.MigrationsConfig;
import com.neo4flix.migrator.seed.AuditSeedLoader;
import com.neo4flix.migrator.seed.DemoSeedLoader;
import com.neo4flix.migrator.seed.LoadSeedLoader;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class MigratorApplication implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MigratorApplication.class);
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_DATABASE = "neo4j";
    private static final String DEFAULT_LOCATION = "classpath:database/migrations";
    private static final List<String> EXPECTED_VERSIONS = List.of("001", "002", "003", "004", "005", "006");
    private static final List<SchemaValidator.SchemaObject> EXPECTED_CONSTRAINTS = List.of(
            nodeUniqueness("user_id_unique", "User", "id"),
            nodeUniqueness("user_normalized_email_unique", "User", "normalizedEmail"),
            nodeUniqueness("movie_id_unique", "Movie", "id"),
            nodeUniqueness("genre_id_unique", "Genre", "id"),
            nodeUniqueness("genre_normalized_name_unique", "Genre", "normalizedName"),
            relationshipUniqueness("rated_key_unique", "RATED", "key"),
            relationshipUniqueness("watchlisted_key_unique", "WATCHLISTED", "key"),
            nodeUniqueness("auth_session_id_unique", "AuthSession", "id"),
            nodeUniqueness("auth_challenge_id_unique", "AuthChallenge", "id"),
            nodeUniqueness("auth_session_refresh_hash_unique", "AuthSession", "refreshTokenHash"),
            nodeUniqueness("auth_challenge_token_hash_unique", "AuthChallenge", "tokenHash"),
            nodeUniqueness("recommendation_share_id_unique", "RecommendationShare", "id"),
            nodeUniqueness(
                    "recommendation_share_token_hash_unique", "RecommendationShare", "publicTokenHash"));
    private static final List<SchemaValidator.SchemaObject> EXPECTED_INDEXES = List.of(
            rangeIndex("movie_title_index", "Movie", "normalizedTitle"),
            rangeIndex("movie_release_year_index", "Movie", "releaseYear"),
            rangeIndex("genre_name_index", "Genre", "name"));

    public static void main(String[] args) {
        SpringApplication.run(MigratorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        MigrationCommand command = MigrationCommand.parse(args);

        String uri = requiredEnvironment("NEO4J_URI");
        String username = requiredEnvironment("NEO4J_USERNAME");
        String password = requiredEnvironment("NEO4J_PASSWORD");
        String database = environmentOrDefault("NEO4J_DATABASE", DEFAULT_DATABASE);
        String location = environmentOrDefault("NEO4J_MIGRATIONS_LOCATION", DEFAULT_LOCATION);

        LOG.info("Running database migrator mode={} database={} location={}", command.mode(), database, location);

        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password))) {
            verifyGds(driver, database);

            Migrations migrations = new Migrations(
                    MigrationsConfig.builder()
                            .withDatabase(database)
                            .withLocationsToScan(location)
                            .withTransactionTimeout(Duration.ofSeconds(30))
                            .build(),
                    driver);

            if (command.mode().equals("migrate")) {
                migrations.apply();
            }

            verifyVersions(migrations);
            verifySchema(driver, database);
            runSeed(command.mode(), driver, database);
            LOG.info("Database migrator completed mode={} database={} versions={}",
                    command.mode(), database, EXPECTED_VERSIONS.size());
        }
    }

    private static void runSeed(String mode, Driver driver, String database) {
        switch (mode) {
            case "seed-demo" -> new DemoSeedLoader().load(driver, database);
            case "seed-audit" -> new AuditSeedLoader().load(driver, database);
            case "seed-load" -> new LoadSeedLoader().load(driver, database);
            default -> {
                // migrate and verify must not load seed data.
            }
        }
    }

    private static void verifyGds(Driver driver, String database) {
        var result = driver.executableQuery("RETURN gds.version() AS version")
                .withConfig(queryConfig(database))
                .execute();
        if (result.records().size() != 1 || result.records().getFirst().get("version").isNull()) {
            throw new IllegalStateException("GDS verification returned no version");
        }
        LOG.info("GDS verification succeeded for database={} version={}",
                database, result.records().getFirst().get("version").asString());
    }

    private static void verifyVersions(Migrations migrations) {
        var chain = migrations.info();
        List<String> missing = EXPECTED_VERSIONS.stream()
                .filter(version -> !chain.isApplied(version))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing migration versions: " + missing);
        }
    }

    private static void verifySchema(Driver driver, String database) {
        driver.executableQuery("CALL db.awaitIndexes(30)")
                .withConfig(queryConfig(database, Duration.ofSeconds(35)))
                .execute();

        List<SchemaValidator.SchemaObject> constraints = driver.executableQuery(
                        "SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties "
                                + "WHERE name IN $names "
                                + "RETURN name, type, entityType, labelsOrTypes, properties")
                .withParameters(Map.of("names", expectedNames(EXPECTED_CONSTRAINTS)))
                .withConfig(queryConfig(database))
                .execute()
                .records()
                .stream()
                .map(MigratorApplication::schemaObject)
                .toList();
        List<SchemaValidator.IndexMetadata> indexes = driver.executableQuery(
                        "SHOW INDEXES YIELD name, type, entityType, labelsOrTypes, properties, state "
                                + "WHERE name IN $names "
                                + "RETURN name, type, entityType, labelsOrTypes, properties, state")
                .withParameters(Map.of("names", expectedNames(EXPECTED_INDEXES)))
                .withConfig(queryConfig(database))
                .execute()
                .records()
                .stream()
                .map(record -> new SchemaValidator.IndexMetadata(
                        schemaObject(record), record.get("state").asString()))
                .toList();

        SchemaValidator.verifyConstraints(EXPECTED_CONSTRAINTS, constraints);
        SchemaValidator.verifyIndexes(EXPECTED_INDEXES, indexes);
    }

    private static List<String> expectedNames(List<SchemaValidator.SchemaObject> expected) {
        return expected.stream().map(SchemaValidator.SchemaObject::name).toList();
    }

    private static SchemaValidator.SchemaObject schemaObject(Record record) {
        return new SchemaValidator.SchemaObject(
                record.get("name").asString(),
                record.get("type").asString(),
                record.get("entityType").asString(),
                record.get("labelsOrTypes").asList(value -> value.asString()),
                record.get("properties").asList(value -> value.asString()));
    }

    private static QueryConfig queryConfig(String database) {
        return queryConfig(database, QUERY_TIMEOUT);
    }

    private static QueryConfig queryConfig(String database, Duration timeout) {
        return QueryConfig.builder()
                .withDatabase(database)
                .withTimeout(timeout)
                .build();
    }

    private static SchemaValidator.SchemaObject nodeUniqueness(
            String name, String label, String property) {
        return new SchemaValidator.SchemaObject(
                name, "NODE_PROPERTY_UNIQUENESS", "NODE", List.of(label), List.of(property));
    }

    private static SchemaValidator.SchemaObject relationshipUniqueness(
            String name, String relationshipType, String property) {
        return new SchemaValidator.SchemaObject(
                name,
                "RELATIONSHIP_PROPERTY_UNIQUENESS",
                "RELATIONSHIP",
                List.of(relationshipType),
                List.of(property));
    }

    private static SchemaValidator.SchemaObject rangeIndex(
            String name, String label, String property) {
        return new SchemaValidator.SchemaObject(
                name, "RANGE", "NODE", List.of(label), List.of(property));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
