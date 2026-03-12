#!/bin/bash
# Maollar Local PR Pass - Pre-push Verification Tool
# This script wraps all mandatory checks defined in PR_CHECKLIST.md.
# Usage: ./ops/pr-pass.sh

set -euo pipefail

export PROJECT_ROOT="$(pwd)"
EVIDENCE_DIR="evidence/pr-pass-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$EVIDENCE_DIR"

log() { echo "✨ [PR-PASS] $*"; }
error() { echo "❌ [PR-PASS] $*"; exit 1; }

log "Starting local verification..."

# 1. Circular Dependency Check (Local baseline)
log "Step 1: Checking for allow-circular-references: false..."
if ! grep -q "allow-circular-references: false" "config/application.yml"; then
    error "Circular references are NOT disabled in config/application.yml"
fi

# 2. Compile & Unit Tests (Framework & Buyer-API)
log "Step 2: Running Circular + Order Fulfillment Guards..."
# We use JDK 17 as baseline for local pass if available
if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME)
fi

./mvnw -B test -pl framework,buyer-api -am -Dtest=CircularDependencyGuardTest,OrderFulfillmentRegressionTest -Dsurefire.failIfNoSpecifiedTests=false | tee "$EVIDENCE_DIR/maven_test.log" || error "Guards FAILED."

# 3. Code Hygiene (Static analysis placeholder or custom grep)
log "Step 3: Checking for forbidden hardcoded secrets..."
if grep -rEi '^[[:space:]]*(password|token|secret):[[:space:]]*[^[:space:]$]' "./" --include="*.yml" --exclude-dir=.git; then
    error "Potential hardcoded secrets found!"
fi

log "🎉 LOCAL CHECKS PASSED."
log "Next steps:"
log "1. Push your changes."
log "2. Running 'RUN_WRITE_SMOKE=1 bash verify-pipeline.sh' on Azure dev cluster is still MANDATORY for full admission."
log "3. Prepare your Release Note using ops/RELEASE_NOTE_TEMPLATE.md."
