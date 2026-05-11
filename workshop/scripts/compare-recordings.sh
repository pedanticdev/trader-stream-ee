#!/usr/bin/env bash
# compare-recordings.sh
# CLI-only side-by-side summary of two JFR recordings.
# Useful for Module 4 when projecting JMC is impractical.
#
# Usage:
#   ./workshop/scripts/compare-recordings.sh \
#       workshop/recordings/c4-promotion-storm.jfr \
#       workshop/recordings/g1-promotion-storm.jfr

set -euo pipefail

C4_REC="${1:-}"
G1_REC="${2:-}"

if [ -z "$C4_REC" ] || [ -z "$G1_REC" ]; then
    echo "Usage: $0 <c4-recording.jfr> <g1-recording.jfr>"
    exit 2
fi

command -v jfr >/dev/null 2>&1 || {
    echo "[FAIL] jfr CLI not found. Install a JDK 21+ (Temurin, Azul, etc.) and ensure jfr is on PATH."
    exit 1
}

count_gc() {
    jfr print --events jdk.GarbageCollection "$1" 2>/dev/null | grep -c '^jdk.GarbageCollection' || true
}

max_pause_ms() {
    jfr print --events jdk.GCPhasePause "$1" 2>/dev/null \
        | awk -F'duration = | s' '
            /duration = /{ seen=1; v=$2*1000; if(v>max) max=v }
            END { if(seen) printf "%.2f\n", max; else print "N/A (no pauses recorded)" }'
}

sum_pause_ms() {
    jfr print --events jdk.GCPhasePause "$1" 2>/dev/null \
        | awk -F'duration = | s' '
            /duration = /{ seen=1; sum+=$2*1000 }
            END { if(seen) printf "%.2f\n", sum; else print "N/A (no pauses recorded)" }'
}

count_event() {
    jfr print --events "$2" "$1" 2>/dev/null | grep -c "^$2" || true
}

summary() {
    local rec="$1" label="$2"
    echo
    echo "== $label =="
    echo "File:                  $rec"
    echo "Size:                  $(du -h "$rec" | cut -f1)"
    echo "GarbageCollection #:   $(count_gc "$rec")"
    echo "GCPhasePause max ms:   $(max_pause_ms "$rec")"
    echo "GCPhasePause sum ms:   $(sum_pause_ms "$rec")"
    echo "EvacuationFailed #:    $(count_event "$rec" jdk.EvacuationFailed)"
    echo "PromotionFailed #:     $(count_event "$rec" jdk.PromotionFailed)"
    echo "VirtualThreadPinned #: $(count_event "$rec" jdk.VirtualThreadPinned)"
    echo "gc.sla.violation #:    $(count_event "$rec" gc.sla.violation)"
    echo "aeron.backpressure #:  $(count_event "$rec" aeron.backpressure)"
    echo "trade.published #:     $(count_event "$rec" trade.published)"
}

summary "$C4_REC" "C4"
summary "$G1_REC" "G1"

echo
echo "Tip: open both in JMC side-by-side for full visual analysis:"
echo "  jmc -open \"$C4_REC\" -open \"$G1_REC\""
