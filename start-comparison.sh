#!/bin/bash

set -e

ARGS=("$@")
MONITORING=false
INGESTION_MODE="AERON"

for arg in "${ARGS[@]}"; do
    case $arg in
        all)
            MONITORING=true
            ;;
        aeron|AERON)
            INGESTION_MODE="AERON"
            ;;
        direct|DIRECT)
            INGESTION_MODE="DIRECT"
            ;;
        --help|-h)
            echo "Usage: ./start-comparison.sh [all] [aeron|direct]"
            echo ""
            echo "  (no args)  - Start C4 and G1 clusters in AERON mode (apps only)"
            echo "  all        - Include full monitoring stack (Prometheus/Grafana/Loki)"
            echo "  aeron      - Use AERON ingestion mode (default, optimized)"
            echo "  direct     - Use DIRECT ingestion mode (GC stress test)"
            echo ""
            echo "Examples:"
            echo "  ./start-comparison.sh           # AERON mode, apps only"
            echo "  ./start-comparison.sh all       # AERON mode, with monitoring"
            echo "  ./start-comparison.sh direct    # DIRECT mode, apps only"
            echo "  ./start-comparison.sh all direct # DIRECT mode, with monitoring"
            echo ""
            exit 0
            ;;
    esac
done

export TRADER_INGESTION_MODE="$INGESTION_MODE"

echo "================================================ட்டான்"
echo "  TradeStreamEE - JVM Performance Comparison"
echo "  C4 vs G1GC Side-by-Side Demo"
echo "================================================ட்டான்"
echo ""
echo "Configuration:"
echo "  Ingestion Mode: $INGESTION_MODE"
echo "  Monitoring:     $([ "$MONITORING" = true ] && echo "enabled" || echo "disabled")"
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

# Create base monitoring directory structure for logs, GC logs, and JFR recordings
mkdir -p monitoring/logs/{c4-{1,2,3},g1-{1,2,3}}
mkdir -p monitoring/gc-logs/{c4-{1,2,3},g1-{1,2,3}}
mkdir -p monitoring/recordings/{c4-{1,2,3},g1-{1,2,3}}

if [ "$MONITORING" = true ]; then
    echo "Mode: Full deployment with monitoring stack"
    # Create full monitoring directory structure
    mkdir -p monitoring/{prometheus,grafana/{provisioning/{datasources,dashboards},dashboards},loki,promtail}
else
    echo "Mode: Application clusters only"
fi

# Download JMX Exporter if not present
if [ ! -f "monitoring/jmx-exporter/jmx_prometheus_javaagent-1.0.1.jar" ]; then
    echo "Downloading JMX Prometheus Exporter 1.0.1..."
    mkdir -p monitoring/jmx-exporter
    wget -q -O monitoring/jmx-exporter/jmx_prometheus_javaagent-1.0.1.jar \
        https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/1.0.1/jmx_prometheus_javaagent-1.0.1.jar
    echo "✓ JMX Exporter downloaded"
fi

# Create networks
echo "Creating Docker networks..."
docker network create trader-network 2>/dev/null || echo "Network trader-network already exists"
docker network create monitoring 2>/dev/null || echo "Network monitoring already exists"

echo "✓ Networks ready"

# Build Docker images
echo "Building Docker images..."
docker build -t trader-stream-ee:c4 -f Dockerfile.scale .
docker build -t trader-stream-ee:g1 -f Dockerfile.scale.standard .
echo "✓ Images built"

if [ "$MONITORING" = true ]; then
    # Start monitoring stack
    echo "Starting monitoring stack (Prometheus, Grafana, Loki)..."
    run_compose -f docker-compose-monitoring.yml up -d
    echo "✓ Monitoring stack started"

    # Wait for monitoring to be ready
    echo "Waiting for monitoring stack to initialize..."
    sleep 10
fi

# Start C4 cluster
echo "Starting Azul C4 cluster (ports 8080-8083)..."
run_compose -f docker-compose-c4.yml up -d
echo "✓ C4 cluster started"

# Start G1 cluster
echo "Starting G1GC cluster (ports 9080-9083)..."
run_compose -f docker-compose-g1.yml up -d
echo "✓ G1 cluster started"

# Wait for clusters to be ready
echo "Waiting for clusters to initialize..."
sleep 10

# Start Traefik metrics scraper
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRAPER_PID_FILE="$SCRIPT_DIR/monitoring/.scraper.pid"
if [ -f "$SCRAPER_PID_FILE" ]; then
    kill "$(cat "$SCRAPER_PID_FILE")" 2>/dev/null
    rm -f "$SCRAPER_PID_FILE"
fi
echo "Starting Traefik metrics scraper..."
"$SCRIPT_DIR/monitoring/scrape-traefik.sh" &
SCRAPER_PID=$!
echo "$SCRAPER_PID" > "$SCRAPER_PID_FILE"
echo "✓ Metrics scraper started (PID $SCRAPER_PID)"

# Check cluster status
echo ""
echo "Checking cluster status..."
echo ""
echo "📊 C4 Cluster Status:"
curl -s http://localhost:8080/trader-stream-ee/api/status/cluster 2>/dev/null | jq . || echo "  Cluster info not available yet"
echo ""
echo "📊 G1 Cluster Status:"
curl -s http://localhost:9080/trader-stream-ee/api/status/cluster 2>/dev/null | jq . || echo "  Cluster info not available yet"

echo ""
echo "================================================ட்டான்"
echo "  Deployment Complete!"
echo "================================================ட்டான்"
echo ""
echo "Application Endpoints:"
echo "  C4 Cluster:     http://localhost:8080/trader-stream-ee/"
echo "  G1 Cluster:     http://localhost:9080/trader-stream-ee/"
echo ""

if [ "$MONITORING" = true ]; then
    echo "Monitoring:"
    echo "  Prometheus:     http://localhost:9090"
    echo "  Grafana:        http://localhost:3000 (admin/admin)"
    echo "  Loki:           http://localhost:3100"
    echo ""
fi

echo "Load Balancers:"
echo "  Traefik C4:     http://localhost:8084"
echo "  Traefik G1:     http://localhost:9084"
echo ""
echo "Ingestion Mode: $INGESTION_MODE"
echo ""
echo "To apply stress test:"
echo "  curl -X POST http://localhost:8080/trader-stream-ee/api/pressure/mode/EXTREME"
echo "  curl -X POST http://localhost:9080/trader-stream-ee/api/pressure/mode/EXTREME"
echo ""
echo "To stop everything:"
echo "  ./stop-comparison.sh"
echo ""
