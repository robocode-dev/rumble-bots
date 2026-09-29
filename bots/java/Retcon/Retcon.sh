#!/usr/bin/env sh
# Boots Retcon from source (Java 22+ multi-file source launch), following the rumble-bots catalog convention:
# only the official Bot API jar is fetched, into a cache outside the bot directory.
set -eu
# Older launchers compile only the entry file and fail with misleading "package does not exist" errors.
JAVA_SPEC=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')
JAVA_MAJOR=${JAVA_SPEC%%.*}
case "$JAVA_MAJOR" in ''|*[!0-9]*) JAVA_MAJOR=0 ;; esac
if [ "$JAVA_MAJOR" -lt 22 ]; then
  echo "Retcon needs Java 22 or newer as 'java' on PATH (multi-file source launch), found java.specification.version=${JAVA_SPEC:-none}" >&2
  exit 1
fi
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BOT_API_VERSION=1.3.1
CACHE_DIR="${RUMBLE_JAVA_CACHE:-$HOME/.cache/rumble-bots-java}"
JAR="$CACHE_DIR/robocode-tankroyale-bot-api-$BOT_API_VERSION.jar"
if [ ! -f "$JAR" ]; then
  mkdir -p "$CACHE_DIR"
  TMP="$JAR.$$.tmp"
  curl -fsSL -o "$TMP" "https://repo1.maven.org/maven2/dev/robocode/tankroyale/robocode-tankroyale-bot-api/$BOT_API_VERSION/robocode-tankroyale-bot-api-$BOT_API_VERSION.jar"
  mv "$TMP" "$JAR"
fi
exec java -cp "$JAR" "$SCRIPT_DIR/src/fnl/bots/retcon/Retcon.java" "$@"
