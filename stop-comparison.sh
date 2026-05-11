#!/bin/bash

echo "Stopping JVM comparison demo..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRAPER_PID_FILE="$SCRIPT_DIR/monitoring/.scraper.pid"
if [ -f "$SCRAPER_PID_FILE" ]; then
    kill "$(cat "$SCRAPER_PID_FILE")" 2>/dev/null
    rm -f "$SCRAPER_PID_FILE"
    echo "Metrics scraper stopped."
fi

docker compose -f docker-compose-g1.yml down
docker compose -f docker-compose-c4.yml down
docker compose -f docker-compose-monitoring.yml down

echo "All services stopped."
echo ""
echo "To preserve data, volumes were not removed."
echo "To remove all data: docker volume prune"
