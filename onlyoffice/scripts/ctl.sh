#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# OnlyOffice Document Server control (Docker Compose)
#
# Usage: ./ctl.sh <action>
#   action: startup | shutdown | status | restart
#
# Configuration priority: env var > config.yaml > default
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(dirname "${SCRIPT_DIR}")"
ROOT_DIR="$(dirname "${SERVICE_DIR}")"

# --- Configuration ---
CONTAINER_NAME="onlyoffice"
COMPOSE_FILE="${SERVICE_DIR}/docker-compose.yml"

# --- Logging ---
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
log_fail()  { echo -e "${RED}[FAIL]${NC}  $1"; }

# --- Generate .env from config.yaml ---
generate_env_file() {
    local config_file="${SERVICE_DIR}/config.yaml"
    local env_file="${SERVICE_DIR}/.env"

    # Use node to parse YAML and generate .env file
    node -e "
      const fs = require('fs');
      const configPath = '${config_file}';
      if (!fs.existsSync(configPath)) { process.exit(0); }
      let yaml;
      try { yaml = require('yaml'); } catch {
        try { yaml = require('${ROOT_DIR}/gateway/node_modules/yaml'); } catch {
          try { yaml = require('${ROOT_DIR}/prometheus-exporter/node_modules/yaml'); } catch {
            process.exit(0);
          }
        }
      }
      const cfg = yaml.parse(fs.readFileSync(configPath, 'utf-8')) || {};
      const lines = [];
      lines.push('ONLYOFFICE_PORT=' + (cfg.port || 8080));
      lines.push('JWT_ENABLED=' + (cfg.jwtEnabled ?? false));
      lines.push('PLUGINS_ENABLED=' + (cfg.pluginsEnabled ?? false));
      lines.push('ALLOW_PRIVATE_IP_ADDRESS=' + (cfg.allowPrivateIpAddress ?? true));
      lines.push('ALLOW_META_IP_ADDRESS=' + (cfg.allowMetaIpAddress ?? true));
      console.log(lines.join('\n'));
    " > "${env_file}" 2>/dev/null || true

    # Read ONLYOFFICE_PORT from generated .env (env var overrides)
    if [ -f "${env_file}" ]; then
        ONLYOFFICE_PORT="${ONLYOFFICE_PORT:-$(grep '^ONLYOFFICE_PORT=' "${env_file}" | cut -d= -f2)}"
    fi
    ONLYOFFICE_PORT="${ONLYOFFICE_PORT:-8080}"
}

# --- Utilities ---
wait_ready() {
    local attempts="${1:-120}" delay="${2:-1}"
    for ((i=1; i<=attempts; i++)); do
        if curl -fsS --connect-timeout 1 --max-time 2 "http://127.0.0.1:${ONLYOFFICE_PORT}/healthcheck" >/dev/null 2>&1 \
          || curl -fsS --connect-timeout 1 --max-time 2 "http://127.0.0.1:${ONLYOFFICE_PORT}/web-apps/apps/api/documents/api.js" >/dev/null 2>&1; then
            return 0
        fi
        (( i % 10 == 0 )) && log_info "Waiting for OnlyOffice readiness... (${i}/${attempts})"
        sleep "${delay}"
    done
    log_error "OnlyOffice readiness check failed"
    return 1
}

# --- OnlyOffice actions ---
do_startup() {
    generate_env_file

    if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_info "OnlyOffice already running"
    else
        log_info "Starting OnlyOffice (port ${ONLYOFFICE_PORT})..."
        docker compose -f "${COMPOSE_FILE}" up -d
    fi

    log_info "Checking OnlyOffice readiness (timeout: 120s)..."
    if ! wait_ready 120 1; then
        log_warn "Not ready; recreating..."
        docker compose -f "${COMPOSE_FILE}" down
        docker compose -f "${COMPOSE_FILE}" up -d
        log_info "Re-checking readiness (timeout: 120s)..."
        if ! wait_ready 120 1; then
            log_error "OnlyOffice not ready after recreate"
            return 1
        fi
    fi
    log_info "OnlyOffice readiness check passed"
}

do_shutdown() {
    if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_info "Stopping OnlyOffice..."
        docker compose -f "${COMPOSE_FILE}" down
    fi
}

do_status() {
    if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        local port="${ONLYOFFICE_PORT:-8080}"
        if curl -fsS "http://127.0.0.1:${port}/healthcheck" >/dev/null 2>&1 \
           || curl -fsS "http://127.0.0.1:${port}/web-apps/apps/api/documents/api.js" >/dev/null 2>&1; then
            log_ok "OnlyOffice running (http://localhost:${port})"
        else
            log_warn "OnlyOffice container running but readiness check failed"
            return 1
        fi
    else
        log_fail "OnlyOffice container is not running"
        return 1
    fi
}

do_restart() {
    do_shutdown
    do_startup
}

# --- Main ---
usage() {
    cat <<EOF
Usage: $(basename "$0") <action>

Actions:
  startup     Start OnlyOffice Document Server (Docker Compose)
  shutdown    Stop OnlyOffice
  status      Check OnlyOffice status
  restart     Restart OnlyOffice
EOF
    exit 1
}

ACTION="${1:-}"
[ -z "${ACTION}" ] && usage

case "${ACTION}" in
    startup)  do_startup ;;
    shutdown) do_shutdown ;;
    status)   do_status ;;
    restart)  do_restart ;;
    -h|--help|help) usage ;;
    *) log_error "Unknown action: ${ACTION}"; usage ;;
esac
