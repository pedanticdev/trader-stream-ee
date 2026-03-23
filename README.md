# TradeStreamEE: Low-Latency Trading on Jakarta EE

A Payara Platform (Jakarta EE 11) application that serves two purposes:

1. **GC Performance Comparison** - Side-by-side benchmark of Azul C4 vs G1GC under realistic allocation pressure from market data ingestion
2. **Trading System Reference** - A matching engine, risk engine, portfolio analytics, and technical analysis built with idiomatic Java 21

The application simulates a high-frequency trading desk that ingests market data via Aeron IPC + SBE (zero-copy binary), processes it through a price-time priority matching engine, computes risk metrics, and streams results to a browser dashboard via WebSocket.

## Prerequisites

- Docker and Docker Compose (for containerized deployment)
- Java 21+ JDK (for building from source; Maven wrapper included)

## Quick Start

### Side-by-Side JVM Comparison (Primary Use Case)

```bash
./start-comparison.sh all
```

Deploys both Azul C4 and G1GC clusters (3 instances each) plus a full monitoring stack (Prometheus, Grafana, Loki).

| Endpoint   | URL                                     |
|:-----------|:----------------------------------------|
| C4 Cluster | http://localhost:8080/trader-stream-ee/ |
| G1 Cluster | http://localhost:9080/trader-stream-ee/ |
| Grafana    | http://localhost:3000 (admin/admin)     |
| Prometheus | http://localhost:9090                   |

Other options:

```bash
./start-comparison.sh           # Clusters only, no monitoring
./start-comparison.sh direct    # DIRECT ingestion mode (GC stress test)
./start-comparison.sh all direct # Full deployment, GC stress mode
./stop-comparison.sh            # Tear everything down
```

### Single-JVM Testing

```bash
./start.sh azul-aeron          # Azul Prime (C4) + Aeron (peak performance)
./start.sh standard-direct     # Eclipse Temurin (G1GC) + Direct (baseline)
./start.sh azul-direct         # C4 with high allocation (stabilizes legacy code)
./start.sh standard-aeron      # G1GC with Aeron (tests architectural improvement)
```

Cluster modes (3 instances + Traefik LB + Hazelcast):

```bash
./start.sh cluster             # C4 + Aeron cluster
./start.sh cluster-standard    # G1GC + Aeron cluster
./start.sh cluster-direct      # C4 + Direct cluster
./start.sh cluster-dynamic 5   # Scale to N instances on the fly
```

Utilities: `logs`, `status`, `stop`, `clean`

## Architecture Overview

```
MarketDataPublisher (synthetic)
  |  SBE binary encode (off-heap)
  v
Aeron IPC (shared memory, stream 1001)
  |
  v
MarketDataFragmentHandler (SBE flyweight decode, zero-copy)
  |
  +---> MatchingEngine (order matching, execution)
  +---> BarAggregator (OHLCV bars, ta4j indicators)
  +---> MarketDataBroadcaster (JSON, 1:50 sampling)
         |
         +---> Hazelcast ITopic (cluster fan-out)
         |
         v
       WebSocket (/market-data, /executions, /indicators, /sla)
         |
         v
       Browser Dashboard
```

**GC monitoring runs in parallel:** `GCPauseMonitor` registers as a JMX `NotificationListener` on all `GarbageCollectorMXBean` instances, records every pause duration, computes percentiles (P50/P95/P99/P999), and pushes SLA violation events to WebSocket clients.

### Ingestion Modes

Configurable via `TRADER_INGESTION_MODE` (`AERON` or `DIRECT`).

| Mode            | Path                                                                   | Allocation     | Purpose                                           |
|:----------------|:-----------------------------------------------------------------------|:---------------|:--------------------------------------------------|
| AERON (default) | Publisher -> SBE binary -> Aeron IPC -> FragmentHandler -> Broadcaster | Low (off-heap) | Production-grade zero-copy pipeline               |
| DIRECT          | Publisher -> JSON + 1KB padding -> Broadcaster                         | High (on-heap) | Stress-test GC behavior under allocation pressure |

### Domain Model

```
MatchingEngine
  |-- PriceTimePriorityMatcher (price-time priority algorithm)
  |-- StopOrderTracker (stop, stop-limit, trailing-stop promotion)
  |-- IcebergOrderHandler (display quantity management)
  |-- OrderBook (NavigableMap<Price, LinkedList<OrderBookEntry>>)
  |
  +--> Execution --> PositionTracker --> RiskEngine
  |                    |                     |-- Historical VaR
  |                    |                     |-- Parametric VaR
  |                    |                     |-- Stress scenarios (sealed interface)
  |                    |                     +-- Exposure summary
  |                    |
  |                    +---> PortfolioService
  |                           |-- NAV tracking
  |                           |-- Sharpe ratio, max drawdown, win rate
  |                           +-- Rebalance plan generation
  |
  +--> BarAggregator --> IndicatorService
                           |-- SMA, EMA, RSI, MACD, Bollinger Bands, ATR
                           +-- ta4j integration
```

`Order` and `Price` are Java records. `Price` uses fixed-point arithmetic (`long` ticks at 1/10000 scale) to avoid floating-point issues in the matching hot path. `StressScenario` is a sealed interface with five record implementations (`FlashCrash`, `VolatilitySpike`, `LiquidityFreeze`, `CorrelationBreakdown`, `InterestRateShock`).

## Tech Stack

| Component          | Technology                                               | Version                                                       |
|:-------------------|:---------------------------------------------------------|:--------------------------------------------------------------|
| Language           | Java 21                                                  | Records, pattern matching, virtual threads, sealed interfaces |
| Runtime            | Jakarta EE 11 / Payara Micro 7                           | CDI, WebSocket, REST, Concurrency 3.1                         |
| Transport          | Aeron                                                    | 1.50.0 (IPC shared memory, kernel bypass)                     |
| Serialization      | SBE (Simple Binary Encoding)                             | 1.34.0 (FIX standard, flyweight decoders)                     |
| Technical Analysis | ta4j                                                     | 0.22.4                                                        |
| Clustering         | Hazelcast                                                | 5.5.0 (distributed topics, atomic counters)                   |
| GC Comparison      | Azul Platform Prime 21 (C4) vs Eclipse Temurin 21 (G1GC) |                                                               |
| Frontend           | HTML5 + Chart.js + vanilla JS                            | No build tools, no frameworks                                 |
| Observability      | Prometheus + Grafana + Loki + JFR                        |                                                               |
| Build              | Maven (wrapper) + Docker                                 | Multi-stage builds                                            |

## Project Structure

```text
src/main/java/fish/payara/trader/
  aeron/           Aeron MediaDriver, Publisher, Subscriber, FragmentHandler
  analysis/        BarAggregator, IndicatorService (ta4j bridge)
  concurrency/     VirtualThreadExecutor qualifier, ManagedExecutorDefinition
  demo/            DemoPresetService, scenario orchestration
  dto/             REST response objects
  gc/              GCStatsService, GCStats (JMX MXBean collection)
  impact/          BusinessImpactCalculator (GC pauses -> dollar impact)
  jfr/             Custom JFR events, recording management
  matching/
    book/          PriceTimeOrderBook, OrderBook (NavigableMap-based)
    config/        MatchingConfig
    engine/        MatchingEngine (central orchestrator)
    exception/     Matching exceptions
    history/       ExecutionHistory (circular buffer, query API)
    model/         Order, Execution, Price, Side, OrderType, OrderStatus (records/enums)
    position/      PositionService, PositionTracker (ConcurrentHashMap)
    jfr/           Matching-specific JFR events
    websocket/     ExecutionBroadcaster
  monitoring/      GCPauseMonitor, SLAMonitorService, MemoryPressure
  portfolio/       PortfolioService (NAV, Sharpe, drawdown, rebalance)
  pressure/        MemoryPressureService, AllocationMode, Workload implementations
    workload/      8 CPU/GC stress workloads (TradingMatching, Collection, Compression, etc.)
  risk/
    model/         Position, RiskSnapshot, StressScenario (sealed), VarResult
    RiskEngine     VaR computation, stress testing
    RiskConfig     Position limits
  rest/            16 JAX-RS resource classes (ApplicationConfig, CorsFilter)
  util/            Instance utilities
  websocket/       MarketDataBroadcaster (Hazelcast topic + WebSocket fan-out)

src/main/resources/
  sbe/market-data.xml         SBE schema (23 message types, FIX-inspired)
  demo-presets.yml            5 demo scenario definitions
  microprofile-config.properties
  hazelcast-config.xml        Hazelcast 5.5.0 (TCP/IP discovery for Docker)

src/main/webapp/
  index.html        Main dashboard (GC charts, market data, demo presets)
  trading.html      Trading desk (order entry, order book, positions)
  comparison.html   C4 vs G1 side-by-side view
  presentation.html Conference presentation mode
  blog.html         Documentation page
  health.html       Health check
  help.html         Usage guide
  swagger.html      API documentation
  trading.js        Trading desk client logic
  pressure-modes.js Shared GC stress scenario selector

monitoring/
  grafana/          Provisioning configs, dashboards
  jmx-exporter/     Prometheus JMX exporter
  prometheus/       Prometheus config
  loki/             Log aggregation config
  promtail/         Log shipping config
```

## REST API

All endpoints under `/api/`.

### Trading

| Method | Path                             | Description             |
|:-------|:---------------------------------|:------------------------|
| POST   | `/matching/orders`               | Submit an order         |
| DELETE | `/matching/orders/{orderId}`     | Cancel an order         |
| GET    | `/matching/orders`               | List orders             |
| GET    | `/matching/orders/book/{symbol}` | Order book snapshot     |
| GET    | `/matching/order-book/{symbol}`  | Order book for a symbol |
| GET    | `/matching/positions`            | All positions           |
| GET    | `/matching/positions/{symbol}`   | Position for a symbol   |
| GET    | `/matching/executions`           | Execution history       |

### Risk & Portfolio

| Method | Path                    | Description                |
|:-------|:------------------------|:---------------------------|
| GET    | `/risk`                 | Risk overview              |
| GET    | `/risk/{symbol}`        | Risk snapshot for a symbol |
| GET    | `/risk/metrics`         | Risk metrics               |
| GET    | `/risk/var`             | Value at Risk              |
| GET    | `/risk/stress`          | Stress test results        |
| GET    | `/risk/exposure`        | Portfolio exposure         |
| POST   | `/risk/limits/{symbol}` | Set position limits        |
| GET    | `/portfolio`            | Portfolio overview         |
| GET    | `/portfolio/snapshot`   | NAV snapshot               |
| GET    | `/portfolio/metrics`    | Sharpe, drawdown, win rate |
| POST   | `/portfolio/rebalance`  | Generate rebalance plan    |
| POST   | `/portfolio/reset`      | Reset portfolio state      |
| GET    | `/analysis/{symbol}`    | Technical indicators       |

### Monitoring & GC

| Method | Path               | Description                    |
|:-------|:-------------------|:-------------------------------|
| GET    | `/gc/stats`        | GC pause percentiles           |
| GET    | `/gc/comparison`   | Cluster comparison data        |
| GET    | `/gc/pauses`       | Recent pause history           |
| GET    | `/gc/sla`          | SLA violation counts           |
| POST   | `/gc/reset`        | Reset GC statistics            |
| POST   | `/gc/sla/reset`    | Reset SLA counters             |
| POST   | `/gc/pauses/reset` | Reset pause history            |
| GET    | `/status`          | System status                  |
| GET    | `/status/cluster`  | Hazelcast cluster info         |
| GET    | `/health/check`    | Health check                   |
| GET    | `/health/ready`    | Readiness probe                |
| GET    | `/health/live`     | Liveness probe                 |
| GET    | `/business/impact` | Business impact of GC pauses   |
| GET    | `/business/config` | Business impact configuration  |
| POST   | `/business/reset`  | Reset business impact counters |

### Memory Pressure

| Method | Path                    | Description                   |
|:-------|:------------------------|:------------------------------|
| POST   | `/pressure/mode/{mode}` | Set GC stress scenario        |
| GET    | `/pressure/status`      | Current pressure status       |
| GET    | `/pressure/modes`       | Available scenarios and types |

Memory scenarios: `STEADY_LOAD`, `GROWING_HEAP`, `PROMOTION_STORM`, `FRAGMENTATION`, `CROSS_GEN_REFS`, `OFF`

CPU workloads: `TRADING_MATCHING`, `TECHNICAL_ANALYSIS`, `COMPRESSION`, `CRYPTO`, `COLLECTION`, `SERIALIZATION`, `STRING`

### Demo Presets & JFR

| Method | Path                                   | Description                    |
|:-------|:---------------------------------------|:-------------------------------|
| GET    | `/demo/presets`                        | List all demo presets          |
| GET    | `/demo/preset/{id}`                    | Get a demo preset              |
| POST   | `/demo/preset/{id}/start`              | Start a demo preset            |
| GET    | `/demo/execution/{executionId}`        | Get execution status           |
| POST   | `/demo/execution/{id}/step/{n}`        | Step through a preset          |
| POST   | `/demo/execution/{executionId}/cancel` | Cancel a preset execution      |
| GET    | `/jfr/status`                          | JFR availability               |
| GET    | `/jfr/recordings`                      | List recordings                |
| GET    | `/jfr/files`                           | Available .jfr downloads       |
| GET    | `/jfr/stats`                           | JFR statistics                 |
| POST   | `/jfr/recording/start`                 | Start a time-bounded recording |
| POST   | `/jfr/recording/stop`                  | Stop a recording               |
| GET    | `/jfr/download/{file}`                 | Download a recording           |

## GC Monitoring & Stress Testing

### Memory Pressure Scenarios

`MemoryPressureService` generates controlled allocation pressure with 4 parallel virtual threads. Each scenario targets specific GC algorithm weaknesses.

| Scenario        | Rate     | Live Set                | What It Tests                        |
|:----------------|:---------|:------------------------|:-------------------------------------|
| STEADY_LOAD     | 200 MB/s | 512 MB stable           | Baseline young generation collection |
| GROWING_HEAP    | 150 MB/s | 100 MB to 2 GB over 60s | Mixed collection pause scaling       |
| PROMOTION_STORM | 300 MB/s | 1 GB (50% survival)     | Old gen under high promotion         |
| FRAGMENTATION   | 200 MB/s | 1 GB fragmented         | Compaction behavior                  |
| CROSS_GEN_REFS  | 150 MB/s | 800 MB in old gen       | Remembered set scanning overhead     |

Expected behavior: G1GC shows stop-the-world pauses that scale with live set size. C4 maintains pauses at or below 1ms across all scenarios.

### JFR Integration

Java Flight Recorder runs by default (circular buffer, 1h/1GB, dumps on exit). 14 custom JFR events correlate domain metrics with JVM behavior:

Market data: `TradePublished`, `QuotePublished`, `MarketDepthPublished`, `BatchProcessed`, `WebSocketBroadcast`
Pipeline: `SbeEncode`, `SbeDecode`, `BackpressureEvent`, `BurstModeActivated`, `SlaViolation`
Matching: `OrderSubmitted`, `OrderMatched`, `StopTriggered`, `OrderCanceled`

Capture ad-hoc snapshots from the web UI or via REST:

```bash
curl -X POST "http://localhost:8080/api/jfr/recording/start?name=gc-stress&durationSeconds=120"
curl http://localhost:8080/api/jfr/download/gc-stress.jfr -o analysis.jfr
jmc analysis.jfr
```

## Configuration & Tuning

### Environment Variables

| Variable                | Values                          | Default | Description                   |
|:------------------------|:--------------------------------|:--------|:------------------------------|
| `TRADER_INGESTION_MODE` | `AERON`, `DIRECT`               | `AERON` | Data ingestion architecture   |
| `ENABLE_PUBLISHER`      | `true`, `false`                 | -       | Enable market data publishing |
| `JFR_ENABLED`           | `true`, `false`                 | `true`  | Enable default JFR recording  |
| `JVM_TYPE`              | `azul-c4`, `eclipse-temurin-g1` | -       | Label for monitoring          |

### JVM Heap Sizes

| Deployment              | Dockerfiles                                     | Heap | Reason             |
|:------------------------|:------------------------------------------------|:-----|:-------------------|
| Single instance         | `Dockerfile`, `Dockerfile.standard`             | 8 GB | Maximum throughput |
| Clustered (3 instances) | `Dockerfile.scale`, `Dockerfile.scale.standard` | 4 GB | ~12 GB per cluster |

### Notable JVM Flags (Both)

`AlwaysPreTouch`, `UseTransparentHugePages`, `UseStringDeduplication`, `OptimizeStringConcat`, `UseContainerSupport`, `MaxRAMPercentage=75.0`, `--add-opens` for Aeron's `Unsafe` usage (`jdk.internal.misc`, `sun.nio.ch`, `java.nio`)

G1GC instances add: `G1HeapRegionSize=16m`, `MaxGCPauseMillis=10`, `ParallelGCThreads=12`, `ConcGCThreads=4`

## Testing

```bash
./test.sh quick          # Unit tests only (~30 seconds)
./test.sh full           # Unit + integration tests
./test.sh unit           # Unit tests
./test.sh integration    # Integration tests
./test.sh coverage       # Generate JaCoCo report
./test.sh benchmarks     # JMH benchmarks
```

Maven commands:

```bash
./mvnw test                        # Unit tests (JaCoCo enforced)
./mvnw verify                      # Unit + integration (Failsafe)
./mvnw clean package -Pquick-test   # Exclude integration/load/benchmark
./mvnw clean verify -Pcoverage     # CI coverage report (relaxed thresholds)
```

Coverage gates (default): 70% instruction, 60% branch, 80% class.

Test stack: JUnit 5.13.4 + Mockito 5.20.0 + AssertJ 3.27.6. Tests instantiate objects directly with field-injected mocks. No CDI containers in unit tests.

## Building

```bash
./mvnw clean package              # Full build with tests
./mvnw clean package -DskipTests  # Build without tests
```

Build pipeline: SBE code generation (`exec-maven-plugin` against `market-data.xml`) -> compile (Java 21) -> WAR packaging -> test -> JaCoCo report -> Spotless format check.

Maven wrapper included.

## Docker

Four Dockerfiles for the JVM comparison matrix:

| Dockerfile                  | Runtime            | JVM  | Cluster                   |
|:----------------------------|:-------------------|:-----|:--------------------------|
| `Dockerfile`                | Azul Prime 21      | C4   | No                        |
| `Dockerfile.standard`       | Eclipse Temurin 21 | G1GC | No                        |
| `Dockerfile.scale`          | Azul Prime 21      | C4   | Yes (Hazelcast + Traefik) |
| `Dockerfile.scale.standard` | Eclipse Temurin 21 | G1GC | Yes (Hazelcast + Traefik) |

All use multi-stage builds: compile on `azul/zulu-openjdk:21`, copy WAR to runtime image. Payara Micro 7.2026.3 downloaded at build time.

Six docker-compose files: `docker-compose.yml` (single C4), `docker-compose-standard.yml` (single G1), `docker-compose-scale.yml` (dynamic cluster), `docker-compose-c4.yml` (3-instance C4 + Traefik + JMX exporter), `docker-compose-g1.yml` (3-instance G1 + Traefik + JMX exporter), `docker-compose-monitoring.yml` (Prometheus + Grafana + Loki + Promtail).

## Design Patterns

- **Flyweight** - SBE decoders wrap DirectBuffer at different offsets. No allocation per message.
- **Sealed Interface** - `StressScenario` with five record implementations. Pattern matching dispatches stress tests.
- **Strategy** - `MatchingStrategy` interface with `PriceTimePriorityMatcher` implementation.
- **Observer** - JMX `NotificationListener` on GC beans. JFR events as telemetry observation layer.
- **CDI Qualifier** - `@VirtualThreadExecutor` injects a virtual-thread-based `ManagedExecutorService` via `@ManagedExecutorDefinition(virtual = true)`.

## SBE Wire Protocol

`market-data.xml` defines 23 FIX-inspired message types. Code generated at build time into `fish.payara.trader.sbe`. All prices use `int64` fixed-point (multiply by 10000 for decimal). Currently 5 message types are decoded in the ingestion path: Trade, Quote, MarketDepth, OrderAck, Heartbeat. The remaining 18 exist as pre-built infrastructure for the full trading lifecycle.

## Key Design Decisions

1. **Intentional garbage generation** - JSON construction via `StringBuilder` in the broadcast path is deliberately unoptimized. This creates measurable allocation pressure to demonstrate GC behavior differences.

2. **1:50 sampling** - Only every 50th message reaches the browser. All messages still flow through the SBE decoder and matching engine. The browser gets 600-2000 updates/sec, not 30,000-100,000.

3. **In-memory state** - Order books, positions, execution history, and bar data live in ConcurrentHashMaps and NavigableMaps. No database. Restarting loses all state. `persistence.xml` exists but is unused.

4. **Hazelcast for cluster fan-out** - In multi-instance mode, market data published on one instance is distributed to all instances via a Hazelcast `ITopic`. WebSocket clients on any instance receive all messages.

5. **Burst pattern** - The publisher runs 500 base iterations (3 messages each) per cycle with 5us parks. Burst multiplier spikes to 5x during "news events" (seconds 20-25 of each minute) and 3x during "market close" (seconds 45-50). Circuit breaker stops after 50 consecutive failures.

## Trading Terms Glossary

### Market Data

| Term                          | Definition                                                                                                          |
|:------------------------------|:--------------------------------------------------------------------------------------------------------------------|
| High-Frequency Trading (HFT)  | Automated strategies executing thousands of orders per second. Sub-millisecond latency is a hard requirement.       |
| Order Book / Limit Order Book | Data structure containing all buy and sell orders, organized by price level. Updated thousands of times per second. |
| Bid / Ask                     | Highest price a buyer will pay / lowest price a seller will accept.                                                 |
| Bid-Ask Spread                | Difference between best bid and best ask. Cost of immediate execution.                                              |
| L1 / L2 / L3 Data             | Level 1: best bid/ask. Level 2: multiple price levels with depth. Level 3: individual orders.                       |
| Crossed Market                | Best bid exceeds best ask. Indicates data error or system malfunction.                                              |

### Memory & GC

| Term                  | Definition                                                                                   |
|:----------------------|:---------------------------------------------------------------------------------------------|
| Allocation Rate       | Speed of object creation (bytes/sec). Higher rates increase GC pressure.                     |
| Live Set              | Total size of reachable objects. G1's mixed collection pause time scales with live set size. |
| Concurrent Collection | GC running alongside application threads. C4 is concurrent; G1GC uses stop-the-world pauses. |
| Promotion             | Moving objects from young to old generation after surviving multiple collections.            |
| Remembered Set        | G1 data structure tracking old-to-young references. Scanning adds overhead. C4 has none.     |
| Heap Fragmentation    | Free space scattered in small chunks. G1 pauses to compact; C4 compacts concurrently.        |

### Serialization & Performance

| Term            | Definition                                                                                |
|:----------------|:------------------------------------------------------------------------------------------|
| SBE             | Simple Binary Encoding. FIX-standard binary format for ultra-low-latency trading systems. |
| Aeron IPC       | Shared-memory messaging transport. Bypasses kernel networking. Sub-microsecond latency.   |
| Flyweight       | Reusable objects over byte buffers. SBE decoders are flyweights, not allocated objects.   |
| Zero-Copy       | Processing data without copying between buffers. Both Aeron IPC and SBE use zero-copy.    |
| P50 / P95 / P99 | Percentile latencies. 50% / 95% / 99% of requests complete faster than this value.        |
| Jitter          | Latency variability. High jitter (unpredictable spikes) is unacceptable for HFT.          |

## References

- [Azul C4 Garbage Collection](https://docs.azul.com/prime/c4-garbage-collection.html)
- [Azul Platform Prime](https://www.azul.com/products/components/pgc/)
- [Aeron Messaging](https://aeron.io/)
- [Simple Binary Encoding](https://github.com/Real-Logic-FIX/Simple-Binary-Encoding)
- [Payara Platform](https://www.payara.fish/)
- [ta4j Technical Analysis](https://ta4j.github.io/)

## License

This project is a reference implementation showing low-latency Java techniques for educational purposes.
