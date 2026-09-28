package com.neo4flix.platform.common.test;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Neo4jSchemaIntegrationTest {

    @Test
    void migratesAnEmptyDatabaseAndLeavesNoPendingMigrationsOnRerun() {
        try (Neo4jGdsContainer neo4j = Neo4jGdsContainer.start()) {
            var initialMigration = neo4j.runMigrator("migrate");
            assertThat(initialMigration.migrationHistorySize()).isPositive();

            assertThat(neo4j.runCypher(
                    "SHOW CONSTRAINTS YIELD name WHERE name IN $names RETURN name",
                    Map.of("names", java.util.List.of("user_id_unique", "rated_key_unique"))))
                    .extracting(record -> record.get("name").asString())
                    .containsExactlyInAnyOrder("user_id_unique", "rated_key_unique");

            var rerun = neo4j.runMigrator("migrate");
            assertThat(rerun.addedMigrationHistoryEntries()).isZero();
            assertThat(rerun.migrationHistorySize()).isEqualTo(initialMigration.migrationHistorySize());
            neo4j.runMigrator("verify");
        }
    }

    @Test
    void startsThePinnedGdsPlugin() {
        try (Neo4jGdsContainer neo4j = Neo4jGdsContainer.start()) {
            assertThat(neo4j.runCypher("RETURN gds.version() AS version", Map.of()))
                    .singleElement()
                    .extracting(record -> record.get("version").asString())
                    .matches(version -> version.matches("2026\\.07\\..+"));
        }
    }
}
