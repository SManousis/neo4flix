package com.neo4flix.migrator;

import java.util.Set;

record MigrationCommand(String mode) {

    private static final Set<String> MODES = Set.of(
            "migrate", "verify", "seed-demo", "seed-audit", "seed-load");

    static MigrationCommand parse(String[] args) {
        String mode = args.length == 0 ? "migrate" : args[0];
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException(
                    "mode must be migrate, verify, seed-demo, seed-audit, or seed-load");
        }
        return new MigrationCommand(mode);
    }
}
