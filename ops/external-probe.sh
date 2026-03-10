#!/bin/bash
# Maollar External Probe - Production Guard with Alerting
# This script integrates health, 4xx/5xx monitoring, and SSL checks with alerting.

set -e

# Webhook for alerts (Replace with real Slack/Discord webhook URL)
ALERT_WEBHOOK="${WEBHOOK_URL:-}"
API_HOST="api.maollar.com"
ENDPOINTS=(
    "https://$API_HOST/actuator/health"
    "https://store-api.maollar.com/actuator/health"
    "https://admin-api.maollar.com/actuator/health"
    "https://common-api.maollar.com/actuator/health"
)

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }

send_alert() {
    local msg="$1"
    log "🚨 ALERT: $msg"
    if [ -n "$ALERT_WEBHOOK" ]; then
        curl -s -o /dev/null -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\"*Lilishop Alert*: $msg\"}" "$ALERT_WEBHOOK" || log "Failed to send alert via webhook"
    fi
}

log "🌐 Starting Enhanced External Probe for $API_HOST family..."

FAILED=0
# 1. Check HTTP Endpoints
for url in "${ENDPOINTS[@]}"; do
    log "🔍 Checking $url..."
    
    # Get status and response content logic
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" || echo "000")
    
    if [ "$STATUS" -eq 200 ]; then
        log "✅ $url - OK (200)"
    elif [[ "$STATUS" == 5* || "$STATUS" == 4* || "$STATUS" == 000 ]]; then
        log "❌ $url - FAILED (Status: $STATUS)"
        send_alert "Endpoint Failure! $url returned HTTP $STATUS"
        FAILED=$((FAILED + 1))
    else
        log "⚠️ $url - Unexpected Status: $STATUS"
    fi
done

# 2. Check SSL Expiry (Alert if expiring in < 14 days)
log "🔒 Checking SSL Expiry..."
for host in api.maollar.com store-api.maollar.com admin-api.maollar.com common-api.maollar.com; do
    EXPIRE_DATE=$(echo | openssl s_client -servername "$host" -connect "$host:443" 2>/dev/null | openssl x509 -noout -enddate | cut -d= -f2 || echo "")
    if [ -n "$EXPIRE_DATE" ]; then
        # Convert to epoch
        if [[ "$OSTYPE" == "darwin"* ]]; then
            EXPIRE_SECS=$(date -j -f "%b %d %H:%M:%S %Y %Z" "$EXPIRE_DATE" "+%s")
        else
            EXPIRE_SECS=$(date -d "$EXPIRE_DATE" "+%s")
        fi
        NOW_SECS=$(date "+%s")
        DAYS_LEFT=$(( (EXPIRE_SECS - NOW_SECS) / 86400 ))
        log "Cert for $host expires in $DAYS_LEFT days."
        
        if [ "$DAYS_LEFT" -lt 14 ]; then
            send_alert "SSL Certificate for $host is expiring soon! ($DAYS_LEFT days left)"
        fi
    fi
done

if [ $FAILED -gt 0 ]; then
    log "🚨 Probe completed with $FAILED failures."
    exit 1
else
    log "🎉 Enhanced External Probe PASSED."
    exit 0
fi
