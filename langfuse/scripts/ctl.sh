#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Langfuse service control (Docker Compose)
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
      const pg = cfg.postgres || {};
      const init = cfg.init || {};
      const lines = [];
      lines.push('LANGFUSE_PORT=' + (cfg.port || 3100));
      lines.push('POSTGRES_DB=' + (pg.db || 'langfuse'));
      lines.push('POSTGRES_USER=' + (pg.user || 'langfuse'));
      lines.push('POSTGRES_PASSWORD=' + (pg.password || 'langfuse'));
      lines.push('POSTGRES_PORT=' + (pg.port || 5432));
      lines.push('NEXTAUTH_SECRET=' + (cfg.nextauthSecret || 'opsfactory-langfuse-secret-key'));
      lines.push('SALT=' + (cfg.salt || 'opsfactory-langfuse-salt'));
      lines.push('TELEMETRY_ENABLED=' + (cfg.telemetryEnabled ?? false));
      lines.push('LANGFUSE_INIT_ORG_ID=' + (init.orgId || 'opsfactory'));
      lines.push('LANGFUSE_INIT_ORG_NAME=' + (init.orgName || 'ops-factory'));
      lines.push('LANGFUSE_INIT_PROJECT_ID=' + (init.projectId || 'opsfactory-agents'));
      lines.push('LANGFUSE_INIT_PROJECT_NAME=' + (init.projectName || 'ops-factory-agents'));
      lines.push('LANGFUSE_INIT_PROJECT_PUBLIC_KEY=' + (init.projectPublicKey || 'pk-lf-opsfactory'));
      lines.push('LANGFUSE_INIT_PROJECT_SECRET_KEY=' + (init.projectSecretKey || 'sk-lf-opsfactory'));
      lines.push('LANGFUSE_INIT_USER_EMAIL=' + (init.userEmail || 'admin@opsfactory.local'));
      lines.push('LANGFUSE_INIT_USER_NAME=' + (init.userName || 'admin'));
      lines.push('LANGFUSE_INIT_USER_PASSWORD=' + (init.userPassword || 'opsfactory'));
      console.log(lines.join('\n'));
    " > "${env_file}" 2>/dev/null || true

    # Read LANGFUSE_PORT from generated .env (env var overrides)
    if [ -f "${env_file}" ]; then
        LANGFUSE_PORT="${LANGFUSE_PORT:-$(grep '^LANGFUSE_PORT=' "${env_file}" | cut -d= -f2)}"
    fi
    LANGFUSE_PORT="${LANGFUSE_PORT:-3100}"
}

# --- Utilities ---
wait_http_ok() {
    local name="$1" url="$2" attempts="${3:-60}" delay="${4:-1}"
    for ((i=1; i<=attempts; i++)); do
        curl -fsS "${url}" >/dev/null 2>&1 && return 0
        sleep "${delay}"
    done
    log_error "${name} health check failed: ${url}"
    return 1
}

# --- Langfuse actions ---
do_startup() {
    generate_env_file

    if docker ps --format '{{.Names}}' | grep -q '^langfuse$'; then
        log_info "Langfuse already running"
    else
        log_info "Starting Langfuse (port ${LANGFUSE_PORT})..."
        docker compose -f "${COMPOSE_FILE}" up -d
    fi

    log_info "Checking Langfuse readiness (timeout: 60s)..."
    if ! wait_http_ok "Langfuse" "http://127.0.0.1:${LANGFUSE_PORT}/api/public/health" 60 1; then
        log_error "Langfuse health check failed"
        return 1
    fi
    log_info "Langfuse ready at http://localhost:${LANGFUSE_PORT}"
}

do_shutdown() {
    if docker ps --format '{{.Names}}' | grep -q '^langfuse$'; then
        log_info "Stopping Langfuse..."
        docker compose -f "${COMPOSE_FILE}" down
    fi
}

do_status() {
    local port="${LANGFUSE_PORT:-3100}"
    if docker ps --format '{{.Names}}' | grep -q '^langfuse$'; then
        if curl -fsS "http://127.0.0.1:${port}/api/public/health" >/dev/null 2>&1; then
            log_ok "Langfuse running (http://localhost:${port})"
        else
            log_warn "Langfuse container running but health check failed"
            return 1
        fi
    else
        log_fail "Langfuse is not running"
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
  startup     Start Langfuse (Docker Compose)
  shutdown    Stop Langfuse
  status      Check Langfuse status
  restart     Restart Langfuse
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
