#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# ops-factory unified service orchestrator
#
# Usage: ./ctl.sh <action> [options] [component ...]
#
#   action:    startup | shutdown | status | restart
#   component: onlyoffice | langfuse | gateway | knowledge | business-intelligence | exporter | control-center | webapp | all (default)
#              Multiple components can be specified.
#   options:
#     --apipwd <value>   Set GATEWAY_API_PASSWORD for gateway and child tools (default: empty)
#
# Examples:
#   ./ctl.sh startup                    # start all services
#   ./ctl.sh startup gateway webapp     # start gateway and webapp only
#   ./ctl.sh shutdown webapp            # stop webapp only
#   ./ctl.sh status                     # check all services
#   ./ctl.sh restart gateway            # restart gateway
#   ./ctl.sh shutdown gateway exporter  # stop gateway and exporter
#
# Service toggles (env vars):
#   ENABLE_ONLYOFFICE=false ./ctl.sh startup   # skip OnlyOffice
#   ENABLE_LANGFUSE=false   ./ctl.sh startup   # skip Langfuse
#   ENABLE_EXPORTER=false   ./ctl.sh startup   # skip Exporter
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "${SCRIPT_DIR}")"

# === Service toggles (optional services, set to false to skip) ===
ENABLE_ONLYOFFICE="${ENABLE_ONLYOFFICE:-true}"
ENABLE_LANGFUSE="${ENABLE_LANGFUSE:-true}"
ENABLE_EXPORTER="${ENABLE_EXPORTER:-true}"
# all other services are mandatory — no toggles

# === Sub-script paths ===
CTL_GATEWAY="${ROOT_DIR}/gateway/scripts/ctl.sh"
CTL_KNOWLEDGE="${ROOT_DIR}/knowledge-service/scripts/ctl.sh"
CTL_BUSINESS_INTELLIGENCE="${ROOT_DIR}/business-intelligence/scripts/ctl.sh"
CTL_CONTROL_CENTER="${ROOT_DIR}/control-center/scripts/ctl.sh"
CTL_WEBAPP="${ROOT_DIR}/web-app/scripts/ctl.sh"
CTL_LANGFUSE="${ROOT_DIR}/langfuse/scripts/ctl.sh"
CTL_ONLYOFFICE="${ROOT_DIR}/onlyoffice/scripts/ctl.sh"
CTL_EXPORTER="${ROOT_DIR}/prometheus-exporter/scripts/ctl.sh"

# === Logging ===
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
log_fail()  { echo -e "${RED}[FAIL]${NC}  $1"; }

# === Helpers ===
component_name() {
    case "$1" in
        onlyoffice) echo "OnlyOffice" ;;
        langfuse) echo "Langfuse" ;;
        gateway) echo "Gateway" ;;
        knowledge) echo "Knowledge" ;;
        business-intelligence) echo "Business Intelligence" ;;
        exporter) echo "Exporter" ;;
        control-center) echo "Control Center" ;;
        webapp) echo "Webapp" ;;
    esac
}

is_optional_component() {
    case "$1" in
        onlyoffice|langfuse|exporter) return 0 ;;
        *) return 1 ;;
    esac
}

# === Component validation ===
VALID_COMPONENTS="onlyoffice langfuse gateway knowledge business-intelligence exporter control-center webapp"

validate_component() {
    local comp="$1"
    for valid in ${VALID_COMPONENTS}; do
        [[ "${comp}" == "${valid}" ]] && return 0
    done
    log_error "Unknown component: ${comp}"
    usage
}

# === Single-component action helpers ===
# Usage: startup_one <component> [--background]
startup_one() {
    local comp="$1"
    shift
    case "${comp}" in
        onlyoffice)
            if [ "${ENABLE_ONLYOFFICE}" != "true" ]; then
                log_info "OnlyOffice disabled (toggle=false)"
                return 0
            fi
            "${CTL_ONLYOFFICE}" startup "$@"
            ;;
        langfuse)
            if [ "${ENABLE_LANGFUSE}" != "true" ]; then
                log_info "Langfuse disabled (toggle=false)"
                return 0
            fi
            "${CTL_LANGFUSE}" startup "$@"
            ;;
        gateway)    "${CTL_GATEWAY}" startup "$@" ;;
        knowledge)  "${CTL_KNOWLEDGE}" startup "$@" ;;
        business-intelligence) "${CTL_BUSINESS_INTELLIGENCE}" startup "$@" ;;
        exporter)
            if [ "${ENABLE_EXPORTER}" != "true" ]; then
                log_info "Exporter disabled (toggle=false)"
                return 0
            fi
            "${CTL_EXPORTER}" startup "$@"
            ;;
        control-center) "${CTL_CONTROL_CENTER}" startup "$@" ;;
        webapp)     "${CTL_WEBAPP}" startup "$@" ;;
    esac
}

startup_with_policy() {
    local comp="$1"
    shift

    if startup_one "${comp}" "$@"; then
        return 0
    fi

    local name
    name="$(component_name "${comp}")"
    if is_optional_component "${comp}"; then
        log_warn "${name} failed to start, continuing because it is optional"
        return 0
    fi

    log_error "${name} failed to start, aborting because it is mandatory"
    return 1
}

shutdown_one() {
    case "$1" in
        onlyoffice) "${CTL_ONLYOFFICE}" shutdown ;;
        langfuse)   "${CTL_LANGFUSE}" shutdown ;;
        gateway)    "${CTL_GATEWAY}" shutdown ;;
        knowledge)  "${CTL_KNOWLEDGE}" shutdown ;;
        business-intelligence) "${CTL_BUSINESS_INTELLIGENCE}" shutdown ;;
        exporter)   "${CTL_EXPORTER}" shutdown ;;
        control-center) "${CTL_CONTROL_CENTER}" shutdown ;;
        webapp)     "${CTL_WEBAPP}" shutdown ;;
    esac
}

status_one() {
    case "$1" in
        onlyoffice)
            if [ "${ENABLE_ONLYOFFICE}" = "true" ]; then
                "${CTL_ONLYOFFICE}" status || return 1
            fi ;;
        langfuse)
            if [ "${ENABLE_LANGFUSE}" = "true" ]; then
                "${CTL_LANGFUSE}" status || return 1
            fi ;;
        gateway)  "${CTL_GATEWAY}" status  || return 1 ;;
        knowledge) "${CTL_KNOWLEDGE}" status || return 1 ;;
        business-intelligence)
            "${CTL_BUSINESS_INTELLIGENCE}" status || return 1 ;;
        exporter)
            if [ "${ENABLE_EXPORTER}" = "true" ]; then
                "${CTL_EXPORTER}" status || return 1
            fi ;;
        control-center) "${CTL_CONTROL_CENTER}" status || return 1 ;;
        webapp)   "${CTL_WEBAPP}" status   || return 1 ;;
    esac
}

# === Orchestration ===
do_startup() {
    local components=("$@")

    if [[ ${#components[@]} -eq 0 || "${components[0]}" == "all" ]]; then
        # Shutdown everything first
        do_shutdown all

        log_info "Starting all services in background..."

        # 1. OnlyOffice (optional)
        startup_with_policy onlyoffice

        # 2. Langfuse (optional)
        startup_with_policy langfuse

        # 3. Gateway (mandatory, background)
        startup_with_policy gateway --background

        # 4. Knowledge-service (mandatory, background)
        startup_with_policy knowledge --background

        # 5. Business Intelligence (mandatory, background)
        startup_with_policy business-intelligence --background

        # 6. Exporter (optional, background)
        startup_with_policy exporter --background

        # 7. Control Center (mandatory, background)
        startup_with_policy control-center --background

        # 8. Webapp (mandatory, background)
        startup_with_policy webapp --background
    else
        for comp in "${components[@]}"; do
            validate_component "${comp}"
        done
        # Shutdown selected components first
        for comp in "${components[@]}"; do
            shutdown_one "${comp}"
        done
        log_info "Starting in background: ${components[*]}..."
        for comp in "${components[@]}"; do
            startup_with_policy "${comp}" --background || return 1
        done
    fi
}

do_shutdown() {
    local components=("$@")

    if [[ ${#components[@]} -eq 0 || "${components[0]}" == "all" ]]; then
        "${CTL_EXPORTER}" shutdown
        "${CTL_CONTROL_CENTER}" shutdown
        "${CTL_BUSINESS_INTELLIGENCE}" shutdown
        "${CTL_KNOWLEDGE}" shutdown
        "${CTL_WEBAPP}" shutdown
        "${CTL_GATEWAY}" shutdown
        "${CTL_LANGFUSE}" shutdown
        "${CTL_ONLYOFFICE}" shutdown
        log_info "All services stopped"
    else
        for comp in "${components[@]}"; do
            validate_component "${comp}"
        done
        for comp in "${components[@]}"; do
            shutdown_one "${comp}"
        done
        log_info "Stopped: ${components[*]}"
    fi
}

do_status() {
    local components=("$@")
    local has_fail=0

    echo "Service status:"
    echo "--------------"

    if [[ ${#components[@]} -eq 0 || "${components[0]}" == "all" ]]; then
        status_one onlyoffice || has_fail=1
        status_one langfuse   || has_fail=1
        status_one gateway    || has_fail=1
        status_one knowledge  || has_fail=1
        status_one business-intelligence || has_fail=1
        status_one exporter   || has_fail=1
        status_one control-center || has_fail=1
        status_one webapp     || has_fail=1
        echo
        if [ "${has_fail}" -eq 0 ]; then
            log_ok "All services are up"
        else
            log_fail "One or more services are down"
        fi
    else
        for comp in "${components[@]}"; do
            validate_component "${comp}"
        done
        for comp in "${components[@]}"; do
            status_one "${comp}" || has_fail=1
        done
    fi

    return "${has_fail}"
}

do_restart() {
    local components=("$@")
    do_shutdown "${components[@]}"
    do_startup "${components[@]}"
}

# === Usage & Main ===
usage() {
    cat <<'EOF'
Usage: ctl.sh <action> [options] [component ...]

Actions:
  startup     Start service(s)
  shutdown    Stop service(s)
  status      Check service status
  restart     Restart service(s)

Components (multiple allowed):
  all         All services (default)
  onlyoffice  OnlyOffice Document Server (Docker)     [optional]
  langfuse    Langfuse observability platform (Docker) [optional]
  gateway     Gateway + goosed agents                  [mandatory]
  knowledge   Knowledge ingestion / retrieval service  [mandatory]
  business-intelligence  Business intelligence service [mandatory]
  exporter    Prometheus metrics exporter              [optional]
  control-center  Control Center service               [mandatory]
  webapp      Web application (Vite dev server)        [mandatory]

Examples:
  ctl.sh startup                    Start all services
  ctl.sh startup gateway webapp     Start gateway and webapp only
  ctl.sh shutdown gateway exporter  Stop gateway and exporter
  ctl.sh status webapp              Check webapp status

Service toggles (env vars):
  ENABLE_ONLYOFFICE=true|false  (default: true)
  ENABLE_LANGFUSE=true|false    (default: true)
  ENABLE_EXPORTER=true|false    (default: true)

Options:
  --apipwd <value>   Set GATEWAY_API_PASSWORD (default: empty)
EOF
    exit 1
}

ACTION="${1:-}"
[ -z "${ACTION}" ] && usage
shift

GATEWAY_API_PASSWORD="${GATEWAY_API_PASSWORD:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --apipwd)
            shift
            if [[ $# -eq 0 ]]; then
                log_error "--apipwd requires a value"
                usage
            fi
            GATEWAY_API_PASSWORD="$1"
            shift
            ;;
        --apipwd=*)
            GATEWAY_API_PASSWORD="${1#*=}"
            shift
            ;;
        --)
            shift
            break
            ;;
        -*)
            log_error "Unknown option: $1"
            usage
            ;;
        *)
            break
            ;;
    esac
done

export GATEWAY_API_PASSWORD

# Remaining args are components (default: all)
if [[ $# -eq 0 ]]; then
    COMPONENTS=("all")
else
    COMPONENTS=("$@")
fi

case "${ACTION}" in
    startup)  do_startup  "${COMPONENTS[@]}" ;;
    shutdown) do_shutdown "${COMPONENTS[@]}" ;;
    status)   do_status   "${COMPONENTS[@]}" ;;
    restart)  do_restart  "${COMPONENTS[@]}" ;;
    -h|--help|help) usage ;;
    *) log_error "Unknown action: ${ACTION}"; usage ;;
esac
