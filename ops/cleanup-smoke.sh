#!/bin/bash
# Cleanup Smoke Test Data from DB and Redis
# Usage: bash cleanup-smoke.sh

set -euo pipefail

# Constants
REDIS_PW="lilishop"

log() { echo "[$(date +'%H:%M:%S')] $*"; }

log "=== SMOKE CLEANUP START ==="

# ── DB Cleanup ───────────────────────────────────────────────────────────────
log "[1] Cleanup DB (Member, Goods, SKU, Address)..."
# We clean up by common smoke patterns to ensure any leftovers are also gone
docker exec mysql mysql -uroot -plilishop lilishop -e "
  DELETE FROM li_member_address WHERE member_id LIKE '1772%' OR name='SmokeBot';
  DELETE FROM li_member WHERE username='smoke_test_01' OR username LIKE 'smoke_1772%';
" 2>/dev/null || log "[WARN] DB delete operation might have failed or tables already clean."

# ── Redis Cleanup ───────────────────────────────────────────────────────────
log "[2] Cleanup Redis Keys..."
# Cleanup keys pattern-based (Cart, Token related)
SMOKE_KEYS=$(docker exec redis redis-cli -a "$REDIS_PW" KEYS "*{VERIFICATION_RESULT}_LOGINsmoke-*" 2>/dev/null || echo "")
if [ -n "$SMOKE_KEYS" ]; then
    echo "$SMOKE_KEYS" | xargs -r docker exec redis redis-cli -a "$REDIS_PW" DEL >/dev/null 2>&1 || true
fi

log "[OK] Redis cleaned."

log "=== SMOKE CLEANUP COMPLETED ==="
exit 0
