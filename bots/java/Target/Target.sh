#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BOT_API_VERSION=1.1.0
CACHE_DIR="${RUMBLE_JAVA_CACHE:-$HOME/.cache/rumble-bots-java}"
JAR="$CACHE_DIR/robocode-tankroyale-bot-api-$BOT_API_VERSION.jar"
if [ ! -f "$JAR" ]; then
  mkdir -p "$CACHE_DIR"
  TMP="$JAR.$$.tmp"
  curl -fsSL -o "$TMP" "https://repo1.maven.org/maven2/dev/robocode/tankroyale/robocode-tankroyale-bot-api/$BOT_API_VERSION/robocode-tankroyale-bot-api-$BOT_API_VERSION.jar"
  mv "$TMP" "$JAR"
fi
exec java -cp "$JAR" "$SCRIPT_DIR/src/Target.java" "$@"
