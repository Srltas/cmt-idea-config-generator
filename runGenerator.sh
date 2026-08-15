#!/bin/bash
#
# Generates the IntelliJ IDEA configuration for a CUBRID Migration Toolkit checkout.
#
#   ./runGenerator.sh [<cmt-project-dir>] [generator options...]
#
# The project directory is taken from the first argument, then $CMT_HOME, then a
# sibling folder that looks like a CMT checkout. Everything else is derived: the
# Eclipse bundles come from the Maven p2 cache, so build the CMT project once
# before running this.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MARKER="plugins/com.cubrid.cubridmigration.app"

CMT=""
if [ $# -gt 0 ] && [ "${1#-}" = "$1" ]; then
    # First argument does not start with '-', so it is the project directory.
    CMT="$1"
    shift
elif [ -n "$CMT_HOME" ]; then
    CMT="$CMT_HOME"
else
    for candidate in "$SCRIPT_DIR/../cubrid-migration" "$SCRIPT_DIR/../develop" "$SCRIPT_DIR/.."; do
        if [ -d "$candidate/$MARKER" ]; then
            CMT="$candidate"
            break
        fi
    done
fi

if [ -z "$CMT" ] || [ ! -d "$CMT/$MARKER" ]; then
    echo "CMT project not found${CMT:+ at $CMT}."
    echo "Usage: $0 [<cmt-project-dir>] [generator options...]"
    exit 1
fi

CMT="$(cd "$CMT" && pwd)"

echo "Building CMT IDEA Config Generator..."
mvn clean package -DskipTests -q -f "$SCRIPT_DIR/pom.xml"

echo "Running generator for $CMT ..."
java -jar "$SCRIPT_DIR/target/cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar" "$CMT" "$@"
