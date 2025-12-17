#!/bin/bash

# Quick stop script

if docker compose version &> /dev/null; then
    docker compose down
else
    docker-compose down
fi

echo "✅ TradeStreamEE stopped"
