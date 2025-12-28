#!/bin/bash

set -e

MODE="${1:-}"

# Show help
if [ "$MODE" = "--help" ] || [ "$MODE" = "-h" ]; then
    echo "Usage: ./start-comparison.sh [all]"
    echo ""
    echo "  (no args)  - Start C4 and G1 application clusters only"
    echo "  all        - Start full stack including Prometheus/Grafana/Loki monitoring"
    echo "  --help     - Show this help message"
    echo ""
    exit 0
fi

echo "================================================ட்டான்"
echo "  TradeStreamEE - JVM Performance Comparison"
echo "  C4 vs G1GC Side-by-Side Demo"
echo "================================================ட்டான்"
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

# Create base monitoring directory structure for logs
mkdir -p monitoring/logs/{c4-{1,2,3},g1-{1,2,3}}

if [ "$MODE" = "all" ]; then
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

if [ "$MODE" = "all" ]; then
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

if [ "$MODE" = "all" ]; then
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
echo "To apply stress test:"
echo "  curl -X POST http://localhost:8080/trader-stream-ee/api/memory/mode/EXTREME"
echo "  curl -X POST http://localhost:9080/trader-stream-ee/api/memory/mode/EXTREME"
echo ""
echo "To stop everything:"
echo "  ./stop-comparison.sh"
echo ""
