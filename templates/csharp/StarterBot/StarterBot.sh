#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE_ROOT="${RUMBLE_DOTNET_CACHE:-${TMPDIR:-/tmp}/rumble-bots-dotnet}"
mkdir -p "$CACHE_ROOT"
ARTIFACTS=$(mktemp -d "$CACHE_ROOT/StarterBot.XXXXXX")
exec dotnet run --project "$SCRIPT_DIR/StarterBot.csproj" --artifacts-path "$ARTIFACTS" -- "$@"
