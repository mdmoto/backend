#!/bin/bash
# Maollar Step 4: Systemd & Logrotate Deployment Script
# This script runs on Azure to set up persistence.

set -e

PROJECT_ROOT="$HOME/lilishop-deployment"
BACKEND_DIR="$PROJECT_ROOT/backend"
USER_NAME="azureuser"
JAVA_PATH=$(which java || echo "/usr/bin/java")

echo "⚙️ [Step 4] Starting Service Persistence & Self-healing Setup..."

# 1. Create Individual Systemd Service Files
declare -A services
services=(
    ["buyer-api"]="8888"
    ["seller-api"]="8889"
    ["manager-api"]="8890"
    ["common-api"]="8891"
    ["consumer"]="8892"
)

# JVM & Spring Options (Sync with auto-start-services.sh)
JVM_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
SPRING_OPTS="--spring.profiles.active=prod --spring.boot.admin.client.enabled=false"

DIST_DIR="$BACKEND_DIR/dist"
mkdir -p "$DIST_DIR"

for name in "${!services[@]}"; do
    port="${services[$name]}"
    SOURCE_JAR="$BACKEND_DIR/$name/target/$name-4.3.jar"
    STABLE_JAR="$DIST_DIR/$name.jar"
    
    if [ -f "$SOURCE_JAR" ]; then
        echo "📦 Deploying $name to stable path..."
        cp "$SOURCE_JAR" "$STABLE_JAR"
    fi
    
    echo "📄 Creating systemd unit for $name (Port: $port)..."
    
    cat <<EOF | sudo tee /etc/systemd/system/lili-$name.service > /dev/null
[Unit]
Description=Maollar $name Service
After=network.target mysql.service redis.service

[Service]
User=$USER_NAME
Group=$USER_NAME
WorkingDirectory=$BACKEND_DIR
EnvironmentFile=$BACKEND_DIR/.env
ExecStart=$JAVA_PATH $JVM_OPTS -jar $STABLE_JAR --server.address=127.0.0.1 --server.port=$port $SPRING_OPTS
Restart=always
RestartSec=10
StandardOutput=append:$BACKEND_DIR/logs/$name.log
StandardError=append:$BACKEND_DIR/logs/$name.log

[Install]
WantedBy=multi-user.target
EOF
done

# 2. Setup Logrotate
echo "⚙️ Configuring Logrotate for Maollar logs..."
cat <<EOF | sudo tee /etc/logrotate.d/lilishop > /dev/null
$BACKEND_DIR/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
    size 100M
}
EOF

# 3. Reload Systemd and Enable Services
echo "🔄 Reloading systemd and preparing for restart..."
sudo systemctl daemon-reload

# ⚠️ Aggressively stop services and kill residual processes to prevent 'Port already in use'
echo "🛑 Stopping all lili services and cleaning up orphans..."
sudo systemctl stop 'lili-*' 2>/dev/null || true
sudo pkill -9 -f 'buyer-api' || true
sudo pkill -9 -f 'seller-api' || true
sudo pkill -9 -f 'manager-api' || true
sudo pkill -9 -f 'common-api' || true
sudo pkill -9 -f 'consumer' || true
sudo pkill -9 -f 'lili-' || true
sleep 5

# Enable and start new services
for name in "${!services[@]}"; do
    echo "🚀 Starting lili-$name.service..."
    sudo systemctl enable lili-$name
    sudo systemctl restart lili-$name
done

echo "✅ [Step 4] Setup complete. Services are now managed by systemd."
echo "💡 Use 'sudo systemctl status lili-*' to check status."
