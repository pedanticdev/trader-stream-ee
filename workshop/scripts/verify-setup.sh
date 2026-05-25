#!/usr/bin/env bash
# verify-setup.sh
# Sanity-check the attendee laptop before the workshop starts.
#
# Run this from the project root:
#   ./workshop/scripts/verify-setup.sh

set -uo pipefail

PASS=0
FAIL=0
WARN=0

ok()    { echo "  [ OK ] $*"; PASS=$((PASS+1)); }
fail()  { echo "  [FAIL] $*"; FAIL=$((FAIL+1)); }
warn()  { echo "  [WARN] $*"; WARN=$((WARN+1)); }
section(){ echo; echo "== $* =="; }

section "Operating system"
case "$(uname -s)" in
    Linux*)   ok "Linux detected" ;;
    Darwin*)  ok "macOS detected" ;;
    MINGW*|MSYS*|CYGWIN*) warn "Windows shell detected; prefer WSL2 for Docker performance" ;;
    *) warn "Unknown OS: $(uname -s)" ;;
esac

section "Docker"
if command -v docker >/dev/null 2>&1; then
    ver=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo "unreachable")
    if [ "$ver" = "unreachable" ]; then
        fail "Docker CLI installed but daemon is not reachable. Start Docker Desktop / docker service."
    else
        ok "Docker daemon reachable (v$ver)"
    fi
else
    fail "Docker not installed. Install Docker Desktop: https://www.docker.com/products/docker-desktop"
fi

if docker compose version >/dev/null 2>&1; then
    ok "Docker Compose v2 available"
elif command -v docker-compose >/dev/null 2>&1; then
    warn "Docker Compose v1 detected. Workshop scripts target v2; consider upgrading."
else
    fail "Docker Compose not found"
fi

section "Java toolchain"
if command -v java >/dev/null 2>&1; then
    jver=$(java -version 2>&1 | head -1)
    ok "java: $jver"
    major=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)\..*/\1/')
    if [ "${major:-0}" -ge 21 ]; then
        ok "Java 21+ detected (major=$major)"
    else
        warn "Java $major detected; the workshop targets 21. The Docker image bundles its own JDK so this is informational only."
    fi
else
    warn "java not on PATH. The Docker image bundles its own JDK, so this is informational only."
fi

if command -v jcmd >/dev/null 2>&1; then
    ok "jcmd available (useful for ad-hoc JFR control outside Docker)"
else
    warn "jcmd not on PATH. Used for the Module 5 production-safe recording demo."
fi

section "JDK Mission Control"
if command -v jmc >/dev/null 2>&1; then
    ok "jmc on PATH: $(command -v jmc)"
else
    warn "jmc not on PATH. Install one of:"
    echo "         - Azul Mission Control (free): https://www.azul.com/products/components/azul-mission-control/"
    echo "         - OpenJDK JMC build:           https://github.com/openjdk/jmc"
    echo "         - bundled with Azul Zulu (the ZGC image used here)"
fi

section "Port availability"
for port in 8080 8081 8082 8083 9080 9081 9082 9083 9090 3000 3100; do
    if command -v lsof >/dev/null 2>&1; then
        if lsof -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
            fail "Port $port is already in use. The workshop needs it free."
        else
            ok "Port $port is free"
        fi
    elif command -v ss >/dev/null 2>&1; then
        if ss -tln "( sport = :$port )" 2>/dev/null | grep -q ":$port"; then
            fail "Port $port is already in use"
        else
            ok "Port $port is free"
        fi
    else
        warn "Cannot check port $port (neither lsof nor ss installed)"
        break
    fi
done

section "Disk space"
if command -v df >/dev/null 2>&1; then
    avail=$(df -BG --output=avail . 2>/dev/null | tail -1 | tr -dc '0-9' || echo "0")
    if [ "${avail:-0}" -ge 10 ]; then
        ok "Free disk space: ${avail}G (need ~5G for images and recordings)"
    else
        warn "Only ${avail}G free. Recommend at least 10G."
    fi
fi

section "Tools used by analysis scripts"
for tool in curl jq jfr; do
    if command -v $tool >/dev/null 2>&1; then
        ok "$tool installed"
    elif [ "$tool" = "jfr" ]; then
        warn "jfr CLI not on PATH. Ships with any JDK 21+; needed by compare-recordings.sh."
    else
        warn "$tool not installed. Install with your package manager; needed by record-scenarios.sh."
    fi
done

# record-scenarios.sh used to require GNU find -printf, which BSD find on macOS
# does not support. The script has been switched to ls -1t which is portable.
# This check is a smoke test against future regressions.
if command -v ls >/dev/null 2>&1 && ls -1t / >/dev/null 2>&1; then
    ok "ls -1t supported (used by record-scenarios.sh to find newest dump)"
fi

section "Summary"
echo "  Passed:   $PASS"
echo "  Warnings: $WARN"
echo "  Failed:   $FAIL"
echo

if [ "$FAIL" -gt 0 ]; then
    echo "  Setup is incomplete. Fix the FAIL items above before running the workshop."
    exit 1
fi
echo "  Setup looks ready."
