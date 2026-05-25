#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"

mkdir -p "$APP_CLASSES"

echo "Compilando middleware..."
(
  cd "$MIDDLEWARE_DIR"
  mvn -Dmaven.compiler.release=23 -Dmaven.compiler.source=23 -Dmaven.compiler.target=23 clean install
)

echo "Montando classpath do projeto..."
(
  cd "$APP_DIR"
  mvn -q -Dmaven.compiler.release=23 -Dmaven.compiler.source=23 -Dmaven.compiler.target=23 \
    dependency:build-classpath -Dmdep.outputFile="$APP_CLASSPATH_FILE"
)

SOURCE_LIST="$APP_TARGET/sources.txt"
: > "$SOURCE_LIST"
printf '%s\n' "$APP_DIR/GatewayTCP.java" "$APP_DIR/InstanceInfo.java" >> "$SOURCE_LIST"
find "$APP_DIR/message" -name '*.java' -print >> "$SOURCE_LIST"
find "$APP_DIR/src/main/java" -name '*.java' -print >> "$SOURCE_LIST"

echo "Compilando gateway e aplicacao..."
javac --release 23 -cp "$(get_app_classpath)" -d "$APP_CLASSES" @"$SOURCE_LIST"

echo "Build concluido."
