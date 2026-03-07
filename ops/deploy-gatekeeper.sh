#!/bin/bash
# Maollar Deployment Gatekeeper (P4 Phase)
# Consolidates all verification steps into a single gate.
# This script must exit 0 for a PR to be merged or a deployment to proceed.

set -euo pipefail

EVIDENCE_DIR="evidence/gatekeeper-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$EVIDENCE_DIR"

echo "🛡️ [GATEKEEPER] Starting Phase 1: Build & Unit Verification..." | tee -a "$EVIDENCE_DIR/log.txt"
if ! bash verify-pipeline.sh; then
    echo "❌ [GATEKEEPER] Phase 1 FAILED (Build/Tests/FlattenCheck)." | tee -a "$EVIDENCE_DIR/log.txt"
    exit 1
fi
echo "✅ [GATEKEEPER] Phase 1 Passed." | tee -a "$EVIDENCE_DIR/log.txt"

echo "🔬 [GATEKEEPER] Starting Phase 2: System Health Inspection..." | tee -a "$EVIDENCE_DIR/log.txt"
if ! bash ops/scheduled-inspection.sh; then
    echo "❌ [GATEKEEPER] Phase 2 FAILED (Scheduled Inspection check)." | tee -a "$EVIDENCE_DIR/log.txt"
    exit 1
fi
echo "✅ [GATEKEEPER] Phase 2 Passed." | tee -a "$EVIDENCE_DIR/log.txt"

echo "⚡ [GATEKEEPER] Starting Phase 3: Transactional Smoke Test (Real DB Check)..." | tee -a "$EVIDENCE_DIR/log.txt"
# Note: Requires correct SMOKE environment variables.
if ! SMOKE_ALLOW_DB_MUTATION=1 RUN_WRITE_SMOKE=1 bash verify-pipeline.sh; then
    echo "❌ [GATEKEEPER] Phase 3 FAILED (Transactional Write Smoke Test)." | tee -a "$EVIDENCE_DIR/log.txt"
    exit 1
fi
echo "✅ [GATEKEEPER] Phase 3 Passed." | tee -a "$EVIDENCE_DIR/log.txt"

echo "🎉 [GATEKEEPER] ALL CHECKS PASSED. Deployment is SAFELY permitted." | tee -a "$EVIDENCE_DIR/log.txt"
mv verify-pipeline.*.log "$EVIDENCE_DIR/" 2>/dev/null || true
exit 0
