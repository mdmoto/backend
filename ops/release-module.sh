#!/usr/bin/env bash

# Run on the production server after the intended source files have been reviewed and synced.
# Local/CI tests must pass before this script is used; production packaging skips legacy test sources.

set -euo pipefail

MODULE="${1:-}"
case "$MODULE" in
  buyer-api) PORT=8888 ;;
  seller-api) PORT=8889 ;;
  manager-api) PORT=8890 ;;
  common-api) PORT=8891 ;;
  consumer) PORT=8892 ;;
  *)
    echo "Usage: $0 {buyer-api|seller-api|manager-api|common-api|consumer}" >&2
    exit 2
    ;;
esac

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="$(dirname "$BACKEND_DIR")"
JAR="$BACKEND_DIR/$MODULE/target/$MODULE-4.3.jar"
LOG="$BACKEND_DIR/logs/$MODULE.log"
HEALTH_URL="http://127.0.0.1:$PORT/actuator/health"
RELEASE_ID="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="$DEPLOY_DIR/backups/releases/$RELEASE_ID-$MODULE"
JVM_OPTS=(-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200)
SPRING_OPTS=(--spring.profiles.active=prod --spring.boot.admin.client.enabled=false)

mkdir -p "$BACKUP_DIR" "$BACKEND_DIR/logs"
if [[ ! -f "$JAR" ]]; then
  echo "Existing JAR not found: $JAR" >&2
  exit 1
fi
cp "$JAR" "$BACKUP_DIR/$MODULE-4.3.jar.before"
sha256sum "$BACKUP_DIR/$MODULE-4.3.jar.before" > "$BACKUP_DIR/before.sha256"

set +u
set -a
[[ -f "$DEPLOY_DIR/.env" ]] && source "$DEPLOY_DIR/.env"
[[ -f "$BACKEND_DIR/.env" ]] && source "$BACKEND_DIR/.env"
set +a
set -u

start_service() {
  cd "$BACKEND_DIR"
  nohup java "${JVM_OPTS[@]}" -jar "$MODULE/target/$MODULE-4.3.jar" \
    --server.address=127.0.0.1 --server.port="$PORT" "${SPRING_OPTS[@]}" \
    > "$LOG" 2>&1 < /dev/null &
  echo $!
}

stop_service() {
  local pid
  pid="$(pgrep -f "^java .* -jar $MODULE/target/$MODULE-4.3.jar" || true)"
  [[ -z "$pid" ]] && return 0
  kill "$pid"
  for _ in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  kill -9 "$pid" 2>/dev/null || true
}

wait_for_health() {
  local pid="$1"
  local code
  for _ in $(seq 1 75); do
    code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 "$HEALTH_URL" 2>/dev/null || true)"
    [[ "$code" == "200" ]] && return 0
    kill -0 "$pid" 2>/dev/null || return 1
    sleep 2
  done
  return 1
}

rollback() {
  echo "Release failed; rolling back $MODULE" >&2
  stop_service
  cp "$BACKUP_DIR/$MODULE-4.3.jar.before" "$JAR"
  local rollback_pid
  rollback_pid="$(start_service)"
  if ! wait_for_health "$rollback_pid"; then
    echo "Rollback health check failed for $MODULE" >&2
    exit 1
  fi
  echo "Rollback completed for $MODULE"
  exit 1
}

cd "$BACKEND_DIR"
if ! ./mvnw -pl "$MODULE" -am -Dmaven.test.skip=true package; then
  cp "$BACKUP_DIR/$MODULE-4.3.jar.before" "$JAR"
  echo "Build failed; running service was not restarted and original JAR was restored" >&2
  exit 1
fi

sha256sum "$JAR" > "$BACKUP_DIR/after.sha256"
stop_service
NEW_PID="$(start_service)"
wait_for_health "$NEW_PID" || rollback

echo "Release completed: module=$MODULE pid=$NEW_PID backup=$BACKUP_DIR"
