#!/usr/bin/env bash
# Startet einen Android-Emulator und wartet, bis er bootfähig ist.
# Aufruf:  scripts/start-emulator.sh [AVD-Name]
# Ohne Argument wird das erste verfügbare AVD genommen.
set -euo pipefail

source "$(dirname "$0")/_sdk-env.sh"

# Läuft schon ein Emulator? Dann nichts tun.
if "$ADB" devices | grep -q 'emulator-.*device$'; then
  echo "Emulator läuft bereits."
  exit 0
fi

# AVDs einlesen (portabel, ohne mapfile — macOS-Bash 3.2-kompatibel)
AVDS=()
while IFS= read -r line; do
  [ -n "$line" ] && AVDS+=("$line")
done < <("$EMULATOR" -list-avds)

if [ "${#AVDS[@]}" -eq 0 ]; then
  cat >&2 <<'EOF'
Kein AVD (Emulator-Image) vorhanden.

Erstelle eines in Android Studio (Device Manager) oder per CLI, z.B.:
  sdkmanager "system-images;android-34;google_apis;arm64-v8a"
  avdmanager create avd -n pixel7 -k "system-images;android-34;google_apis;arm64-v8a" -d pixel_7

Danach erneut: scripts/start-emulator.sh
EOF
  exit 1
fi

AVD="${1:-${AVDS[0]}}"
echo "==> Starte Emulator: $AVD"
# Im Hintergrund starten, Ausgabe abkoppeln
"$EMULATOR" -avd "$AVD" >/dev/null 2>&1 &

echo "==> Warte auf Boot ..."
"$ADB" wait-for-device
# Warten bis sys.boot_completed == 1
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 2
done
echo "==> Emulator bereit."
