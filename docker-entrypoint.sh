#!/bin/bash
# docker-entrypoint.sh
#
# Builds JAVA_OPTS for single-instance Payara Micro deployments.
# Always-on JFR is OFF by default; ad-hoc recordings are produced on demand
# via the /api/jfr REST endpoints. Set JFR_ALWAYS_ON=true to opt in to a
# circular always-on recording.

set -e

DEFAULT_OPTS="-Xms8g -Xmx8g -XX:+UseZGC \
-Xlog:gc*:file=/opt/payara/gc-logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10M \
-XX:+AlwaysPreTouch -XX:+UseTransparentHugePages \
-XX:+UseStringDeduplication \
-XX:+OptimizeStringConcat \
-Djava.net.preferIPv4Stack=true \
-Xlog:jfr*=info"

JFR_RECORDING_NAME="${JFR_RECORDING_NAME:-${RECORDING_NAME:-production}}"

if [ "${JFR_ALWAYS_ON}" = "true" ]; then
    export JAVA_OPTS="${DEFAULT_OPTS} \
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
-XX:StartFlightRecording=name=${JFR_RECORDING_NAME},filename=/opt/payara/recordings/recording.jfr,dumponexit=true,maxage=1h,maxsize=1g,method-profiling=normal \
-XX:FlightRecorderOptions=stackdepth=256"
else
    export JAVA_OPTS="${DEFAULT_OPTS} \
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED"
fi

exec "$@"
