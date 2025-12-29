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
run_compose() {
    if docker compose version &> /dev/null; then
        docker compose "$@"
    else
        docker-compose "$@"
    fi
}

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
        MODE=AERON run_compose -f docker-compose.yml up -d --build --force-recreate
        echo ""
        echo "🌐 Access: http://localhost:8080/trader-stream-ee/"
        ;;

    azul-direct)
        echo "🚀 [Azul Prime] Starting with DIRECT Architecture (Legacy Mode)..."
        echo "   > Dockerfile (Azul) + MODE=DIRECT"
        echo "   ℹ️  Observe how C4 handles high-allocation legacy code."
        echo ""
        MODE=DIRECT run_compose -f docker-compose.yml up -d --build --force-recreate
        echo ""
        echo "🌐 Access: http://localhost:8080/trader-stream-ee/"
        ;;

    # --- Standard OpenJDK (G1 GC) Scenarios ---

    standard|standard-direct)
        echo "🚀 [Standard JDK] Starting with DIRECT Architecture (Legacy Mode)..."
        echo "   > Dockerfile.standard (Temurin) + MODE=DIRECT"
        echo "   ℹ️  Baseline performance: High allocation on G1GC."
        echo ""
        MODE=DIRECT run_compose -f docker-compose-standard.yml up -d --build --force-recreate
        echo ""
        echo "🌐 Access: http://localhost:8080/trader-stream-ee/"
        ;;

    standard-aeron)
        echo "🚀 [Standard JDK] Starting with AERON Architecture (Optimized)..."
        echo "   > Dockerfile.standard (Temurin) + MODE=AERON"
        echo "   ℹ️  Observe if off-heap transport helps G1GC."
        echo ""
        MODE=AERON run_compose -f docker-compose-standard.yml up -d --build --force-recreate
        echo ""
        echo "🌐 Access: http://localhost:8080/trader-stream-ee/"
        ;;

    # --- Clustered Scenarios ---

    cluster|cluster-azul)
        echo "🚀 [Azul Prime Cluster] Starting 3-instance cluster with AERON..."
        echo "   > Dockerfile.scale (Azul) + MODE=AERON + Traefik LB"
        echo "   ℹ️  Demonstrates horizontal scalability with Hazelcast clustering."
        echo ""
        MODE=AERON DOCKERFILE=Dockerfile.scale run_compose -f docker-compose-scale.yml up -d --build --force-recreate
        echo ""
        echo "✅ Cluster started. Waiting for instances to be ready..."
        sleep 10
        echo ""
        echo "📊 Cluster Status:"
        curl -s http://localhost:8080/trader-stream-ee/api/status/cluster 2>/dev/null | jq . || echo "Cluster info endpoint not available yet"
        echo ""
        echo "🌐 Access: http://localhost:8080/trader-stream-ee/"
        ;;

    cluster-standard)
        echo "🚀 [Standard JDK Cluster] Starting 3-instance cluster with AERON..."
        echo "   > Dockerfile.scale.standard (Temurin) + MODE=AERON + Traefik LB"
        echo "   ℹ️  Compare cluster performance with G1GC."
        echo ""
        MODE=AERON DOCKERFILE=Dockerfile.scale.standard run_compose -f docker-compose-scale.yml up -d --build --force-recreate
        echo ""
        echo "✅ Cluster started. Waiting for instances to be ready..."
        sleep 10
        echo ""
        echo "📊 Cluster Status:"
        curl -s http://localhost:8080/trader-stream-ee/api/status/cluster 2>/dev/null | jq . || echo "Cluster info endpoint not available yet"
        echo ""
        echo "🌐 Access: http://localhost:8080/trader-stream-ee/"
        ;;

    cluster-direct)
        echo "🚀 [Azul Prime Cluster] Starting 3-instance cluster with DIRECT mode..."
        echo "   > Dockerfile.scale (Azul) + MODE=DIRECT + Traefik LB"
        echo "   ℹ️  Test cluster with high-allocation legacy mode."
        echo ""
        MODE=DIRECT DOCKERFILE=Dockerfile.scale run_compose -f docker-compose-scale.yml up -d --build --force-recreate
        echo ""
        echo "🌐 Access: http://localhost:8080/trader-stream-ee/"
        ;;

    cluster-dynamic)
        echo "🚀 [Dynamic Cluster] Starting scalable cluster..."
        if [ -z "$2" ]; then
            INSTANCES=3
            echo "   > No instance count specified, defaulting to 3"
        else
            INSTANCES=$2
        fi
        echo "   > Dockerfile.scale (Azul) + MODE=AERON + $INSTANCES scalable instances"
        echo "   ℹ️  Uses generic service for true dynamic scaling."
        echo ""
        MODE=AERON DOCKERFILE=Dockerfile.scale run_compose -f docker-compose-scale.yml build trader-stream
        run_compose -f docker-compose-scale.yml up -d traefik
        run_compose -f docker-compose-scale.yml up -d --scale trader-stream=$INSTANCES --no-recreate trader-stream
        echo ""
        echo "✅ Cluster started. Waiting for instances to be ready..."
        sleep 15
        echo ""
        echo "📊 Cluster Status:"
        curl -s http://localhost:8080/trader-stream-ee/api/status/cluster 2>/dev/null | jq . || echo "Cluster info endpoint not available yet"
        echo ""
        echo "🌐 Access: http://localhost:8080/trader-stream-ee/"
        ;;

    scale)
        echo "🚀 Scaling cluster..."
        if [ -z "$2" ]; then
            echo "Usage: ./start.sh scale <number-of-instances>"
            echo "Example: ./start.sh scale 5"
            exit 1
        fi
        INSTANCES=$2
        echo "   > Scaling to $INSTANCES instances (using generic trader-stream service)"
        echo "   > Note: This uses the scalable service, not the named instances (trader-stream-1/2/3)"
        run_compose -f docker-compose-scale.yml up -d --scale trader-stream=$INSTANCES --no-recreate
        echo ""
        echo "✅ Scaled to $INSTANCES instances"
        sleep 10
        echo ""
        echo "📊 Checking cluster status..."
        curl -s http://localhost:8080/trader-stream-ee/api/status/cluster 2>/dev/null | jq . || echo "Cluster info not available yet"
        ;;

    # --- Utilities ---

    down|stop)
        echo "🛑 Stopping TradeStreamEE..."
        run_compose -f docker-compose.yml down
        run_compose -f docker-compose-standard.yml down
        run_compose -f docker-compose-scale.yml down
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
        if docker ps | grep -q "trader-stream"; then
            if docker ps | grep -q "trader-stream-1"; then
                # Cluster mode - show all instances
                echo "Cluster mode detected - showing logs from all instances..."
                run_compose -f docker-compose-scale.yml logs -f
            else
                # Single instance mode
                docker logs -f trader-stream-ee
            fi
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
            echo ""
            # Check if cluster mode
            if curl -s http://localhost:8080/trader-stream-ee/api/status/cluster 2>/dev/null | grep -q "clustered"; then
                echo "📡 Cluster Status:"
                curl -s http://localhost:8080/trader-stream-ee/api/status/cluster 2>/dev/null | jq .
            fi
        else
            echo "❌ Application is not responding"
            echo "Try: ./start.sh logs"
        fi
        ;;

    clean)
        echo "🧹 Cleaning up..."
        run_compose -f docker-compose.yml down -v
        run_compose -f docker-compose-standard.yml down -v
        run_compose -f docker-compose-scale.yml down -v
        docker system prune -f
        echo "✅ Cleaned"
        ;;

    *)
        echo "Usage: ./start.sh [command]"
        echo ""
        echo "Single Instance Modes:"
        echo "  azul-aeron       - Azul Prime (C4) + Aeron (Optimized)  [Default]"
        echo "  azul-direct      - Azul Prime (C4) + Direct (Legacy)"
        echo "  standard-direct  - Standard JDK (G1) + Direct (Legacy)  [Baseline]"
        echo "  standard-aeron   - Standard JDK (G1) + Aeron (Optimized)"
        echo ""
        echo "Clustered Modes (Fixed 3 instances + Traefik LB):"
        echo "  cluster          - Azul Prime (C4) + Aeron + Hazelcast Cluster"
        echo "  cluster-azul     - Same as 'cluster'"
        echo "  cluster-standard - Standard JDK (G1) + Aeron + Hazelcast Cluster"
        echo "  cluster-direct   - Azul Prime (C4) + Direct + Hazelcast Cluster"
        echo ""
        echo "Dynamic Scaling (Generic service + Traefik LB):"
        echo "  cluster-dynamic [N] - Start N scalable instances (default: 3)"
        echo "  scale N             - Scale existing dynamic cluster to N instances"
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