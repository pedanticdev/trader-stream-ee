# Multi-stage Dockerfile for TradeStreamEE
# Uses Azul Platform Prime (Zing) for Pauseless Garbage Collection demonstration

FROM azul/zulu-openjdk:21 AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first for better layer caching
COPY mvnw .
COPY mvnw.cmd .
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
ARG PAYARA_VERSION=6.2025.11
ADD https://nexus.payara.fish/repository/payara-community/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar /opt/payara/payara-micro.jar

# Copy WAR file from build stage
COPY --from=build /app/target/*.war ROOT.war

EXPOSE 8080

# Default JVM Options for Azul Platform Prime
# Note: These are defaults - can be overridden via docker-compose or docker run -e
# Azul Platform Prime uses C4 GC by default - no need to specify -XX:+UseZGC
ENV JAVA_OPTS="-Xms8g \
    -Xmx8g \
    -Xlog:gc*:file=/opt/payara/gc.log:time,uptime,level,tags:filecount=5,filesize=10M \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+AlwaysPreTouch \
    -XX:+UseTransparentHugePages \
    -XX:-UseBiasedLocking \
    -Djava.net.preferIPv4Stack=true"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/trader-stream-ee/api/status || exit 1

# Run Payara Micro with the WAR
CMD java ${JAVA_OPTS} -jar payara-micro.jar --deploy ROOT.war --contextroot trader-stream-ee --nohazelcast
