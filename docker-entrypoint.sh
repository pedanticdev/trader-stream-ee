#!/bin/bash
set -e

# Default JVM options (without JFR)
DEFAULT_OPTS="-Xms8g -Xmx8g \
-Xlog:gc*:file=/opt/payara/gc-logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10M \
-XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions \
-XX:+AlwaysPreTouch -XX:+UseTransparentHugePages \
-XX:+UseStringDeduplication \
-XX:+OptimizeStringConcat \
-Djava.net.preferIPv4Stack=true \
-Xlog:jfr*=info"

# Recording name (can be overridden by ENV)
RECORDING_NAME="${RECORDING_NAME:-production}"

# Build JAVA_OPTS based on JFR_ENABLED setting
if [ "${JFR_ENABLED}" = "false" ]; then
    # JFR disabled - use default options only
    export JAVA_OPTS="${DEFAULT_OPTS}"
else
    # JFR enabled - add JFR options
    export JAVA_OPTS="${DEFAULT_OPTS} \
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
-XX:StartFlightRecording=name=${RECORDING_NAME},filename=/opt/payara/recordings/recording.jfr,dumponexit=true,maxage=1h,maxsize=1g,method-profiling=normal \
-XX:FlightRecorderOptions=stackdepth=256"
fi

# Execute the CMD
exec "$@"
