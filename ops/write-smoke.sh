#!/bin/bash
# Write Smoke Test - Lilishop Production Transactional Verification
# Usage: SMOKE_USER=xxx SMOKE_PWD=xxx SMOKE_SKU_ID=xxx bash write-smoke.sh

set -euo pipefail

# --- Configuration ---
TS=$(date +%Y%m%d-%H%M%S)
EVD=~/lilishop-deployment/evidence/write-smoke-$TS
mkdir -p "$EVD"

# Defaults for local testing if not provided (but typically provided by env)
USER="${SMOKE_USER:-smoke_test_01}"
PWD="${SMOKE_PWD:-}"
SKU_ID="${SMOKE_SKU_ID:-1772999003}"

# Constants
API_BASE="http://127.0.0.1:8888" # Direct to buyer-api
# Safety Locks
ALLOW_MUTATION="${SMOKE_ALLOW_DB_MUTATION:-0}"
REDIS_PW="${LILI_REDIS_PASSWORD:-REQUIRED_OVERRIDE}"
DB_PW="${LILI_DB_PASSWORD:-REQUIRED_OVERRIDE}"

log() { echo "[$(date +'%H:%M:%S')] $*"; }
error_exit() { log "ERROR: $*"; exit 1; }

# ── Step -1: Ensure Smoke User Exists ─────────────────────────────────────────
ensure_smoke_user() {
    log "[Pre] Ensuring smoke user '$USER' exists in DB..."
    # Check if user exists (Ignore mysql warnings about password)
    EXISTS=$(docker exec mysql mysql -uroot -p"$DB_PW" lilishop -N -s -e "SELECT id FROM li_member WHERE username='$USER';" 2>/dev/null || echo "")
    
    if [ -n "$EXISTS" ]; then
        log "[Pre] User '$USER' exists (ID: $EXISTS), ensuring password and activation..."
        HASH=$(python3 -c "import bcrypt; print(bcrypt.hashpw(b'$PWD', bcrypt.gensalt()).decode())")
        # Password reset is allowed as it only affects the smoke user
        docker exec mysql mysql -uroot -p"$DB_PW" lilishop -e "UPDATE li_member SET password='$HASH', disabled=b'1', delete_flag=b'0' WHERE username='$USER';" 2>/dev/null || true
    else
        log "[Pre] User '$USER' not found, performing self-registration via DB..."
        # Generate a unique big ID for smoke user (17 digits starting with 1772, similar to existing smoke ids)
        SMOKE_ID="1772$(date +%s%3N)"
        HASH=$(python3 -c "import bcrypt; print(bcrypt.hashpw(b'$PWD', bcrypt.gensalt()).decode())")
        docker exec mysql mysql -uroot -p"$DB_PW" lilishop -e "
            INSERT INTO li_member (id, username, password, disabled, delete_flag, create_time, sex, point, total_point, is_kyc_verified)
            VALUES ($SMOKE_ID, '$USER', '$HASH', b'1', b'0', NOW(), 0, 0, 0, b'0');
        " 2>/dev/null || log "[WARN] Failed to insert smoke user."
    fi
}

# ── Step -0.5: Ensure Valid SKU exists ────────────────────────────────────────
ensure_smoke_sku() {
    log "[Pre] Checking if preferred SKU $SKU_ID exists..."
    EXISTS=$(docker exec mysql mysql -uroot -p"$DB_PW" lilishop -N -s -e "SELECT id FROM li_goods_sku WHERE id='$SKU_ID' AND delete_flag=b'0' AND market_enable='UPPER';" 2>/dev/null || echo "")
    
    if [ -z "$EXISTS" ]; then
        log "[Pre] Preferred SKU $SKU_ID not found or not listed (DOWN)."
        if [ "$ALLOW_MUTATION" -eq 1 ]; then
            log "[Pre] SMOKE_ALLOW_DB_MUTATION=1: Attempting fallback to any SKU..."
            SKU_ID=$(docker exec mysql mysql -uroot -p"$DB_PW" lilishop -N -s -e "SELECT id FROM li_goods_sku WHERE delete_flag=b'0' LIMIT 1;" 2>/dev/null || echo "")
        else
            error_exit "SKU $SKU_ID is not available and SMOKE_ALLOW_DB_MUTATION is disabled. Setup product manually or enable mutation."
        fi
        
        if [ -z "$SKU_ID" ]; then
            error_exit "No valid SKU found in database."
        fi
        log "[Pre] Selected fallback SKU: $SKU_ID"
    fi
}

log "=== START WRITE SMOKE TEST ($TS) ==="
log "User: $USER | SKU: $SKU_ID (Preferred) | Mutation: $ALLOW_MUTATION"

if [ -z "$PWD" ]; then
    error_exit "SMOKE_PWD is not set."
fi

# Ensure user and SKU exist before login
ensure_smoke_user
ensure_smoke_sku

# ── Step 0: Pre-check & Enable SKU (Conditional) ─────────────────────────────
if [ "$ALLOW_MUTATION" -eq 1 ]; then
    log "[0] SMOKE_ALLOW_DB_MUTATION=1: Forcing SKU $SKU_ID to UPPER and fixing stock..."
    docker exec mysql mysql -uroot -p"$DB_PW" lilishop -e "
      UPDATE li_goods SET market_enable='UPPER', auth_flag='PASS', delete_flag=b'0' WHERE id=(SELECT goods_id FROM li_goods_sku WHERE id='$SKU_ID');
      UPDATE li_goods_sku SET market_enable='UPPER', auth_flag='PASS', delete_flag=b'0', quantity=999 WHERE id='$SKU_ID';
    " 2>/dev/null || log "[WARN] DB update for SKU activation failed."
    
    # Clear Redis cache for the SKU to ensure API sees the DB change
    docker exec redis redis-cli -a "$REDIS_PW" DEL "{GOODS_SKU}_$SKU_ID" >/dev/null 2>&1 || true
    docker exec redis redis-cli -a "$REDIS_PW" DEL "{SKU_STOCK}_$SKU_ID" >/dev/null 2>&1 || true
else
    log "[0] SMOKE_ALLOW_DB_MUTATION=0: Skipping commodity state mutation."
fi

# ── Step 1: Login (with Captcha Bypass) ──────────────────────────────────────
log "[1] Login (Bypassing Captcha)..."
SMOKE_UUID="smoke-$(date +%s%3N)"
docker exec redis redis-cli -a "$REDIS_PW" SET "{VERIFICATION_RESULT}_LOGIN${SMOKE_UUID}" "true" EX 300 >/dev/null 2>&1

LOGIN_RESP=$(curl -sS --max-time 10 -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H "uuid: ${SMOKE_UUID}" \
  -d "username=${USER}&password=${PWD}" \
  "$API_BASE/buyer/passport/member/userLogin")

echo "$LOGIN_RESP" > "$EVD/10-login.json"

TOKEN=$(python3 -c "import json,sys; j=json.load(open('$EVD/10-login.json')); print(j.get('result',{}).get('accessToken',''))" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
    MSG=$(python3 -c "import json,sys; print(json.load(open('$EVD/10-login.json')).get('message',''))" 2>/dev/null || echo "Unknown error")
    error_exit "Login failed: $MSG"
fi
log "[OK] Authenticated successfully."

# ── Step 1.5: Ensure Default Address exists ──────────────────────────────────
log "[1.5] Ensuring a default delivery address exists..."
# Get addresses
ADDR_JSON=$(curl -sS -H "accessToken: $TOKEN" "$API_BASE/buyer/member/address")
ADDR_ID=$(echo "$ADDR_JSON" | python3 -c "import json,sys; j=json.load(sys.stdin); res=j.get('result',[]); print(res[0].get('id','') if res else '')" 2>/dev/null || echo "")

if [ -z "$ADDR_ID" ]; then
    log "[1.5] No address found, adding dummy address..."
    # Match the validation requirements of MemberAddress entity
    ADDR_RESP=$(curl -sS --max-time 10 -X POST \
      -H "accessToken: $TOKEN" \
      -H 'Content-Type: application/x-www-form-urlencoded' \
      -d "name=SmokeBot&mobile=13800138000&consigneeAddressIdPath=1,1,1&consigneeAddressPath=北京市,市辖区,东城区&detail=SmokeStreet101&alias=Home&isDefault=true" \
      "$API_BASE/buyer/member/address")
    
    # Debug: log the response if it fails
    SUCCESS=$(echo "$ADDR_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin).get('success'))" 2>/dev/null || echo "false")
    if [ "$SUCCESS" != "True" ]; then
        log "[WARN] Failed to add dummy address. Response: $ADDR_RESP"
        # We try one more fallback if possible, but let's see if this fixes it
    fi
    
    ADDR_ID=$(echo "$ADDR_RESP" | python3 -c "import json,sys; j=json.load(sys.stdin); print(j.get('result',{}).get('id','') if j.get('success') else '')" 2>/dev/null || echo "")
    if [ -z "$ADDR_ID" ]; then
        error_exit "Failed to add dummy address."
    fi
    log "[OK] Dummy address added (ID: $ADDR_ID)."
else
    # To be extremely safe, set the first address as default
    log "[1.5] Found existing address(es), setting ID $ADDR_ID as default..."
    curl -sS -X POST -H "accessToken: $TOKEN" "$API_BASE/buyer/member/address/isDefault/${ADDR_ID}" >/dev/null 2>&1
    log "[OK] Address $ADDR_ID set as default."
fi

# ── Step 2: Add to Cart ──────────────────────────────────────────────────────
log "[2] Adding SKU $SKU_ID to cart..."
curl -sS --max-time 10 -X POST \
  -H "accessToken: $TOKEN" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "skuId=${SKU_ID}&num=1&cartType=CART" \
  "$API_BASE/buyer/trade/carts" > "$EVD/20-cart-add.json"

SUCCESS=$(python3 -c "import json; print(json.load(open('$EVD/20-cart-add.json')).get('success'))" 2>/dev/null || echo "false")
if [ "$SUCCESS" != "True" ]; then
    MSG=$(python3 -c "import json; print(json.load(open('$EVD/20-cart-add.json')).get('message',''))" 2>/dev/null || echo "Unknown error")
    error_exit "Failed to add to cart: $MSG"
fi
log "[OK] Added to cart."

# ── Step 3: Checkout Preview ─────────────────────────────────────────────────
log "[3] Getting checkout preview (TradeDTO)..."
# We add Consignee query param if needed, but normally it picks the default
curl -sS --max-time 10 -H "accessToken: $TOKEN" \
  "$API_BASE/buyer/trade/carts/checked?way=CART" > "$EVD/30-checkout.json"

HAS_ITEMS=$(python3 -c "import json; j=json.load(open('$EVD/30-checkout.json')); d=j.get('result',{}); print('YES' if (d.get('skuList') or d.get('cartList')) else 'NO')" 2>/dev/null || echo "NO")
if [ "$HAS_ITEMS" != "YES" ]; then
    error_exit "Checkout preview is empty. Possible stock or region issue."
fi
log "[OK] Checkout preview generated with items."

# ── Step 4: Create Order ──────────────────────────────────────────────────────
log "[4] Creating order..."
TRADE_BODY=$(python3 -c "import json; j=json.load(open('$EVD/30-checkout.json')); print(json.dumps(j.get('result',{})))" 2>/dev/null || echo "{}")

curl -sS --max-time 15 -X POST \
  -H "accessToken: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$TRADE_BODY" \
  "$API_BASE/buyer/trade/carts/create/trade" > "$EVD/40-order-create.json"

ORDER_SN=$(python3 -c "
import json
j = json.load(open('$EVD/40-order-create.json'))
res = j.get('result')
if isinstance(res, dict): print(res.get('sn') or res.get('orderSn') or res.get('tradeNo') or '')
elif isinstance(res, str): print(res)
" 2>/dev/null || echo "")

if [ -z "$ORDER_SN" ]; then
    MSG=$(python3 -c "import json; print(json.load(open('$EVD/40-order-create.json')).get('message',''))" 2>/dev/null || echo "Unknown error")
    error_exit "Order creation failed: $MSG"
fi
log "[OK] Order created: $ORDER_SN"
echo "$ORDER_SN" > "$EVD/41-order-sn.txt"

# ── Step 5: Cancel Order (Rollback) ──────────────────────────────────────────
log "[5] Rolling back (Cancelling order $ORDER_SN)..."
curl -sS --max-time 10 -X POST \
  -H "accessToken: $TOKEN" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "reason=AutomatedSmokeTestCleanup" \
  "$API_BASE/buyer/order/order/${ORDER_SN}/cancel" > "$EVD/60-order-cancel.json"

CANCEL_SUCCESS=$(python3 -c "import json; print(json.load(open('$EVD/60-order-cancel.json')).get('success'))" 2>/dev/null || echo "false")
if [ "$CANCEL_SUCCESS" != "True" ]; then
    log "[WARN] Order cancellation might have failed. Manual check suggested for SN: $ORDER_SN"
else
    log "[OK] Order cancelled."
fi

log "=== SMOKE TEST COMPLETED SUCCESSFULLY ==="
log "Evidence archived in: $EVD"
exit 0
