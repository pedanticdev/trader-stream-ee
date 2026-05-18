#!/usr/bin/env bash
# quickstart.sh
# One-command workshop bring-up. Runs verify-setup, builds the workshop image,
# starts the single-instance Azul Prime container, waits for it to become
# healthy, then runs a smoke recording to prove the JFR pipeline end-to-end.
#
# Usage:
#   ./workshop/scripts/quickstart.sh           # default smoke test (medium, ~20s)
#   SMOKE=fast   ./workshop/scripts/quickstart.sh  # status check only (~3s)
#   SMOKE=full   ./workshop/scripts/quickstart.sh  # 60s recording + scenario (~90s)
#   SKIP_VERIFY=1 ./workshop/scripts/quickstart.sh # skip the prerequisite check

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT_DIR"

HOST="${HOST:-http://localhost:8080}"
CONTEXT="/trader-stream-ee"
COMPOSE_FILE="docker-compose-workshop.yml"
SERVICE="trader-stream-workshop"
SMOKE="${SMOKE:-medium}"

if [ -t 1 ]; then
    BLUE=$'\033[1;34m'; GREEN=$'\033[1;32m'; YELLOW=$'\033[1;33m'; RED=$'\033[1;31m'; DIM=$'\033[2m'; RESET=$'\033[0m'
else
    BLUE=''; GREEN=''; YELLOW=''; RED=''; DIM=''; RESET=''
fi

step() { printf "\n%s[%s]%s %s\n" "$BLUE" "$(date +%H:%M:%S)" "$RESET" "$*"; }
ok()   { printf "  %s[ OK ]%s %s\n" "$GREEN" "$RESET" "$*"; }
warn() { printf "  %s[WARN]%s %s\n" "$YELLOW" "$RESET" "$*"; }
fail() { printf "  %s[FAIL]%s %s\n" "$RED" "$RESET" "$*" >&2; exit 1; }

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

if [ -z "${SKIP_VERIFY:-}" ]; then
    step "1/5 Sanity-checking host setup"
    if ! "$SCRIPT_DIR/verify-setup.sh"; then
        fail "verify-setup.sh reported errors. Fix them, or re-run with SKIP_VERIFY=1 to bypass."
    fi
else
    step "1/5 Skipping prerequisite check (SKIP_VERIFY=1)"
fi

step "2/5 Building the workshop image"
echo "  ${DIM}First run pulls the Azul Prime base layer and runs Maven inside Docker; allow 5-10 minutes.${RESET}"
compose build
ok "image trader-stream-ee:workshop is ready"

step "3/5 Starting the workshop container"
compose up -d
ok "container started"

step "4/5 Waiting for Payara Micro to report healthy (start period ~60s)"
deadline=$((SECONDS + 180))
h="starting"
while [ "$SECONDS" -lt "$deadline" ]; do
    h=$(docker inspect --format='{{.State.Health.Status}}' "$SERVICE" 2>/dev/null || echo starting)
    case "$h" in
        healthy)    ok "container is healthy"; break ;;
        unhealthy)  fail "container reported unhealthy. Logs: docker compose -f $COMPOSE_FILE logs $SERVICE" ;;
        *)          printf "  %s. " "$h"; sleep 5 ;;
    esac
done
if [ "$h" != "healthy" ]; then
    fail "Timed out waiting for healthy after 3 minutes."
fi

step "5/5 Running smoke test (SMOKE=$SMOKE)"

smoke_fast() {
    curl -fsS --max-time 5 "$HOST$CONTEXT/api/jfr/status" | jq -e '.jfrAvailable == true' >/dev/null \
        || fail "JFR is not available inside the container."
    ok "JFR REST endpoint responds; jfrAvailable=true"
}

smoke_medium() {
    local name="quickstart-$(date +%s)"
    local dump_dir="$ROOT_DIR/monitoring/recordings/workshop"
    curl -fsS --max-time 10 -X POST \
        "$HOST$CONTEXT/api/jfr/recording/start?name=$name&durationSeconds=15&settings=tradestream-workshop" \
        | jq -e '.state == "RUNNING"' >/dev/null \
        || fail "Could not start a recording."
    ok "recording '$name' started; sleeping 18s for it to dump"
    sleep 18
    local file
    file=$(ls -1t "$dump_dir/$name-"*.jfr 2>/dev/null | head -1 || true)
    [ -n "$file" ] && [ -s "$file" ] \
        || fail "Recording did not dump to $dump_dir."
    local events
    events=$(jfr summary "$file" 2>/dev/null | awk '/^ trade\.published/{print $2}')
    [ -n "$events" ] && [ "$events" -gt 0 ] \
        || fail "Recording exists but contains no trade.published events. Is the publisher running?"
    ok "recording $(basename "$file") has $events trade.published events"
}

smoke_full() {
    local name="quickstart-full-$(date +%s)"
    local dump_dir="$ROOT_DIR/monitoring/recordings/workshop"
    curl -fsS --max-time 10 -X POST \
        "$HOST$CONTEXT/api/jfr/recording/start?name=$name&durationSeconds=65&settings=tradestream-workshop" \
        >/dev/null
    sleep 5
    curl -fsS --max-time 10 -X POST "$HOST$CONTEXT/api/pressure/mode/PROMOTION_STORM" >/dev/null
    ok "PROMOTION_STORM running; recording for 55s"
    sleep 55
    curl -fsS --max-time 10 -X POST "$HOST$CONTEXT/api/pressure/mode/OFF" >/dev/null
    sleep 10
    local file
    file=$(ls -1t "$dump_dir/$name-"*.jfr 2>/dev/null | head -1 || true)
    [ -n "$file" ] && [ -s "$file" ] \
        || fail "Full smoke recording did not dump."
    ok "recording $(basename "$file") dumped ($(du -h "$file" | cut -f1))"
}

case "$SMOKE" in
    fast)    smoke_fast ;;
    medium)  smoke_medium ;;
    full)    smoke_full ;;
    *) fail "Unknown SMOKE level: $SMOKE (expected: fast | medium | full)" ;;
esac

cat <<BANNER

${GREEN}Workshop is ready.${RESET}

  Dashboard:      ${HOST}${CONTEXT}/
  Health check:   ${HOST}${CONTEXT}/api/health/check
  JFR status:     ${HOST}${CONTEXT}/api/jfr/status
  Recordings dir: ./monitoring/recordings/workshop/

${DIM}Common commands:${RESET}
  Start a recording (60 s):
    curl -X POST '${HOST}${CONTEXT}/api/jfr/recording/start?name=demo&durationSeconds=60&settings=tradestream-workshop'

  Run a GC stress scenario:
    curl -X POST '${HOST}${CONTEXT}/api/pressure/mode/PROMOTION_STORM'
    sleep 60
    curl -X POST '${HOST}${CONTEXT}/api/pressure/mode/OFF'

  Inspect a recording:
    ./workshop/scripts/jfr-query.sh summary monitoring/recordings/workshop/<file>.jfr

  Tail the logs:
    docker compose -f ${COMPOSE_FILE} logs -f ${SERVICE}

  Stop the workshop:
    docker compose -f ${COMPOSE_FILE} down

BANNER
