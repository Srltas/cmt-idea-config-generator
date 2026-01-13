#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Building CMT IDEA Config Generator..."
mvn clean package -DskipTests -q -f "$SCRIPT_DIR/pom.xml"

JAR_FILE="$SCRIPT_DIR/target/cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar"

echo "Running generator..."
java -jar "$JAR_FILE" \
    -c "$SCRIPT_DIR/osgi-app.properties" \
    -p "$SCRIPT_DIR/../cubrid-migration" \
    -o "$SCRIPT_DIR/../" \
    "$@"