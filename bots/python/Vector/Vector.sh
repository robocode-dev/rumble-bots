#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${RUMBLE_PYTHON:-}" ]; then
  exec "$RUMBLE_PYTHON" "$SCRIPT_DIR/src/Vector.py" "$@"
fi
if command -v python3 >/dev/null 2>&1; then
  exec python3 "$SCRIPT_DIR/src/Vector.py" "$@"
fi
exec python "$SCRIPT_DIR/src/Vector.py" "$@"
