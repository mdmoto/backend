#!/bin/bash
# Maollar Scheduled Inspection Script
# Runs weekly/daily to ensure system integrity.

set -e

EVIDENCE_DIR="$HOME/lilishop-deployment/evidence/inspections"
mkdir -p "$EVIDENCE_DIR"

TS=$(date +%Y%m%d-%H%M%S)
REPORT="$EVIDENCE_DIR/inspection-$TS.log"

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" | tee -a "$REPORT"
}

log "🛡️ Starting Scheduled Inspection..."

# 1. Internal Pipeline Check (Health only)
log "🧪 [1/3] Running Internal Health Pipeline..."
# We use a special flag for skip build if possible, but verify-pipeline.sh 
# currently rebuilds. For inspection, we should just run health checks.
bash ~/lilishop-deployment/ops/external-probe.sh >> "$REPORT" 2>&1
STATUS=$?

if [ $STATUS -eq 0 ]; then
    log "✅ Internal Health Check: PASS"
else
    log "❌ Internal Health Check: FAIL"
fi

# 2. SLI Analysis
log "📊 [2/3] Analyzing Service Level Indicators (last 60 min)..."
# Simple log analysis (placeholder for more complex logic)
LOG_FILE="$HOME/lilishop-deployment/backend/logs/buyer-api/info.log"
if [ -f "$LOG_FILE" ]; then
    ERROR_COUNT=$(grep -c "ERROR" "$LOG_FILE" || echo "0")
    log "📈 Errors in last log file: $ERROR_COUNT"
else
    log "⚠️ Log file not found for SLI analysis."
fi

# 3. Disk & Resource Check
log "🔋 [3/3] Checking System Resources..."
DF_INFO=$(df -h / | tail -1)
log "💾 Disk Usage: $DF_INFO"

log "🎉 Inspection Completed. Report saved to $REPORT"

# Exit with failure if health check failed to trigger cron notification (if configured)
exit $STATUS
