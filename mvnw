#!/bin/sh
# ----------------------------------------------------------------------------
# Health-ID Vault - Robust Maven Wrapper Proxy
# ----------------------------------------------------------------------------

# Set local maven path
LOCAL_MVN_DIR="$(cd "$(dirname "$0")" && pwd)/apache-maven-3.9.6"

if [ -x "$LOCAL_MVN_DIR/bin/mvn" ]; then
    export MAVEN_HOME="$LOCAL_MVN_DIR"
    exec "$MAVEN_HOME/bin/mvn" "$@"
elif command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
else
    echo "❌ Error: Maven not found locally or in system PATH."
    exit 1
fi