package com.neo4flix.migrator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationCommandTest {

    @Test
    void rejectsUnknownModeWithoutConnecting() {
        assertThatThrownBy(() -> MigrationCommand.parse(new String[] {"reset"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mode must be migrate, verify, seed-demo, seed-audit, or seed-load");
    }

    @Test
    void defaultsToMigrate() {
        assertThat(MigrationCommand.parse(new String[0]).mode()).isEqualTo("migrate");
    }
}
