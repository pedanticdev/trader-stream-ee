#!/bin/bash

#############################################################################
# OOM Monitor for Payara Micro Containers
#
# This script monitors Payara Micro containers for Out of Memory errors
# and automatically restarts containers when OOM is detected.
#
# Usage:
#   ./monitor-oom.sh [interval_minutes] [container_prefix]
#
# Arguments:
#   interval_minutes - How often to check (default: 5 minutes)
#   container_prefix - Container name prefix to monitor (default: trader-stream-)
#
# Examples:
#   ./monitor-oom.sh                    # Check every 5 minutes
#   ./monitor-oom.sh 2                  # Check every 2 minutes
#   ./monitor-oom.sh 10 trader-stream-  # Check every 10 minutes for trader-stream-* containers
#
# To run in background:
#   ./monitor-oom.sh &
#   echo $! > monitor-oom.pid
#
# To stop:
#   kill $(cat monitor-oom.pid)
#
#############################################################################

set -euo pipefail

# Show help if requested
if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ] || [ "${1:-}" = "help" ]; then
    echo "================================================"
    echo "  Payara Micro OOM Monitor - Help"
    echo "================================================"
    echo ""
    echo "Usage:"
    echo "  ./monitor-oom.sh [interval_minutes] [container_prefix]"
    echo ""
    echo "Arguments:"
    echo "  interval_minutes - How often to check for OOM errors (default: 5 minutes)"
    echo "  container_prefix - Container name prefix to monitor (default: trader-stream-)"
    echo ""
    echo "Examples:"
    echo "  ./monitor-oom.sh                    # Check every 5 minutes for trader-stream-* containers"
    echo "  ./monitor-oom.sh 2                  # Check every 2 minutes"
    echo "  ./monitor-oom.sh 10 trader-stream-  # Check every 10 minutes for trader-stream-* containers"
    echo ""
    echo "Background Execution:"
    echo "  ./monitor-oom.sh &                  # Run in background"
    echo "  echo \$! > monitor-oom.pid           # Save process ID for later"
    echo "  kill \$(cat monitor-oom.pid)         # Stop the monitor"
    echo ""
    echo "Features:"
    echo "  - Monitors Docker container logs for OOM errors and WebSocket exceptions"
    echo "  - Automatically restarts containers when errors are detected"
    echo "  - Logs all activity to oom-monitor.log"
    echo "  - Automatic log rotation when file exceeds 10MB"
    echo "  - Waits for container health checks after restart"
    echo ""
    echo "Monitored Error Patterns:"
    echo "  - OutOfMemoryError (all variants)"
    echo "  - GC overhead limit exceeded"
    echo "  - WebSocket/Tyrus connection errors"
    echo "  - Broken pipe / Connection reset"
    echo ""
    echo "Signals:"
    echo "  Ctrl+C or SIGTERM - Gracefully stop monitoring"
    echo ""
    exit 0
fi

# Configuration
CHECK_INTERVAL_MINUTES=${1:-5}
CONTAINER_PREFIX=${2:-trader-stream-}
LOG_FILE="oom-monitor.log"
MAX_LOG_SIZE=10485760  # 10MB

# OOM patterns to search for in logs
OOM_PATTERNS=(
    "java.lang.OutOfMemoryError"
    "OutOfMemoryError"
    "Out of memory"
    "GC overhead limit exceeded"
    "Requested array size exceeds VM limit"
    "unable to create new native thread"
    "Metaspace"
    # Eclipse Tyrus WebSocket exceptions that indicate container issues
    "org.glassfish.tyrus"
    "TyrusWebSocketEngine"
    "Connection reset by peer"
    "Broken pipe"
    "UpgradeException"
    "WebSocket connection closed"
    "DeploymentException"
    "HandshakeException"
    "SessionException"
    "Unexpected error during WebSocket"
    "Failed to process WebSocket frame"
    "WebSocket frame buffer overflow"
)

# ANSI color codes
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "[$(date +'%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log_info() {
    log "${BLUE}[INFO]${NC} $1"
}

log_warn() {
    log "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    log "${RED}[ERROR]${NC} $1"
}

log_success() {
    log "${GREEN}[SUCCESS]${NC} $1"
}

# Rotate log file if too large
rotate_log_if_needed() {
    if [ -f "$LOG_FILE" ]; then
        local size=$(stat -f%z "$LOG_FILE" 2>/dev/null || stat -c%s "$LOG_FILE" 2>/dev/null || echo 0)
        if [ "$size" -gt "$MAX_LOG_SIZE" ]; then
            mv "$LOG_FILE" "${LOG_FILE}.old"
            log_info "Rotated log file (size: $size bytes)"
        fi
    fi
}

# Check if Docker is available
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed or not in PATH"
        exit 1
    fi

    if ! docker ps &> /dev/null; then
        log_error "Cannot connect to Docker daemon. Is Docker running?"
        exit 1
    fi
}

# Get list of containers matching prefix
get_containers() {
    docker ps --filter "name=${CONTAINER_PREFIX}" --format "{{.Names}}" 2>/dev/null || true
}

# Check if container has OOM errors in logs
check_container_oom() {
    local container=$1
    local found_oom=false
    local oom_pattern=""

    # Get logs from last check interval (plus buffer)
    local since_time="${CHECK_INTERVAL_MINUTES}m"
    local logs=$(docker logs --since "$since_time" "$container" 2>&1 || true)

    if [ -z "$logs" ]; then
        return 1
    fi

    # Check each OOM pattern
    for pattern in "${OOM_PATTERNS[@]}"; do
        if echo "$logs" | grep -q "$pattern"; then
            found_oom=true
            oom_pattern="$pattern"
            break
        fi
    done

    if [ "$found_oom" = true ]; then
        log_error "OOM detected in container '$container' - Pattern: '$oom_pattern'"

        # Extract and log the actual OOM error lines
        local oom_lines=$(echo "$logs" | grep -A 3 "$oom_pattern" | head -10)
        echo "$oom_lines" | while IFS= read -r line; do
            log "  | $line"
        done

        return 0
    fi

    return 1
}

# Restart container
restart_container() {
    local container=$1

    log_warn "Restarting container '$container' due to OOM..."

    if docker restart "$container" &> /dev/null; then
        log_success "Container '$container' restarted successfully"

        # Wait for container to be healthy
        local max_wait=60
        local waited=0
        while [ $waited -lt $max_wait ]; do
            local health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "none")

            if [ "$health" = "healthy" ]; then
                log_success "Container '$container' is healthy after restart"
                return 0
            elif [ "$health" = "none" ]; then
                # No healthcheck defined, just check if running
                if docker ps --filter "name=$container" --filter "status=running" | grep -q "$container"; then
                    log_info "Container '$container' is running (no healthcheck defined)"
                    return 0
                fi
            fi

            sleep 2
            waited=$((waited + 2))
        done

        log_warn "Container '$container' restarted but health check timed out after ${max_wait}s"
        return 0
    else
        log_error "Failed to restart container '$container'"
        return 1
    fi
}

# Main monitoring loop
monitor() {
    log_info "Starting OOM monitor for containers matching '$CONTAINER_PREFIX*'"
    log_info "Check interval: ${CHECK_INTERVAL_MINUTES} minutes"
    log_info "Log file: $LOG_FILE"
    log_info "Press Ctrl+C to stop"
    echo ""

    local iteration=0

    while true; do
        iteration=$((iteration + 1))
        rotate_log_if_needed

        log_info "Check #${iteration} - Scanning for OOM errors..."

        local containers=$(get_containers)

        if [ -z "$containers" ]; then
            log_warn "No containers found matching prefix '$CONTAINER_PREFIX'"
        else
            local container_count=$(echo "$containers" | wc -l | tr -d ' ')
            log_info "Found $container_count container(s) to monitor"

            local oom_detected=false
            local restart_count=0

            # Check each container
            while IFS= read -r container; do
                if [ -n "$container" ]; then
                    if check_container_oom "$container"; then
                        oom_detected=true
                        if restart_container "$container"; then
                            restart_count=$((restart_count + 1))
                        fi
                    fi
                fi
            done <<< "$containers"

            if [ "$oom_detected" = false ]; then
                log_success "No OOM errors detected in any container"
            else
                log_warn "Total containers restarted: $restart_count"
            fi
        fi

        log_info "Next check in ${CHECK_INTERVAL_MINUTES} minutes..."
        echo ""

        # Sleep for specified interval
        sleep $((CHECK_INTERVAL_MINUTES * 60))
    done
}

# Signal handler for graceful shutdown
cleanup() {
    echo ""
    log_info "Received shutdown signal. Exiting..."
    exit 0
}

trap cleanup SIGINT SIGTERM

# Main entry point
main() {
    echo "================================================"
    echo "  Payara Micro OOM Monitor"
    echo "================================================"
    echo ""

    # Validate interval - show help if invalid
    if ! [[ "$CHECK_INTERVAL_MINUTES" =~ ^[0-9]+$ ]] || [ "$CHECK_INTERVAL_MINUTES" -lt 1 ]; then
        echo "Error: Invalid parameter '$CHECK_INTERVAL_MINUTES'"
        echo "Check interval must be a positive integer (minutes)"
        echo ""
        echo "For help, run: $0 --help"
        echo ""
        echo "Usage: $0 [interval_minutes] [container_prefix]"
        echo "Example: $0 5 trader-stream-"
        exit 1
    fi

    check_docker
    monitor
}

# Run main function
main
