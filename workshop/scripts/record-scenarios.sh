#!/usr/bin/env bash
# record-scenarios.sh
# Generates the 10 pre-recorded JFR files used by the workshop.
#
# Prereq: workshop containers running and healthy.
#   ./workshop/scripts/quickstart.sh
#   (or: docker compose -f docker-compose-workshop.yml up -d)
#
# Output:
#   workshop/recordings/zgc-<scenario>.jfr  (5 files)
#   workshop/recordings/g1-<scenario>.jfr   (5 files)
#
# Typical invocation (uses all defaults below):
#   ./workshop/scripts/record-scenarios.sh
#
# Tune the timing only when you understand why. Each variable accepts an env
# var override (e.g. RECORD_SECS=60 ./workshop/scripts/record-scenarios.sh).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

OUT_DIR="$ROOT_DIR/workshop/recordings"
mkdir -p "$OUT_DIR"

# ---------------------------------------------------------------------------
# Configuration defaults
# ---------------------------------------------------------------------------
# Hosts target the workshop containers directly (single instance each).
ZGC_HOST="${ZGC_HOST:-http://localhost:8080}"
G1_HOST="${G1_HOST:-http://localhost:9080}"
CONTEXT="/trader-stream-ee"

# Per-scenario timing. The total recording window is WARMUP + RECORD seconds.
# Defaults are tuned for laptop hardware: short enough to fit 10 scenarios
# under 10 minutes wall-clock, long enough to capture multiple GC events per
# scenario at the workshop's allocation rates.
WARMUP_SECS="${WARMUP_SECS:-5}"     # JFR runs before the scenario starts; calibrates baseline allocation
SETTLE_SECS="${SETTLE_SECS:-3}"     # idle pause between scenarios so OFF mode actually quiesces the JVM
RECORD_SECS="${RECORD_SECS:-30}"    # active recording window with the scenario allocating

ZGC_RECORDINGS_DIR="$ROOT_DIR/monitoring/recordings/workshop-zgc"
G1_RECORDINGS_DIR="$ROOT_DIR/monitoring/recordings/workshop-g1"

SCENARIOS=(
    "STEADY_LOAD:baseline"
    "GROWING_HEAP:growing-heap"
    "PROMOTION_STORM:promotion-storm"
    "FRAGMENTATION:fragmentation"
    "CROSS_GEN_REFS:cross-gen-refs"
)

log()  { printf '\033[1;34m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"; }
fail() { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }

require_health() {
    local host="$1" label="$2"
    log "Checking $label health at $host"
    local resp
    resp=$(curl -s -o /dev/null -w '%{http_code}' "$host$CONTEXT/api/health/ready") || resp=000
    if [ "$resp" != "200" ]; then
        fail "$label not healthy (HTTP $resp). Start workshop: docker compose -f docker-compose-workshop.yml up -d"
    fi
}

record_one() {
    local host="$1" cluster_label="$2" scenario="$3" out_name="$4" target_dir="$5"

    log "[$cluster_label/$scenario] Resetting pressure mode"
    curl -fsS --max-time 15 --retry 3 --retry-all-errors --retry-delay 2 -X POST "$host$CONTEXT/api/pressure/mode/OFF" >/dev/null
    sleep "$SETTLE_SECS"

    log "[$cluster_label/$scenario] Starting JFR recording ($((WARMUP_SECS+RECORD_SECS))s window, workshop settings)"
    local start_resp
    start_resp=$(curl -fsS --max-time 15 --retry 3 --retry-all-errors --retry-delay 2 -X POST \
        "$host$CONTEXT/api/jfr/recording/start?name=$out_name&durationSeconds=$((WARMUP_SECS+RECORD_SECS))&settings=tradestream-workshop")
    local rec_id
    rec_id=$(echo "$start_resp" | jq -r '.recordingId // empty')
    if [ -z "$rec_id" ]; then
        fail "Failed to start recording: $start_resp"
    fi
    log "[$cluster_label/$scenario] Recording $rec_id started; warming up for ${WARMUP_SECS}s"
    sleep "$WARMUP_SECS"

    log "[$cluster_label/$scenario] Triggering scenario $scenario"
    curl -fsS --max-time 15 --retry 3 --retry-all-errors --retry-delay 2 -X POST "$host$CONTEXT/api/pressure/mode/$scenario" >/dev/null

    log "[$cluster_label/$scenario] Recording for ${RECORD_SECS}s"
    sleep "$RECORD_SECS"

    log "[$cluster_label/$scenario] Restoring OFF mode"
    curl -fsS --max-time 15 --retry 3 --retry-all-errors --retry-delay 2 -X POST "$host$CONTEXT/api/pressure/mode/OFF" >/dev/null
    sleep 3

    # The JfrRecordingResource auto-stops after durationSeconds; force stop to flush
    log "[$cluster_label/$scenario] Stopping recording $rec_id"
    curl -fsS --max-time 15 --retry 3 --retry-all-errors --retry-delay 2 -X POST "$host$CONTEXT/api/jfr/recording/stop?id=$rec_id" >/dev/null || true
    sleep 2

    local dumped
    dumped=$(ls -1t "$target_dir"/${out_name}-*.jfr 2>/dev/null | head -1)
    if [ -z "$dumped" ] || [ ! -s "$dumped" ]; then
        fail "Dumped recording not found in $target_dir (looking for ${out_name}-*.jfr)"
    fi

    local final="$OUT_DIR/${cluster_label}-${out_name}.jfr"
    cp "$dumped" "$final"
    local size_mb
    size_mb=$(du -m "$final" | awk '{print $1}')
    log "[$cluster_label/$scenario] -> $final (${size_mb} MB)"
    if [ "$size_mb" -gt 30 ]; then
        log "  WARN: recording exceeds 30 MB target. Consider reducing RECORD_SECS or disabling jdk.ObjectAllocationOutsideTLAB stacks."
    fi
}

main() {
    command -v jq >/dev/null 2>&1 || fail "jq is required"
    command -v curl >/dev/null 2>&1 || fail "curl is required"

    require_health "$ZGC_HOST" "ZGC"
    require_health "$G1_HOST" "G1"

    log "Output directory: $OUT_DIR"
    log "Scenarios: ${#SCENARIOS[@]}"

    for spec in "${SCENARIOS[@]}"; do
        local mode="${spec%%:*}"
        local name="${spec##*:}"
        record_one "$ZGC_HOST" "zgc" "$mode" "$name" "$ZGC_RECORDINGS_DIR"
        record_one "$G1_HOST" "g1" "$mode" "$name" "$G1_RECORDINGS_DIR"
    done

    log "Done. Recordings in $OUT_DIR:"
    ls -lh "$OUT_DIR"/*.jfr | awk '{ print "  " $9, "(" $5 ")" }'
}

main "$@"
