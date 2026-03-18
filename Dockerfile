# Multi-stage Dockerfile for TradeStreamEE
# Uses Azul Platform Prime (Zing) for Pauseless Garbage Collection demonstration

FROM azul/zulu-openjdk:21 AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first for better layer caching
COPY mvnw .
COPY mvnw.cmd .
COPY spotless ./spotless
COPY .mvn .mvn
COPY pom.xml .

RUN ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw spotless:apply
RUN ./mvnw clean package -DskipTests

# Use Azul Platform Prime for C4 GC
FROM azul/prime:21

LABEL maintainer="TradeStreamEE"
LABEL description="High-frequency trading dashboard with Aeron + SBE + Payara Micro + Azul C4"

WORKDIR /opt/payara

# Add Payara Micro from URL
ARG PAYARA_VERSION=7.2026.3
ADD https://nexus.payara.fish/repository/payara-community/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar /opt/payara/payara-micro.jar

# Copy WAR file from build stage
COPY --from=build /app/target/*.war ROOT.war

# Copy entrypoint script for JFR configuration
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Create recordings directory for JFR output and gc-logs for GC logs
RUN mkdir -p /opt/payara/recordings /opt/payara/gc-logs && chmod 777 /opt/payara/recordings /opt/payara/gc-logs

EXPOSE 8080
EXPOSE 9009

# Default JVM Options for Azul Platform Prime
#
# NOTE: These JAVA_OPTS are used for single-instance deployments (start.sh script).
# For cluster deployments (start-comparison.sh), these values are overridden by
# docker-compose-{c4,g1}.yml environment variables. See those files for actual
# runtime flags in cluster mode.
#
# Azul Platform Prime uses C4 GC by default - no need to specify -XX:+UseZGC
#
# JFR is enabled by default - entrypoint script handles JFR_ENABLED environment variable
ENV JAVA_OPTS="-Xms8g \
    -Xmx8g \
    -Xlog:gc*:file=/opt/payara/gc-logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10M \
    --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens java.base/java.nio=ALL-UNNAMED \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+AlwaysPreTouch \
    -XX:+UseTransparentHugePages \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -Djava.net.preferIPv4Stack=true \
    -Xlog:jfr*=info"

# Set recording name for JFR
ENV RECORDING_NAME="production"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/trader-stream-ee/api/status || exit 1

# Use entrypoint to handle JFR configuration
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

# Run Payara Micro with the WAR
CMD java ${JAVA_OPTS} -jar payara-micro.jar --deploy ROOT.war --contextroot trader-stream-ee --nohazelcast
