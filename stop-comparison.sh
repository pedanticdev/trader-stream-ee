#!/bin/bash

echo "Stopping JVM comparison demo..."

docker compose -f docker-compose-g1.yml down
docker compose -f docker-compose-c4.yml down
docker compose -f docker-compose-monitoring.yml down

echo "All services stopped."
echo ""
echo "To preserve data, volumes were not removed."
echo "To remove all data: docker volume prune"
