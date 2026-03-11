#!/bin/bash
# Install local JARs to Maven repository
# Run this script before executing 'mvn clean package' if you don't have a private Nexus/Artifactory repository.

set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_FILE="$BASE_DIR/backend/framework/src/main/resources/maven-repository/SF-CSIM-EXPRESS-SDK-V2.1.7.jar"

if [ -f "$JAR_FILE" ]; then
    echo "📦 Installing SF-CSIM-EXPRESS-SDK-V2.1.7.jar to local Maven repository..."
    "$BASE_DIR/backend/mvnw" install:install-file \
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
