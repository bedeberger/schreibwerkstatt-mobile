#!/usr/bin/env bash
# Stop-Hook: führt die JVM-Unit-Tests aus, sobald Kotlin-Quellen geändert wurden,
# und blockiert das Turn-Ende bei rotem Testlauf (Ausgabe geht zurück an das Modell).
# Läuft einmal pro Turn, nicht nach jedem einzelnen Edit (Robolectric ist zu langsam dafür).
set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

input=$(cat)

# Stop-Hook-Schleife vermeiden: haben wir diesen Turn schon einmal blockiert, fertig werden lassen.
printf '%s' "$input" | jq -e '.stop_hook_active == true' >/dev/null 2>&1 && exit 0

# Nur aktiv werden, wenn .kt-Dateien geändert sind (tracked, staged oder untracked).
git status --porcelain 2>/dev/null | grep -qE '\.kt$' || exit 0

out=$(./gradlew testDebugUnitTest -q 2>&1)
code=$?

if [ "$code" -eq 0 ]; then
  jq -nc '{systemMessage:"✓ testDebugUnitTest grün"}'
else
  printf '%s' "$out" | tail -40 | jq -Rsc \
    '{decision:"block", reason:("testDebugUnitTest FAILED — vor dem Beenden fixen:\n" + .)}'
fi
exit 0
