#!/bin/bash
# Install local JARs to Maven repository
# Run this script before executing 'mvn clean package' if you don't have a private Nexus/Artifactory repository.

set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Support both layouts:
# - Repo root:        <root>/mvnw + <root>/framework/...
# - Azure deployment: <root>/backend/mvnw + <root>/backend/framework/...
if [ -f "$BASE_DIR/mvnw" ]; then
    BACKEND_DIR="$BASE_DIR"
elif [ -f "$BASE_DIR/backend/mvnw" ]; then
    BACKEND_DIR="$BASE_DIR/backend"
else
    echo "❌ Error: mvnw not found in $BASE_DIR or $BASE_DIR/backend"
    exit 1
fi

JAR_FILE="$BACKEND_DIR/framework/src/main/resources/maven-repository/SF-CSIM-EXPRESS-SDK-V2.1.7.jar"

if [ -f "$JAR_FILE" ]; then
    echo "📦 Installing SF-CSIM-EXPRESS-SDK-V2.1.7.jar to local Maven repository..."
    "$BACKEND_DIR/mvnw" install:install-file \
        -Dfile="$JAR_FILE" \
        -DgroupId=com.qiyuesuo.sdk \
        -DartifactId=SDK \
        -Dversion=2.1.7 \
        -Dpackaging=jar \
        -DgeneratePom=true

    echo "✅ Successfully installed local JARs."
else
    echo "❌ Error: JAR file not found at $JAR_FILE"
    exit 1
fi
