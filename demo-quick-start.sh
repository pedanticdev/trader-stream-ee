#!/bin/bash

set -e

USAGE="
Usage: ./demo-quick-start.sh [OPTIONS]

Quick demo launcher for TradeStreamEE. Uses pre-built images for fast startup.

OPTIONS:
  all         Start both clusters with monitoring (default)
  apps        Start clusters only (no monitoring)
  c4          Start C4 cluster only
  g1          Start G1 cluster only
  stop        Stop all demo services
  help        Show this help message

EXAMPLES:
  ./demo-quick-start.sh           # Start everything (recommended for demos)
  ./demo-quick-start.sh apps      # Start clusters only, no monitoring
  ./demo-quick-start.sh stop      # Stop all services
"

# Default mode
MODE="${1:-all}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo ""
    echo "================================================"
    echo "  TradeStreamEE - Quick Demo Launcher"
    echo "================================================"
    echo ""
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Check if Docker is running
check_docker() {
    if ! docker info &> /dev/null; then
        print_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
    print_success "Docker is running"
}

# Check required images
check_images() {
    print_info "Checking for required Docker images..."

    C4_IMAGE=$(docker images -q trader-stream-ee:c4 2>/dev/null)
    G1_IMAGE=$(docker images -q trader-stream-ee:g1 2>/dev/null)

    if [ -z "$C4_IMAGE" ] || [ -z "$G1_IMAGE" ]; then
        print_warning "Pre-built images not found. Building now (this takes 2-3 minutes)..."
        build_images
    else
        print_success "Pre-built images found"
    fi
}

# Build images if needed
build_images() {
    print_info "Building Docker images..."
    docker build -t trader-stream-ee:c4 -f Dockerfile.scale .
    docker build -t trader-stream-ee:g1 -f Dockerfile.scale.standard .
    print_success "Images built successfully"
}

# Create networks
create_networks() {
    print_info "Creating Docker networks..."
    docker network create trader-network 2>/dev/null || echo "Network trader-network exists"
    docker network create monitoring 2>/dev/null || echo "Network monitoring exists"
    print_success "Networks ready"
}

# Download JMX Exporter
download_jmx_exporter() {
    if [ ! -f "monitoring/jmx-exporter/jmx_prometheus_javaagent-1.0.1.jar" ]; then
        print_info "Downloading JMX Exporter..."
        mkdir -p monitoring/jmx-exporter
        wget -q -O monitoring/jmx-exporter/jmx_prometheus_javaagent-1.0.1.jar \
            https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/1.0.1/jmx_prometheus_javaagent-1.0.1.jar
        print_success "JMX Exporter downloaded"
    fi
}

# Start monitoring stack
start_monitoring() {
    print_info "Starting monitoring stack..."
    docker compose -f docker-compose-monitoring.yml up -d
    print_success "Monitoring stack started"
    sleep 5
}

# Start C4 cluster
start_c4() {
    print_info "Starting C4 cluster (ports 8080-8083)..."
    docker compose -f docker-compose-c4.yml up -d
    print_success "C4 cluster started"
}

# Start G1 cluster
start_g1() {
    print_info "Starting G1 cluster (ports 9080-9083)..."
    docker compose -f docker-compose-g1.yml up -d
    print_success "G1 cluster started"
}

# Stop all services
stop_all() {
    print_info "Stopping all demo services..."

    # Stop clusters
    docker compose -f docker-compose-c4.yml down 2>/dev/null || true
    docker compose -f docker-compose-g1.yml down 2>/dev/null || true

    # Stop monitoring if requested
    if [ "$MODE" = "stop" ]; then
        docker compose -f docker-compose-monitoring.yml down 2>/dev/null || true
    fi

    print_success "All services stopped"
}

# Wait for clusters to be ready
wait_for_ready() {
    print_info "Waiting for clusters to initialize (this may take 30-60 seconds)..."

    local c4_ready=false
    local g1_ready=false
    local attempts=0
    local max_attempts=30

    while [ $attempts -lt $max_attempts ]; do
        if [ "$c4_ready" = false ]; then
            if curl -s http://localhost:8080/trader-stream-ee/api/status &> /dev/null; then
                c4_ready=true
                print_success "C4 cluster is ready"
            fi
        fi

        if [ "$g1_ready" = false ]; then
            if curl -s http://localhost:9080/trader-stream-ee/api/status &> /dev/null; then
                g1_ready=true
                print_success "G1 cluster is ready"
            fi
        fi

        if [ "$c4_ready" = true ] && [ "$g1_ready" = true ]; then
            break
        fi

        sleep 2
        attempts=$((attempts + 1))
        echo -n "."
    done
    echo ""

    if [ $attempts -eq $max_attempts ]; then
        print_warning "Clusters are taking longer than expected. Check logs with: docker compose logs"
    fi
}

# Show access information
show_access_info() {
    echo ""
    echo "================================================"
    echo "  Demo Ready!"
    echo "================================================"
    echo ""
    echo "Application Endpoints:"
    echo "  C4 Cluster:     http://localhost:8080/trader-stream-ee/"
    echo "  G1 Cluster:     http://localhost:9080/trader-stream-ee/"
    echo "  Comparison:     http://localhost:8080/trader-stream-ee/comparison.html"
    echo "  Health Check:   http://localhost:8080/trader-stream-ee/health.html"
    echo ""

    # Check if monitoring is running
    if docker ps | grep -q "trader-prometheus"; then
        echo "Monitoring:"
        echo "  Grafana:        http://localhost:3000 (admin/admin)"
        echo "  Prometheus:     http://localhost:9090"
        echo ""
    fi

    echo "Quick Demo Commands:"
    echo "  # Apply stress test to both clusters"
    echo "  curl -X POST http://localhost:8080/trader-stream-ee/api/pressure/mode/PROMOTION_STORM"
    echo "  curl -X POST http://localhost:9080/trader-stream-ee/api/pressure/mode/PROMOTION_STORM"
    echo ""
    echo "  # Check GC stats"
    echo "  curl http://localhost:8080/trader-stream-ee/api/gc/stats"
    echo "  curl http://localhost:9080/trader-stream-ee/api/gc/stats"
    echo ""
    echo "To stop: ./demo-quick-start.sh stop"
    echo ""
}

# Main execution
main() {
    case $MODE in
        help|--help|-h)
            echo "$USAGE"
            exit 0
            ;;
        stop)
            print_header
            check_docker
            stop_all
            exit 0
            ;;
        apps)
            START_MONITORING=false
            ;;
        c4)
            print_header
            check_docker
            check_images
            create_networks
            download_jmx_exporter
            start_c4
            echo ""
            print_success "C4 Cluster running on http://localhost:8080/trader-stream-ee/"
            exit 0
            ;;
        g1)
            print_header
            check_docker
            check_images
            create_networks
            download_jmx_exporter
            start_g1
            echo ""
            print_success "G1 Cluster running on http://localhost:9080/trader-stream-ee/"
            exit 0
            ;;
        all|"")
            START_MONITORING=true
            ;;
        *)
            echo "Unknown mode: $MODE"
            echo "$USAGE"
            exit 1
            ;;
    esac

    print_header
    check_docker
    check_images
    create_networks
    download_jmx_exporter

    if [ "$START_MONITORING" = true ]; then
        start_monitoring
    fi

    start_c4
    start_g1

    wait_for_ready
    show_access_info
}

main "$@"
