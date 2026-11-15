#!/usr/bin/env sh
# Adapted from robocode-dev/rumble-bots Python StarterBot (Apache-2.0).
# Modified: renamed the entry point to MizumoriBot; added attribution comments.
# See THIRD_PARTY_NOTICES.txt and LICENSE-APACHE-2.0.txt.
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "${RUMBLE_PYTHON:-python3}" "$SCRIPT_DIR/src/MizumoriBot.py" "$@"
