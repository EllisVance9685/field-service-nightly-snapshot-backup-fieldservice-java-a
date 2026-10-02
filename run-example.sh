#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/field-service-snapshot-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/example/fieldservice/*.java
cp src/main/resources/application.properties "$BUILD_DIR/application.properties"
java -cp "$BUILD_DIR" example.fieldservice.FieldServiceSnapshotJob

