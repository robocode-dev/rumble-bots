#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
RUN_ROOT="${RUMBLE_NODE_CACHE:-${TMPDIR:-/tmp}/rumble-bots-node}"
mkdir -p "$RUN_ROOT"
RUN_DIR=$(mktemp -d "$RUN_ROOT/StarterBot.XXXXXX")
cp "$SCRIPT_DIR/package.json" "$SCRIPT_DIR/package-lock.json" "$SCRIPT_DIR/StarterBot.json" "$RUN_DIR/"
cp -R "$SCRIPT_DIR/src" "$RUN_DIR/src"
npm ci --prefix "$RUN_DIR" --ignore-scripts --no-audit --no-fund --omit=dev >/dev/null
exec node "$RUN_DIR/src/StarterBot.ts" "$@"
