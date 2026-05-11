#!/usr/bin/env bash
OUT="/data/home/seeraj/development/repos/payara/payara/trader-stream-ee/monitoring/metrics.log"
echo "=== Scraping traefik-c4 at http://localhost:8084/metrics/prometheus every 2s ===" > "$OUT"
while true; do
  echo "--- $(date -Iseconds) ---" >> "$OUT"
  curl -sf http://localhost:8084/metrics/prometheus >> "$OUT" 2>&1
  echo "" >> "$OUT"
  sleep 2
done
