#!/usr/bin/env bash
# Gemeinsamer Helper: ermittelt das Android-SDK und exportiert ADB / EMULATOR.
# Wird von den anderen Skripten via `source` eingebunden — nicht direkt aufrufen.
#
# SDK-Reihenfolge: local.properties (sdk.dir) -> $ANDROID_HOME -> $ANDROID_SDK_ROOT
#                  -> Standardpfad ~/Library/Android/sdk

# Repo-Root relativ zu diesem Skript
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

_sdk_from_props() {
  local props="${REPO_ROOT}/local.properties"
  [ -f "$props" ] || return 1
  # sdk.dir=... auslesen, Backslash-Escapes von Windows-Pfaden entfernen
  grep -E '^sdk\.dir=' "$props" | head -n1 | cut -d'=' -f2- | sed 's/\\\\/\//g; s/\\//g'
}

ANDROID_SDK="$(_sdk_from_props || true)"
[ -n "${ANDROID_SDK:-}" ] || ANDROID_SDK="${ANDROID_HOME:-}"
[ -n "${ANDROID_SDK:-}" ] || ANDROID_SDK="${ANDROID_SDK_ROOT:-}"
[ -n "${ANDROID_SDK:-}" ] || ANDROID_SDK="$HOME/Library/Android/sdk"

if [ ! -d "$ANDROID_SDK" ]; then
  echo "Android-SDK nicht gefunden (geprüft: local.properties, \$ANDROID_HOME, $ANDROID_SDK)." >&2
  exit 1
fi

ADB="$ANDROID_SDK/platform-tools/adb"
EMULATOR="$ANDROID_SDK/emulator/emulator"

# Falls Tools auf dem PATH erwartet werden
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$PATH"
export ANDROID_SDK ADB EMULATOR REPO_ROOT
