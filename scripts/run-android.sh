#!/usr/bin/env bash
# Build, install und starte die App auf dem verbundenen Gerät/Emulator.
# Aufruf:  scripts/run-android.sh
set -euo pipefail

source "$(dirname "$0")/_sdk-env.sh"

APP_ID="ch.schreibwerkstatt.mobile"
ACTIVITY="${APP_ID}/${APP_ID}.MainActivity"

cd "$REPO_ROOT"

# Gerät vorhanden? Sonst Emulator starten.
if ! "$ADB" devices | grep -qE '\b(device)$'; then
  echo "Kein Gerät/Emulator verbunden — versuche Emulator zu starten ..."
  "$(dirname "$0")/start-emulator.sh"
fi

echo "==> Build + Install (installDebug)"
./gradlew installDebug

echo "==> Starte ${ACTIVITY}"
"$ADB" shell am start -n "${ACTIVITY}"

echo "==> Logcat (Ctrl+C zum Beenden)"
"$ADB" logcat --pid="$("$ADB" shell pidof -s "${APP_ID}" | tr -d '\r')"
