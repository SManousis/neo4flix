#!/bin/sh
# Compatibility wrapper for operators that used the Batch 0 script directly.
# GDS verification, migrations, and schema verification now live in the Java tool.
set -eu

MIGRATOR_JAR=${MIGRATOR_JAR:-/app/app.jar}
if [ ! -f "$MIGRATOR_JAR" ]; then
    printf '%s\n' "Database migrator JAR not found: $MIGRATOR_JAR" >&2
    exit 66
fi

exec java -jar "$MIGRATOR_JAR" "${1:-migrate}"
