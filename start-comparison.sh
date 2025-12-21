#!/bin/bash

set -e

echo "================================================ட்டான"
echo "  TradeStreamEE - JVM Performance Comparison"
echo "  C4 vs G1GC Side-by-Side Demo"
echo "================================================ட்டான"
echo ""

# Create monitoring directory structure
mkdir -p monitoring/{prometheus,grafana/{provisioning/{datasources,dashboards},dashboards},loki,promtail,logs/{c4-{1,2,3},g1-{1,2,3}}}

# Download JMX Exporter if not present
if [ ! -f "monitoring/jmx-exporter/jmx_prometheus_javaagent-1.0.1.jar" ]; then
    echo "Downloading JMX Prometheus Exporter 1.0.1..."
    mkdir -p monitoring/jmx-exporter
    wget -q -O monitoring/jmx-exporter/jmx_prometheus_javaagent-1.0.1.jar \
        https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/1.0.1/jmx_prometheus_javaagent-1.0.1.jar
    echo "✓ JMX Exporter downloaded"
fi

# Build application
echo "Building application..."
./mvnw clean package -DskipTests
echo "✓ Build complete"

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

# Start monitoring stack
echo "Starting monitoring stack (Prometheus, Grafana, Loki)..."
docker compose -f docker-compose-monitoring.yml up -d
echo "✓ Monitoring stack started"

# Wait for monitoring to be ready
echo "Waiting for monitoring stack to initialize..."
sleep 10

# Start C4 cluster
echo "Starting Azul C4 cluster (ports 8080-8083)..."
docker compose -f docker-compose-c4.yml up -d
echo "✓ C4 cluster started"

# Start G1 cluster
echo "Starting G1GC cluster (ports 9080-9083)..."
docker compose -f docker-compose-g1.yml up -d
echo "✓ G1 cluster started"

echo ""
echo "================================================ட்டான"
echo "  Deployment Complete!"
echo "================================================ட்டான"
echo ""
echo "Application Endpoints:"
echo "  C4 Cluster:     http://localhost:8080/trader-stream-ee/"
echo "  G1 Cluster:     http://localhost:9080/trader-stream-ee/"
echo ""
echo "Monitoring:"
echo "  Prometheus:     http://localhost:9090"
echo "  Grafana:        http://localhost:3000 (admin/admin)"
echo "  Loki:           http://localhost:3100"
echo ""
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
