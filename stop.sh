#!/bin/bash

# Quick stop script

docker compose down 2>/dev/null || docker-compose down

echo "✅ TradeStreamEE stopped"
