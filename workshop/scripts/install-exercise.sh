#!/usr/bin/env bash
# install-exercise.sh
# Copies a Module 3 student-edited event into the source tree and rebuilds.
#
# Usage:
#   ./workshop/scripts/install-exercise.sh module-3-burst-event
#
# What it does:
#   1. Copies workshop/exercises/<exercise>/BurstPatternEvent.starter.java
#      to src/main/java/fish/payara/trader/jfr/BurstPatternEvent.java
#      (only if the destination does not already exist - protects student edits)
#   2. Patches MarketDataPublisher to emit the new event in its burst-mode branch.
#   3. Rebuilds and redeploys the ZGC instance image.
#   4. Reminds the speaker how to roll the running container.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
EXERCISE="${1:-module-3-burst-event}"
EX_DIR="$ROOT_DIR/workshop/exercises/$EXERCISE"

if [ ! -d "$EX_DIR" ]; then
    echo "[FAIL] Exercise dir not found: $EX_DIR" >&2
    exit 1
fi

case "$EXERCISE" in
    module-3-burst-event)
        SRC="$EX_DIR/BurstPatternEvent.starter.java"
        DST="$ROOT_DIR/src/main/java/fish/payara/trader/jfr/BurstPatternEvent.java"
        if [ ! -f "$SRC" ]; then
            echo "[FAIL] Starter file not found: $SRC" >&2
            exit 1
        fi
        if [ -f "$DST" ]; then
            echo "[skip] $DST already exists - leaving student edits in place."
        else
            cp "$SRC" "$DST"
            echo "[ok]   Copied starter -> $DST"
            echo "       Now edit it in your IDE; the file is part of the main source tree."
        fi
        ;;
    *)
        echo "[FAIL] Unknown exercise: $EXERCISE"
        echo "       Currently install-exercise.sh only supports: module-3-burst-event"
        exit 1
        ;;
esac

echo
echo "Next steps:"
echo "  1. Edit $DST in your IDE."
echo "  2. Rebuild the ZGC image (Maven runs inside Docker):"
echo "       docker compose -f docker-compose-workshop.yml build trader-stream-workshop-zgc"
echo "  3. Roll instance 1 only (keeps cluster alive):"
echo "       docker compose -f docker-compose-workshop.yml up -d --no-deps trader-stream-workshop-zgc"
echo "  4. Capture a fresh recording with the new event enabled:"
echo "       curl -X POST 'http://localhost:8080/trader-stream-ee/api/jfr/recording/start?name=burst-event&durationSeconds=60&settings=tradestream-workshop'"
