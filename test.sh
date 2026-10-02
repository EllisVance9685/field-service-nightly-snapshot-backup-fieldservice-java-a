#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/field-service-snapshot-test-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/example/fieldservice/*.java src/test/java/example/fieldservice/*.java
java -cp "$BUILD_DIR" example.fieldservice.NightlySnapshotDecisionTest

