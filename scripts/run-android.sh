#!/usr/bin/env bash
# Build, install und starte die App auf einem Gerät/Emulator.
#
# Aufruf:
#   scripts/run-android.sh                 # Auto: nimmt das einzige Gerät, sonst den Emulator
#   scripts/run-android.sh emulator-5554   # gezielt dieses Gerät (Serial)
#   ANDROID_SERIAL=... scripts/run-android.sh
set -euo pipefail

source "$(dirname "$0")/_sdk-env.sh"

APP_ID="ch.schreibwerkstatt.mobile"
ACTIVITY="${APP_ID}/${APP_ID}.MainActivity"

cd "$REPO_ROOT"

# Online-Geräte (Status == "device") als Liste ausgeben.
list_devices() {
  "$ADB" devices | awk '/\tdevice$/ {print $1}'
}

# Aus einer Geräteliste ein Ziel wählen: Emulator bevorzugen.
pick_device() {
  local devices=("$@") emu=""
  for d in "${devices[@]}"; do
    case "$d" in emulator-*) emu="$d"; break;; esac
  done
  if [ -n "$emu" ]; then echo "$emu"; else echo "${devices[0]}"; fi
}

# Wunschgerät via Argument oder ENV?
TARGET="${1:-${ANDROID_SERIAL:-}}"

# Online-Geräte einsammeln (portabel, ohne mapfile)
DEVICES=()
while IFS= read -r d; do [ -n "$d" ] && DEVICES+=("$d"); done < <(list_devices)

# Kein Gerät -> Emulator starten und neu einsammeln
if [ "${#DEVICES[@]}" -eq 0 ] && [ -z "$TARGET" ]; then
  echo "Kein Gerät/Emulator verbunden — starte Emulator ..."
  "$(dirname "$0")/start-emulator.sh"
  DEVICES=()
  while IFS= read -r d; do [ -n "$d" ] && DEVICES+=("$d"); done < <(list_devices)
fi

if [ -n "$TARGET" ]; then
  # Vorgegebenes Ziel muss online sein
  if ! printf '%s\n' "${DEVICES[@]}" | grep -qx "$TARGET"; then
    echo "Gerät '$TARGET' nicht gefunden/online. Verfügbar:" >&2
    printf '  %s\n' "${DEVICES[@]:-（keine）}" >&2
    exit 1
  fi
  SERIAL="$TARGET"
elif [ "${#DEVICES[@]}" -eq 0 ]; then
  echo "Weiterhin kein Gerät verfügbar. Prüfe 'adb devices'." >&2
  exit 1
elif [ "${#DEVICES[@]}" -eq 1 ]; then
  SERIAL="${DEVICES[0]}"
else
  SERIAL="$(pick_device "${DEVICES[@]}")"
  echo "Mehrere Geräte gefunden (${DEVICES[*]}) — wähle: $SERIAL"
  echo "  (Override: scripts/run-android.sh <serial>  oder  ANDROID_SERIAL=<serial> ...)"
fi

# Gradle und adb auf das gewählte Gerät festnageln
export ANDROID_SERIAL="$SERIAL"
echo "==> Ziel: $ANDROID_SERIAL"

echo "==> Build + Install (installDebug)"
./gradlew installDebug

echo "==> Starte ${ACTIVITY}"
"$ADB" -s "$SERIAL" shell am start -n "${ACTIVITY}"

echo "==> Logcat (Ctrl+C zum Beenden)"
# Kurz auf den App-Prozess warten (Activity-Start ist asynchron)
PID=""
for _ in 1 2 3 4 5 6 7 8 9 10; do
  PID="$("$ADB" -s "$SERIAL" shell pidof -s "${APP_ID}" 2>/dev/null | tr -d '\r')"
  [ -n "$PID" ] && break
  sleep 0.5
done
if [ -n "$PID" ]; then
  "$ADB" -s "$SERIAL" logcat --pid="$PID"
else
  echo "(PID nicht ermittelt — zeige ungefiltertes Logcat)"
  "$ADB" -s "$SERIAL" logcat
fi
