#!/usr/bin/env bash
# jfr-query.sh
# Thin wrapper around the `jfr` CLI for the analyses attendees do most often.
#
# Usage:
#   jfr-query.sh summary <file>            Print recording metadata + event counts
#   jfr-query.sh gc-pauses <file>          Print every jdk.GarbageCollection event
#   jfr-query.sh gc-top <file> [N]         Top N longest GC pauses (default 10)
#   jfr-query.sh allocation <file> [N]     Top N allocation hot stacks (sampled)
#   jfr-query.sh custom <file> [event]     List custom event counts; one event optional
#   jfr-query.sh compare <fileA> <fileB>   Side-by-side GC summary of two recordings

set -euo pipefail

cmd="${1:-}"
shift || true

require_jfr() {
    command -v jfr >/dev/null 2>&1 || {
        echo "[FAIL] 'jfr' CLI not found. Install any JDK 21+ (Temurin, Azul, etc.) and ensure jfr is on PATH." >&2
        exit 1
    }
}

require_file() {
    local f="${1:-}"
    if [ -z "$f" ] || [ ! -f "$f" ]; then
        echo "[FAIL] recording file not found: $f" >&2
        exit 2
    fi
}

case "$cmd" in
    summary)
        require_jfr
        require_file "${1:-}"
        jfr summary "$1"
        ;;

    gc-pauses)
        require_jfr
        require_file "${1:-}"
        jfr print --events 'jdk.GarbageCollection,jdk.GCPhasePause,jdk.GCPhasePauseLevel1' "$1"
        ;;

    gc-top)
        require_jfr
        require_file "${1:-}"
        top="${2:-10}"
        jfr print --events jdk.GCPhasePause --json "$1" \
            | awk -F'"duration"[[:space:]]*:[[:space:]]*"PT' '
                /"duration"/{
                    split($2, a, "S\"")
                    dur=a[1]+0
                    print dur "s"
                }' \
            | sort -nr | head -"$top"
        echo ""
        echo "(top $top pauses; durations in seconds)"
        ;;

    allocation)
        require_jfr
        require_file "${1:-}"
        top="${2:-15}"
        echo "=== Top allocation sites by sample count ==="
        jfr print --events jdk.ObjectAllocationSample --stack-depth 1 "$1" 2>/dev/null \
            | awk '/objectClass = /{gsub(/^[ \t]*objectClass = /, ""); print}' \
            | sort | uniq -c | sort -nr | head -"$top"
        ;;

    custom)
        require_jfr
        require_file "${1:-}"
        if [ -n "${2:-}" ]; then
            jfr print --events "$2" "$1"
        else
            echo "=== Custom application event counts ==="
            for ev in trade.published quote.published marketdepth.published \
                      message.batch.processed websocket.broadcast sbe.encode sbe.decode \
                      gc.sla.violation aeron.backpressure burst.mode.activated \
                      order.submitted order.matched stop.triggered order.canceled \
                      burst.pattern.detected; do
                count=$(jfr print --events "$ev" "$1" 2>/dev/null | grep -c "^$ev" || true)
                printf "  %-30s %s\n" "$ev" "$count"
            done
        fi
        ;;

    compare)
        require_jfr
        require_file "${1:-}"
        require_file "${2:-}"
        printf "%-30s %-20s %-20s\n" "Metric" "$(basename "$1")" "$(basename "$2")"
        printf "%-30s %-20s %-20s\n" "------" "------" "------"
        for ev in jdk.GarbageCollection jdk.EvacuationFailed jdk.PromotionFailed \
                  jdk.PromoteObjectOutsidePLAB jdk.G1HeapRegionTypeChange \
                  gc.sla.violation aeron.backpressure trade.published; do
            a=$(jfr print --events "$ev" "$1" 2>/dev/null | grep -c "^$ev" || true)
            b=$(jfr print --events "$ev" "$2" 2>/dev/null | grep -c "^$ev" || true)
            printf "%-30s %-20s %-20s\n" "$ev" "$a" "$b"
        done
        ;;

    ""|-h|--help|help)
        sed -n '2,11p' "$0"
        ;;

    *)
        echo "[FAIL] Unknown subcommand: $cmd" >&2
        sed -n '2,11p' "$0" >&2
        exit 2
        ;;
esac
