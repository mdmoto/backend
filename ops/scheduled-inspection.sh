#!/bin/bash
# Maollar Scheduled Inspection & Health Audit
# This script performs a holistic check of the platform's vital signs.
# Usage: ./ops/scheduled-inspection.sh

set -euo pipefail

REPORT_DIR="evidence/inspections/$(date +%F)"
mkdir -p "$REPORT_DIR"
LOG_FILE="$REPORT_DIR/inspection-$(date +%H%M%S).log"
exec > >(tee -a "$LOG_FILE") 2>&1

log() { echo "🩺 [INSPECTION] $(date +'%Y-%m-%d %H:%M:%S') - $*"; }

log "Starting Platform Health Audit..."

# 1. Service Persistence (Systemd)
log "Checking Systemd Services..."
SERVICES=("lili-buyer-api" "lili-seller-api" "lili-manager-api" "lili-common-api" "lili-consumer")
for svc in "${SERVICES[@]}"; do
    if sudo systemctl is-active --quiet "$svc" 2>/dev/null; then
        log "✅ $svc: ACTIVE"
    else
        log "❌ $svc: INACTIVE or NOT FOUND"
    fi
done

# 2. Network & Ports
log "Checking Core Ports..."
PORTS=("8888" "8889" "8890" "8891" "8892" "3306" "6379" "9876")
for port in "${PORTS[@]}"; do
    if nc -z 127.0.0.1 "$port" 2>/dev/null; then
        log "✅ Port $port: REACHABLE"
    else
        log "⚠️ Port $port: UNREACHABLE"
    fi
done

# 3. Log Analysis (Error Scanning)
log "Scanning logs for recent errors (Last 1 hour)..."
# Sample log path - adjust to actual production log location
LOG_PATH="/var/log/lilishop" 
if [ -d "$LOG_PATH" ]; then
    ERROR_COUNT=$(grep -ri "ERROR" "$LOG_PATH" --since "1 hour ago" 2>/dev/null | wc -l || echo "0")
    log "🔍 Found $ERROR_COUNT ERROR entries in logs."
else
    log "⏭️ Log directory $LOG_PATH not found; skipping log analysis."
fi

# 4. Resource Usage
log "System Resources..."
df -h / | tail -1 | awk '{print "Disk Usage: " $5}'
free -m 2>/dev/null | grep Mem | awk '{print "Memory Usage: " $3 "/" $2 " MB"}' || log "Memory info unavailable"

log "Audit Completed. Detailed report saved to $LOG_FILE"
