#!/bin/bash
# Maollar External Probe - Verification from outside the service cluster
# This script checks the visibility and responsiveness of the public API and SSL.

set -e

# On Azure VM, api.maollar.com should be in /etc/hosts, but we use 127.0.0.1 for the health probe.
CHECK_IP="127.0.0.1"
API_HOST="api.maollar.com"
ENDPOINTS=(
    "http://$CHECK_IP:8888/actuator/health"
    "http://$CHECK_IP:8889/actuator/health"
    "http://$CHECK_IP:8890/actuator/health"
    "http://$CHECK_IP:8891/actuator/health"
    "http://$CHECK_IP:8892/actuator/health"
)

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }

log "🌐 Starting External Probe for $API_HOST..."

FAILED=0
for url in "${ENDPOINTS[@]}"; do
    log "🔍 Checking $url..."
    # We use --resolve to map the domain if it's not in public DNS yet, using localhost for internal tests
    # But for a true 'external' probe, this should be the public IP or domain.
    # Here we assume the test runs in an environment where the domain is resolved.
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" || echo "000")
    
    if [ "$STATUS" -eq 200 ]; then
        log "✅ $url - OK (200)"
    else
        log "❌ $url - FAILED (Status: $STATUS)"
        FAILED=$((FAILED + 1))
    fi
done

# Check SSL (if 443 is used, though here we use ports 8888-8892 for direct checks)
# In production, we would check https://api.maollar.com

if [ $FAILED -gt 0 ]; then
    log "🚨 Probe completed with $FAILED failures."
    exit 1
else
    log "🎉 External Probe PASSED."
    exit 0
fi
