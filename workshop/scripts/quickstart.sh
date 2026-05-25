#!/usr/bin/env bash
# quickstart.sh
# One-command workshop bring-up. Runs verify-setup, builds the workshop image,
# starts the ZGC (Zulu) and G1 (Temurin) containers, waits for them to become
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

HOST_ZGC="${HOST_ZGC:-http://localhost:8080}"
HOST_G1="${HOST_G1:-http://localhost:9080}"
CONTEXT="/trader-stream-ee"
COMPOSE_FILE="docker-compose-workshop.yml"
SERVICE_ZGC="trader-stream-workshop-zgc"
SERVICE_G1="trader-stream-workshop-g1"
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

step "2/5 Building the workshop images"
echo "  ${DIM}Building ZGC (Zulu 25) and G1 (Temurin 25) images. First run pulls base layers and runs Maven; allow 5-10 minutes.${RESET}"
compose build
ok "workshop images ready"

step "3/5 Starting the workshop containers"
compose up -d
ok "containers started"

wait_healthy() {
    local label="$1" host="$2" service="$3"
    deadline=$((SECONDS + 240))
    grace=$((SECONDS + 90))
    local http_code=""
    while [ "$SECONDS" -lt "$deadline" ]; do
        http_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$host$CONTEXT/api/health/ready" 2>/dev/null || echo "000")
        if [ "$http_code" = "200" ]; then
            ok "$label /api/health/ready returned 200"
            break
        fi
        h=$(docker inspect --format='{{.State.Health.Status}}' "$service" 2>/dev/null || echo starting)
        if [ "$h" = "unhealthy" ] && [ "$SECONDS" -ge "$grace" ]; then
            fail "$label unhealthy after grace period. Logs: docker compose -f $COMPOSE_FILE logs $service"
        fi
        printf "  %-8s %-3s HTTP %s (docker %s)\n" "$label" "$(date +%H:%M:%S)" "$http_code" "$h"
        sleep 5
    done
    if [ "$http_code" != "200" ]; then
        fail "$label timed out waiting for healthy after 4 minutes."
    fi
}

step "4/5 Waiting for both instances to deploy (~60s start period)"
wait_healthy "ZGC" "$HOST_ZGC" "$SERVICE_ZGC"
wait_healthy "G1"  "$HOST_G1"  "$SERVICE_G1"

step "5/5 Running smoke test (SMOKE=$SMOKE)"

smoke_fast() {
    curl -fsS --max-time 5 "$HOST_ZGC$CONTEXT/api/jfr/status" | jq -e '.jfrAvailable == true' >/dev/null \
        || fail "JFR is not available on ZGC instance."
    ok "ZGC JFR REST endpoint responds; jfrAvailable=true"
    curl -fsS --max-time 5 "$HOST_G1$CONTEXT/api/jfr/status" | jq -e '.jfrAvailable == true' >/dev/null \
        || fail "JFR is not available on G1 instance."
    ok "G1 JFR REST endpoint responds; jfrAvailable=true"
}

smoke_medium() {
    local name="quickstart-$(date +%s)"
    local dump_dir="$ROOT_DIR/monitoring/recordings/workshop-zgc"
    curl -fsS --max-time 10 -X POST \
        "$HOST_ZGC$CONTEXT/api/jfr/recording/start?name=$name&durationSeconds=15&settings=tradestream-workshop" \
        | jq -e '.state == "RUNNING"' >/dev/null \
        || fail "Could not start a recording on ZGC."
    ok "ZGC recording '$name' started; sleeping 18s for it to dump"
    sleep 18
    local file
    file=$(ls -1t "$dump_dir/$name-"*.jfr 2>/dev/null | head -1 || true)
    [ -n "$file" ] && [ -s "$file" ] \
        || fail "Recording did not dump to $dump_dir."
    local events
    events=$(jfr summary "$file" 2>/dev/null | awk '/^ trade\.published/{print $2}')
    [ -n "$events" ] && [ "$events" -gt 0 ] \
        || fail "Recording exists but contains no trade.published events. Is the publisher running?"
    ok "ZGC recording $(basename "$file") has $events trade.published events"
}

smoke_full() {
    local name="quickstart-full-$(date +%s)"
    local dump_dir="$ROOT_DIR/monitoring/recordings/workshop-zgc"
    curl -fsS --max-time 10 -X POST \
        "$HOST_ZGC$CONTEXT/api/jfr/recording/start?name=$name&durationSeconds=65&settings=tradestream-workshop" \
        >/dev/null
    sleep 5
    curl -fsS --max-time 10 -X POST "$HOST_ZGC$CONTEXT/api/pressure/mode/EARNINGS_SPIKE" >/dev/null
    ok "EARNINGS_SPIKE running on ZGC; recording for 55s"
    sleep 55
    curl -fsS --max-time 10 -X POST "$HOST_ZGC$CONTEXT/api/pressure/mode/OFF" >/dev/null
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

  ZGC Dashboard:   ${HOST_ZGC}${CONTEXT}/
  G1 Dashboard:    ${HOST_G1}${CONTEXT}/

  ZGC Health:      ${HOST_ZGC}${CONTEXT}/api/health/check
  G1 Health:       ${HOST_G1}${CONTEXT}/api/health/check

  ZGC JFR status:  ${HOST_ZGC}${CONTEXT}/api/jfr/status
  G1 JFR status:   ${HOST_G1}${CONTEXT}/api/jfr/status

  ZGC recordings:  ./monitoring/recordings/workshop-zgc/
  G1 recordings:   ./monitoring/recordings/workshop-g1/

${DIM}Common commands:${RESET}
  Start a recording on ZGC (60 s):
    curl -X POST '${HOST_ZGC}${CONTEXT}/api/jfr/recording/start?name=demo&durationSeconds=60&settings=tradestream-workshop'

  Start a recording on G1 (60 s):
    curl -X POST '${HOST_G1}${CONTEXT}/api/jfr/recording/start?name=demo&durationSeconds=60&settings=tradestream-workshop'

  Run a stress scenario on both runtimes:
    curl -X POST '${HOST_ZGC}${CONTEXT}/api/pressure/mode/EARNINGS_SPIKE'
    curl -X POST '${HOST_G1}${CONTEXT}/api/pressure/mode/EARNINGS_SPIKE'
    sleep 60
    curl -X POST '${HOST_ZGC}${CONTEXT}/api/pressure/mode/OFF'
    curl -X POST '${HOST_G1}${CONTEXT}/api/pressure/mode/OFF'

  Tail the logs:
    docker compose -f ${COMPOSE_FILE} logs -f

  Stop the workshop:
    docker compose -f ${COMPOSE_FILE} down

BANNER
