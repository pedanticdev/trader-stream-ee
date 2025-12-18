#!/bin/bash

# TradeStreamEE Quick Start Script

set -e

echo "================================================"
echo "  TradeStreamEE - Performance Matrix"
echo "================================================"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed"
    echo "Please install Docker Desktop from: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Check if Docker Compose is available
if ! docker compose version &> /dev/null && ! docker-compose --version &> /dev/null; then
    echo "❌ Docker Compose is not installed"
    echo "Please install Docker Compose from: https://docs.docker.com/compose/install/"
    exit 1
fi

# Use docker compose (v2) or docker-compose (v1)
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

echo "✅ Docker is installed"
echo "✅ Docker Compose is installed"
echo ""

# Parse command line arguments
ACTION="${1:-up}"

case "$ACTION" in
    # --- Azul Platform Prime (C4 GC) Scenarios ---
    
    up|start|azul-aeron)
        echo "🚀 [Azul Prime] Starting with AERON Architecture (Optimized)..."
        echo "   > Dockerfile (Azul) + MODE=AERON"
        echo ""
        MODE=AERON $DOCKER_COMPOSE -f docker-compose.yml up -d --build --force-recreate
        ;;

    azul-direct)
        echo "🚀 [Azul Prime] Starting with DIRECT Architecture (Legacy Mode)..."
        echo "   > Dockerfile (Azul) + MODE=DIRECT"
        echo "   ℹ️  Observe how C4 handles high-allocation legacy code."
        echo ""
        MODE=DIRECT $DOCKER_COMPOSE -f docker-compose.yml up -d --build --force-recreate
        ;;

    # --- Standard OpenJDK (G1 GC) Scenarios ---

    standard|standard-direct)
        echo "🚀 [Standard JDK] Starting with DIRECT Architecture (Legacy Mode)..."
        echo "   > Dockerfile.standard (Temurin) + MODE=DIRECT"
        echo "   ℹ️  Baseline performance: High allocation on G1GC."
        echo ""
        MODE=DIRECT $DOCKER_COMPOSE -f docker-compose-standard.yml up -d --build --force-recreate
        ;;

    standard-aeron)
        echo "🚀 [Standard JDK] Starting with AERON Architecture (Optimized)..."
        echo "   > Dockerfile.standard (Temurin) + MODE=AERON"
        echo "   ℹ️  Observe if off-heap transport helps G1GC."
        echo ""
        MODE=AERON $DOCKER_COMPOSE -f docker-compose-standard.yml up -d --build --force-recreate
        ;;
    
    # --- Utilities ---

    down|stop)
        echo "🛑 Stopping TradeStreamEE..."
        $DOCKER_COMPOSE -f docker-compose.yml down
        $DOCKER_COMPOSE -f docker-compose-standard.yml down
        echo "✅ Stopped"
        ;;
    
    restart)
        echo "🔄 Restarting..."
        ./start.sh stop
        ./start.sh start
        ;;
    
    logs)
        echo "📋 Showing logs..."
        # Try to find which container is running
        if docker ps | grep -q "trader-stream-ee"; then
            docker logs -f trader-stream-ee
        else
            echo "❌ No running container found."
        fi
        ;;
    
    status)
        echo "📊 Checking status..."
        echo ""
        if curl -f http://localhost:8080/trader-stream-ee/api/status 2>/dev/null | jq .; then
            echo ""
            echo "✅ Application is running"
        else
            echo "❌ Application is not responding"
            echo "Try: ./start.sh logs"
        fi
        ;;
    
    clean)
        echo "🧹 Cleaning up..."
        $DOCKER_COMPOSE -f docker-compose.yml down -v
        $DOCKER_COMPOSE -f docker-compose-standard.yml down -v
        docker system prune -f
        echo "✅ Cleaned"
        ;;
    
    *)
        echo "Usage: ./start.sh [command]"
        echo ""
        echo "JVM Comparison Matrix:"
        echo "  azul-aeron       - Azul Prime (C4) + Aeron (Optimized)  [Default]"
        echo "  azul-direct      - Azul Prime (C4) + Direct (Legacy)"
        echo "  standard-direct  - Standard JDK (G1) + Direct (Legacy)  [Baseline]"
        echo "  standard-aeron   - Standard JDK (G1) + Aeron (Optimized)"
        echo ""
        echo "Utilities:"
        echo "  logs             - Show application logs"
        echo "  status           - Check if application is running"
        echo "  stop             - Stop the application"
        echo "  clean            - Stop and clean all containers/volumes"
        echo ""
        exit 1
        ;;
esac