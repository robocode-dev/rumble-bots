#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "${RUMBLE_PYTHON:-python3}" "$SCRIPT_DIR/src/StarterBot.py" "$@"
