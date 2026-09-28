package com.neo4flix.migrator.seed;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.MigrationsConfig;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AuditSeedLoaderIT {

    private static final String USERNAME = "neo4j";
    private static final String PASSWORD = "test-password";
    private static final String DATABASE = "neo4j";

    @Test
    void loadsTheFixedAuditGraphIdempotently() {
        try (Neo4jContainer<?> container = new Neo4jContainer<>(
                DockerImageName.parse("neo4j:2026.07.1-community"))
                .withAdminPassword(PASSWORD)) {
            container.start();
            try (Driver driver = GraphDatabase.driver(
                    container.getBoltUrl(), AuthTokens.basic(USERNAME, PASSWORD))) {
                applyMigrations(driver);

                new AuditSeedLoader().load(driver, DATABASE);

                assertThat(count(driver, "MATCH (:User) RETURN count(*) AS count"))
                        .isEqualTo(6L);
                assertThat(count(driver, "MATCH (:Movie) RETURN count(*) AS count"))
                        .isEqualTo(8L);
                assertThat(count(driver, "MATCH (:Genre) RETURN count(*) AS count"))
                        .isEqualTo(4L);
                assertThat(count(driver, "MATCH ()-[inGenre:IN_GENRE]->() RETURN count(inGenre) AS count"))
                        .isEqualTo(11L);
                assertThat(score(driver, "audit-alice", "audit-matrix"))
                        .isEqualTo(5L);

                new AuditSeedLoader().load(driver, DATABASE);

                assertThat(count(driver, "MATCH ()-[rated:RATED]->() RETURN count(rated) AS count"))
                        .isEqualTo(14L);
                assertThat(count(driver, "MATCH ()-[inGenre:IN_GENRE]->() RETURN count(inGenre) AS count"))
                        .isEqualTo(11L);
                assertThat(count(driver, """
                                MATCH (user:User)-[rated:RATED]->(movie:Movie)
                                WHERE rated.key <> user.id + ':' + movie.id
                                RETURN count(rated) AS count
                                """))
                        .isZero();
            }
        }
    }

    @Test
    void loadsDemoAndLoadScaffoldingWithUuidApplicationIdentifiers() {
        try (Neo4jContainer<?> container = new Neo4jContainer<>(
                DockerImageName.parse("neo4j:2026.07.1-community"))
                .withAdminPassword(PASSWORD)) {
            container.start();
            try (Driver driver = GraphDatabase.driver(
                    container.getBoltUrl(), AuthTokens.basic(USERNAME, PASSWORD))) {
                applyMigrations(driver);
                new DemoSeedLoader().load(driver, DATABASE);
                new LoadSeedLoader().load(driver, DATABASE);

                List<String> ids = driver.executableQuery("""
                                MATCH (node)
                                WHERE node:Genre OR node:Movie
                                RETURN node.id AS id
                                """)
                        .execute()
                        .records()
                        .stream()
                        .map(record -> record.get("id").asString())
                        .toList();

                assertThat(ids).hasSize(5);
                assertThat(ids).allSatisfy(id ->
                        assertThatCode(() -> UUID.fromString(id)).doesNotThrowAnyException());
            }
        }
    }

    private static void applyMigrations(Driver driver) {
        new Migrations(
                MigrationsConfig.builder()
                        .withDatabase(DATABASE)
                        .withLocationsToScan("classpath:database/migrations")
                        .withTransactionTimeout(Duration.ofSeconds(30))
                        .build(),
                driver)
                .apply();
    }

    private static long count(Driver driver, String query) {
        return driver.executableQuery(query)
                .execute()
                .records()
                .getFirst()
                .get("count")
                .asLong();
    }

    private static long score(Driver driver, String userSlug, String movieSlug) {
        return driver.executableQuery("""
                        MATCH (:User {slug: $userSlug})-[rated:RATED]->(:Movie {slug: $movieSlug})
                        RETURN rated.score AS score
                        """)
                .withParameters(Map.of("userSlug", userSlug, "movieSlug", movieSlug))
                .execute()
                .records()
                .getFirst()
                .get("score")
                .asLong();
    }
}
