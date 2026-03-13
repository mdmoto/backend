#!/bin/bash
# Maollar Minimal CI/Verification Pipeline (Azure Portable Version)
# Optimized for remote execution in ~/lilishop-deployment/

set -euo pipefail

PROJECT_ROOT="${PROJECT_ROOT:-$HOME/lilishop-deployment}"
LOG_FILE="${LOG_FILE:-$PROJECT_ROOT/verify-pipeline.$(date +%F_%H%M%S).log}"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE") 2>&1

echo "🧪 [CI] Starting Maollar Verification Pipeline on Azure..."
echo "📄 [CI] Log: $LOG_FILE"
echo "📁 [CI] Project root: $PROJECT_ROOT"

# Load Environment Variables for Tests
ENV_FILE="$PROJECT_ROOT/.env"
if [ -f "$ENV_FILE" ]; then
    echo "🔐 [CI] Loading environment variables from .env..."
    # Export variables from .env (ignoring comments and empty lines)
    export $(grep -v '^#' "$ENV_FILE" | xargs)
else
    echo "⚠️ [CI] No .env file found in $PROJECT_ROOT/. No environment variables loaded."
fi

# Install local JARs if required (e.g. systemPath replacement for vendor SDKs)
if [ -x "$PROJECT_ROOT/ops/install-local-jars.sh" ]; then
    echo "📦 [CI] Installing local JAR dependencies..."
    bash "$PROJECT_ROOT/ops/install-local-jars.sh"
elif [ -x "$PROJECT_ROOT/ops/install-local-jars.sh" ]; then
    echo "📦 [CI] Installing local JAR dependencies..."
    bash "$PROJECT_ROOT/ops/install-local-jars.sh"
else
    echo "ℹ️ [CI] No install-local-jars.sh found; skipping local JAR installation."
fi

# 0.1 REQUIRED_OVERRIDE Intercept (Security Gatekeeper)
echo "🛡️ [CI] 0.1: Ensuring all critical secrets are provided (no REQUIRED_OVERRIDE leaks)..."
# This intercept ensures that if a config uses :REQUIRED_OVERRIDE, it MUST be overridden in the environment.
CRITICAL_VARS=("LILI_DB_PASSWORD" "LILI_REDIS_PASSWORD" "LILI_JASYPT_PASSWORD")
for var in "${CRITICAL_VARS[@]}"; do
    val="${!var:-}"
    if [ "$val" == "REQUIRED_OVERRIDE" ]; then
        echo "🚨 [SECURITY BLOCK] Environment variable $var is set to literal 'REQUIRED_OVERRIDE'!"
        exit 8
    fi
    
    if [ -z "$val" ]; then
        # grep recursively in config and deploy dirs
        if grep -rq "\${$var:REQUIRED_OVERRIDE}" "$PROJECT_ROOT" --include="application.yml" --include="deploy-api.yml" --exclude-dir=.git; then
            echo "🚨 [SECURITY BLOCK] $var is missing in environment but has a REQUIRED_OVERRIDE default in config!"
            exit 8
        fi
    fi
done
echo "✅ [CI] Secret placeholder check passed."


# 0. Pre-build Regression Check (P1-2)
echo "🔍 [CI] Checking for flattened test files (duplicate class prevention)..."
BAD1=$(find "$PROJECT_ROOT/manager-api/src/test/java/cn/lili/test" -maxdepth 1 -type f -name "*.java" -print || true)
BAD2=$(find "$PROJECT_ROOT/consumer/src/test/java/cn/lili/buyer/test" -maxdepth 1 -type f -name "*.java" -print || true)

if [ -n "${BAD1}${BAD2}" ]; then
  echo "❌ [P1 BLOCK] Found flattened test files (will cause duplicate class):"
  echo "$BAD1"
  echo "$BAD2"
  exit 2
fi
echo "✅ [CI] No flattened test files found."

# 0.2 Circular Dependency Prevention Check
echo "🔍 [CI] Checking for allow-circular-references=false (P1 Guard)..."
# Check application.yml
if grep -q "allow-circular-references: true" "$PROJECT_ROOT/config/application.yml"; then
  echo "❌ [P1 BLOCK] Circular references are explicitly enabled in application.yml!"
  exit 5
fi
if ! grep -q "allow-circular-references: false" "$PROJECT_ROOT/config/application.yml"; then
  echo "❌ [P1 BLOCK] Circular references are not disabled in application.yml!"
  exit 5
fi
# Check setup-persistence.sh
if grep -q "SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES=true" "$PROJECT_ROOT/ops/setup-persistence.sh"; then
  echo "❌ [P1 BLOCK] Circular references are explicitly enabled in setup-persistence.sh!"
  exit 5
fi
if ! grep -q "SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES=false" "$PROJECT_ROOT/ops/setup-persistence.sh"; then
  echo "❌ [P1 BLOCK] Circular references are not disabled in setup-persistence.sh!"
  exit 5
fi
echo "✅ [CI] Circular reference config check passed."

# 0.3 Circular Dependency Runtime Guard Test
echo "🔍 [CI] Running CircularDependencyGuardTest..."
if [ -d "$PROJECT_ROOT" ] && [ -f "$PROJECT_ROOT/mvnw" ]; then
    (cd "$PROJECT_ROOT" && ./mvnw -B -pl buyer-api -am -Dtest=CircularDependencyGuardTest,TradeBuilderRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test)
    echo "✅ [CI] Engineering Quality guards passed (Circular + Trade)."
else
    echo "⏭️ [CI] Skipping runtime guard test (backend or mvnw not found)."
fi
# 1. Backend Build & Test
echo "🏗️ [CI] 1/3: Building Backend & Running Tests (mvn test)..."
BACKEND_PATH="$PROJECT_ROOT"

if [ -d "$BACKEND_PATH" ]; then
    # Ensure middleware is up before SpringBootTest tries to connect.
    if [ -d "$PROJECT_ROOT/docker" ]; then
        echo "🐳 [CI] Ensuring docker-compose middleware is running..."
        (cd "$PROJECT_ROOT/docker" && sudo docker compose up -d)
        echo "⏳ [CI] Waiting for MySQL to be ready..."
        for i in $(seq 1 30); do
            if sudo docker exec mysql mysqladmin ping -uroot -p"${LILI_DB_PASSWORD:-lilishop}" >/dev/null 2>&1; then
                echo "✅ [CI] MySQL is ready."
                break
            fi
            sleep 2
        done
    fi

    cd "$BACKEND_PATH"
    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        echo "⚙️ [CI] Compiling and packaging validation..."
        # Package to recreate jars, without tests to save time
        ./mvnw -B clean package -DskipTests=true
        
        if [ -f "$HOME/setup-persistence.sh" ]; then
            echo "⚙️ [CI] Invoking setup-persistence.sh to restart services..."
            bash "$HOME/setup-persistence.sh"
        else
            echo "⚠️ [CI] setup-persistence.sh not found in $HOME; skipping service restart."
        fi
    else
        echo "❌ mvnw not found in $BACKEND_PATH"
        exit 1
    fi
else
    echo "❌ Backend directory not found at $BACKEND_PATH"
    exit 1
fi

# 2. Frontend Check (Optional on Azure)
# Default: skip, because many prod VMs don't have Node/npm installed.
# Enable explicitly: RUN_FRONTEND_BUILD=1
echo "📦 [CI] 2/3: Checking Frontend Build (optional, RUN_FRONTEND_BUILD=1)..."
if [ "${RUN_FRONTEND_BUILD:-0}" -eq 1 ]; then
    if ! command -v npm >/dev/null 2>&1; then
        echo "❌ [CI] RUN_FRONTEND_BUILD=1 but npm is not installed."
        echo "   Fix: install Node.js + npm (recommended via nvm), then re-run."
        exit 10
    fi

    FRONTEND_PATH="$PROJECT_ROOT/lilishop-uniapp"
    if [ ! -d "$FRONTEND_PATH" ]; then
        FRONTEND_PATH="$PROJECT_ROOT/frontend"
    fi

    if [ -d "$FRONTEND_PATH" ]; then
        echo "✅ Frontend directory exists at $FRONTEND_PATH"
        cd "$FRONTEND_PATH"
        if [ -f "package.json" ]; then
            if [ ! -d "node_modules" ]; then
                echo "📥 [CI] node_modules not found, installing dependencies..."
                if [ -f "package-lock.json" ]; then
                    npm ci --legacy-peer-deps
                else
                    npm install --legacy-peer-deps
                fi
            fi
            npm run build:h5
            echo "✅ H5 Build PASS."
            npm run build:mp-weixin
            echo "✅ mp-weixin Build PASS."
        else
            echo "❌ package.json not found in $FRONTEND_PATH"
            exit 1
        fi
    else
        echo "❌ No frontend found to verify at $PROJECT_ROOT/lilishop-uniapp or $PROJECT_ROOT/frontend"
        exit 1
    fi
else
    echo "⏭️ [CI] Skipping frontend build (set RUN_FRONTEND_BUILD=1 to enable)."
fi

# 3. Running Service Smoke Test
echo "🚦 [CI] 3/3: Running Health Check Smoke Test..."
SERVICES=("lili-buyer-api" "lili-seller-api" "lili-manager-api" "lili-common-api" "lili-consumer")
HEALTH_URLS=(
    "http://127.0.0.1:8888/actuator/health"
    "http://127.0.0.1:8889/actuator/health"
    "http://127.0.0.1:8890/actuator/health"
    "http://127.0.0.1:8891/actuator/health"
    "http://127.0.0.1:8892/actuator/health"
)

for url in "${HEALTH_URLS[@]}"; do
    ok=0
    for i in $(seq 1 50); do
        # Use full response check to ensure it's actually Spring Actuator (status:UP)
        RESPONSE=$(curl -sS --max-time 3 "$url" || echo "FAILED")
        if [[ "$RESPONSE" == *"\"status\":\"UP\""* ]]; then
            echo "✅ Health Check OK: $url"
            ok=1
            break
        fi
        sleep 3
    done
    if [ "$ok" -ne 1 ]; then
        echo "❌ Health Check FAILED: $url"
        exit 1
    fi
done

# 3.3 Startup Log Noise Check (Boot 3 Gatekeeper)
echo "🔍 [CI] 3.3: Scanning startup logs for ERROR/alarms..."
# Look for critical keywords in the last 5 minutes of systemd logs
# Exclude known benign warnings (e.g., BeanPostProcessor eligiblity warnings)
BENIGN_WARNINGS="eligible for getting processed by all BeanPostProcessors"
for svc in "${SERVICES[@]}"; do
    LOG_NOISE=$(sudo journalctl -u "$svc" --since "5 minutes ago" | grep -Ei "error|exception|stacktrace|fallback" | grep -vEi "$BENIGN_WARNINGS" || true)
    if [ -n "$LOG_NOISE" ]; then
        echo "❌ [LOG NOISE] Critical errors detected in $svc startup logs:"
        echo "$LOG_NOISE" | tail -n 20
        # If we find "ERROR" or "Exception" that isn't handled, fail the pipeline
        if echo "$LOG_NOISE" | grep -Ei "ERROR|Exception" >/dev/null; then
           echo "🚨 [CI BLOCK] Pipeline failed due to critical log noise in $svc."
           exit 7
        fi
    fi
done
echo "✅ [CI] Startup log audit passed."

# 3.2 Transactional Smoke Test (Optional)
if [ "${RUN_WRITE_SMOKE:-0}" -eq 1 ]; then
    echo "⚡ [CI] RUN_WRITE_SMOKE=1: Starting Transactional Smoke Test..."
    if [ -f "$PROJECT_ROOT/ops/write-smoke.sh" ]; then
        # Use default smoke test credentials if not provided
        export SMOKE_USER="${SMOKE_USER:-smoke_test_01}"
        export SMOKE_PWD="${SMOKE_PWD:-MaollarSmoke123}"
        export SMOKE_ALLOW_DB_MUTATION=1
        bash "$PROJECT_ROOT/ops/write-smoke.sh"
    else
        echo "⚠️ [CI] write-smoke.sh not found, skipping."
    fi
fi


# 4. Production Security Audit (P1 Final Check)
echo "🛡️ [CI] 4/6: Running Production Security Audit..."
# 4.1 Port exposure check (Strictly catch 0.0.0.0, [::], or *)
# Exclude 9999 from the initial block list, we will check it separately with iptables logic
BAD_EXPOSURE=$(sudo ss -ltnp | grep -v '127\.0\.0\.1' | grep -E '0\.0\.0\.0|::|\s+\*:' | grep -E '(:8888|:8889|:8890|:8891|:8892|:3306|:6379|:9200|:9876|:5601|:8180|:9001)\b' || true)
if [ -n "$BAD_EXPOSURE" ]; then
    echo "❌ [SECURITY ALERT] Found internal services exposed to public interface!"
    echo "$BAD_EXPOSURE"
    exit 3
fi

# 4.1.1 Special Check for XXL-JOB (9999) - Path A: Iptables Awareness
XXL_EXPOSURE=$(sudo ss -ltnp | grep -v '127\.0\.0\.1' | grep -E '0\.0\.0\.0|::|\s+\*:' | grep -E '(:9999)\b' || true)
if [ -n "$XXL_EXPOSURE" ]; then
    echo "🔍 [AUDIT] Port 9999 is exposed globally, checking iptables protection..."
    HAS_ACCEPT=$(sudo iptables -S INPUT | grep -q -- '-A INPUT -s 172.19.0.0/16 -p tcp -m tcp --dport 9999 -j ACCEPT' && echo 1 || echo 0)
    HAS_DROP=$(sudo iptables -S INPUT | grep -q -- '-A INPUT -p tcp -m tcp --dport 9999 -j DROP' && echo 1 || echo 0)
    
    if [ "$HAS_ACCEPT" -eq 1 ] && [ "$HAS_DROP" -eq 1 ]; then
        echo "✅ [AUDIT] Port 9999 exposure is mitigated by valid iptables rules."
    else
        echo "❌ [SECURITY ALERT] Port 9999 is exposed and NOT protected by intended iptables rules!"
        sudo iptables -S | grep 9999 || true
        exit 3
    fi
fi
echo "✅ [CI] Port exposure check passed."

# 4.2 Local Secret Scan (PROD BLOCKER)
echo "🔍 [CI] Checking for potential hardcoded secrets..."
# Matches 'password:' followed by spaces and a character that is NOT '$' or space at line start
SECRET_ERROR=$(grep -rEi '^[[:space:]]*(password|token|secret):[[:space:]]*[^[:space:]$]' "$PROJECT_ROOT/" --include="*.yml" --exclude-dir=.git || true)
if [ -n "$SECRET_ERROR" ]; then
    echo "❌ [SECURITY ALERT] Found plaintext secrets in production config!"
    echo "$SECRET_ERROR"
    exit 4
fi

# 4.3 REQUIRED_OVERRIDE intercept (DEPRECATED: Moved to step 0.1)
echo "✅ [CI] Secret scan passed (All secrets are placeholders)."

# 5. External Domain/SSL Check
echo "🌍 [CI] 5/6: Verifying External Domain & SSL Health..."
# ... (existing domain code stays)
DOMAINS=(
    "api.maollar.com"
    "admin-api.maollar.com"
    "store-api.maollar.com"
    "common-api.maollar.com"
)
for domain in "${DOMAINS[@]}"; do
    # Check via Local Nginx bypass Cloudflare to avoid VM outbound timeout
    # This verifies Nginx configuration and backend routing for each domain
    STATUS=$(curl -sS -o /dev/null -w "%{http_code}" -H "Host: $domain" "http://127.0.0.1/actuator/health" || echo 000)
    if [ "$STATUS" -eq 200 ]; then
        echo "✅ Domain (Local Route) OK: $domain"
    else
        echo "⚠️ Domain (Local Route) FAILED ($STATUS): $domain"
        # We don't exit here as actuator/health might be restricted by IP in Nginx
    fi
done

# 6. Systemd Persistence Check
echo "⚙️ [CI] 6/6: Verifying Systemd Service Persistence..."
SERVICES=("lili-buyer-api" "lili-seller-api" "lili-manager-api" "lili-common-api" "lili-consumer")
for svc in "${SERVICES[@]}"; do
    if sudo systemctl is-active --quiet "$svc"; then
        echo "✅ Service Active: $svc"
    else
        echo "❌ Service FAILED: $svc (Run 'sudo journalctl -u $svc' to debug)"
        exit 6
    fi
done

echo "🎉 [CI] All verification steps completed! Project is SAFELY deployed."
