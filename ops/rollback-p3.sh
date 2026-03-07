#!/bin/bash
# Maollar P3 Rollback Script
# This script reverts the 'current' symlink to the previous version and restarts services.

set -e

DIST_DIR="$HOME/lilishop-deployment/backend/dist"
RELEASES_DIR="$DIST_DIR/releases"
SYMLINK_PATH="$DIST_DIR/current"

log() { echo "[$(date +'%H:%M:%S')] $*"; }

log "🎬 Starting P3 Rollback..."

# 1. Identify current and previous versions
CURRENT_TARGET=$(readlink -f "$SYMLINK_PATH")
PREVIOUS_RELEASE=$(ls -td "$RELEASES_DIR"/*/ | sed -n '2p' | sed 's/\/$//')

if [ -z "$PREVIOUS_RELEASE" ]; then
    echo "❌ Error: Previous release not found in $RELEASES_DIR."
    exit 1
fi

PREV_NAME=$(basename "$PREVIOUS_RELEASE")

log "◀️ Rolling back from $(basename "$CURRENT_TARGET") to $PREV_NAME"

# 2. Update symlink
ln -sfn "$PREVIOUS_RELEASE" "$SYMLINK_PATH"
log "🔗 Symlink updated: current -> $PREV_NAME"

# 3. Restart services
log "🔄 Restarting lili services..."
sudo systemctl restart 'lili-*'

log "✅ Rollback complete. Using version: $PREV_NAME"
sudo systemctl status 'lili-*' --no-pager
