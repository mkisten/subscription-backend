#!/usr/bin/env bash
set -euo pipefail

STATE_DIR=/var/lib/service-health-monitor
mkdir -p "$STATE_DIR"

ENV_FILE=/opt/subscription-backend/.env
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

BOT_TOKEN=$(grep '^TELEGRAM_BOT_TOKEN=' "$ENV_FILE" | head -n1 | cut -d= -f2-)
CHAT_ID=$(grep '^ADMIN_CHAT_ID=' "$ENV_FILE" | head -n1 | cut -d= -f2-)
if [[ -z ${BOT_TOKEN:-} || -z ${CHAT_ID:-} ]]; then
  echo "Missing TELEGRAM_BOT_TOKEN or ADMIN_CHAT_ID" >&2
  exit 1
fi

extract_redsocks() {
  local key=$1
  awk -F'=' -v wanted="$key" '
    {
      gsub(/\r/, "", $0)
      gsub(/^[ \t]+|[ \t]+$/, "", $1)
      gsub(/^[ \t]+|[ \t]+$/, "", $2)
      gsub(/;/, "", $2)
      gsub(/"/, "", $2)
      if ($1 == wanted) {
        print $2
        exit
      }
    }
  ' /etc/redsocks.conf
}

PROXY_IP=$(extract_redsocks ip)
PROXY_PORT=$(extract_redsocks port)
PROXY_LOGIN=$(extract_redsocks login)
PROXY_PASSWORD=$(extract_redsocks password)
if [[ -z ${PROXY_IP:-} || -z ${PROXY_PORT:-} || -z ${PROXY_LOGIN:-} || -z ${PROXY_PASSWORD:-} ]]; then
  echo "Failed to parse SOCKS proxy settings from /etc/redsocks.conf" >&2
  exit 1
fi

send_telegram() {
  local text=$1
  local proxy_url="socks5h://${PROXY_LOGIN}:${PROXY_PASSWORD}@${PROXY_IP}:${PROXY_PORT}"
  curl --silent --show-error --fail --max-time 20 \
    --proxy "$proxy_url" \
    -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" \
    -d "chat_id=${CHAT_ID}" \
    --data-urlencode "text=${text}" \
    -d disable_web_page_preview=true >/dev/null
}

report_state() {
  local key=$1
  local current=$2
  local detail=$3
  local state_file="$STATE_DIR/${key}.state"

  if [[ ! -f "$state_file" ]]; then
    printf '%s\n' "$current" > "$state_file"
    return
  fi

  local previous
  previous=$(cat "$state_file" 2>/dev/null || echo unknown)
  if [[ "$previous" != "$current" ]]; then
    local host message
    host=$(hostname)
    if [[ "$current" == "UP" ]]; then
      message="[RECOVERED][$host] $key - $detail"
    else
      message="[DOWN][$host] $key - $detail"
    fi
    send_telegram "$message"
    printf '%s\n' "$current" > "$state_file"
  fi
}

check_http() {
  local url=$1
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$url" || true)
  [[ "$code" == "200" ]]
}

check_http_json_contains() {
  local url=$1
  local needle=$2
  local body
  body=$(curl -s --max-time 20 "$url" || true)
  [[ -n "$body" && "$body" == *"$needle"* ]]
}

check_docker_health() {
  local container=$1
  local running health
  running=$(docker inspect -f '{{.State.Status}}' "$container" 2>/dev/null || true)
  health=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || true)
  [[ "$running" == "running" && ( "$health" == "healthy" || "$health" == "none" ) ]]
}

check_systemd_active() {
  local unit=$1
  [[ "$(systemctl is-active "$unit" 2>/dev/null || true)" == "active" ]]
}

check_container_http_code() {
  local container=$1
  local url=$2
  local code
  code=$(docker exec "$container" curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$url" 2>/dev/null || true)
  [[ "$code" =~ ^(200|204|301|302|307|308)$ ]]
}

check_graylog_gelf_listener() {
  docker exec graylog sh -lc "awk 'NR>1 && \$2 ~ /:2FA9$/ {found=1} END{exit found?0:1}' /proc/net/udp /proc/net/udp6" >/dev/null 2>&1
}

run_check() {
  local key=$1
  local ok_detail=$2
  local down_detail=$3
  shift 3
  if "$@"; then
    report_state "$key" UP "$ok_detail"
  else
    report_state "$key" DOWN "$down_detail"
  fi
}

run_check 'subscription_backend' 'HTTP 200 on :8080/actuator/health' 'No HTTP 200 on :8080/actuator/health' \
  check_http 'http://127.0.0.1:8080/actuator/health'

run_check 'subscription_backend_telegram' 'Telegram reachable from subscription container' 'Telegram is not reachable from subscription container' \
  check_container_http_code 'subscription_backend' 'https://api.telegram.org'

run_check 'vacancy_backend' 'HTTP 200 on :8081/api/actuator/health' 'No HTTP 200 on :8081/api/actuator/health' \
  check_http 'http://127.0.0.1:8081/api/actuator/health'

run_check 'hh_parser_backend' 'HTTP 200 on :8084/api/actuator/health' 'No HTTP 200 on :8084/api/actuator/health' \
  check_http 'http://127.0.0.1:8084/api/actuator/health'

run_check 'hh_parser_backend_search' 'Parser returns vacancy JSON' 'Parser does not return vacancy JSON' \
  check_http_json_contains 'http://127.0.0.1:8084/api/vacancies?text=java&area=113&page=0&per_page=1&search_field=name' '"items"'

run_check 'graylog' 'HTTP 200 on :9000/api/' 'No HTTP 200 on :9000/api/' \
  check_http 'http://127.0.0.1:9000/api/'

run_check 'graylog_gelf_input' 'Graylog GELF UDP input is listening on 12201/udp' 'Graylog GELF UDP input is not listening on 12201/udp' \
  check_graylog_gelf_listener

run_check 'shopping_backend' 'Docker container running and healthy' 'Docker container is not running/healthy' \
  check_docker_health 'shopping_backend'

run_check 'shopping_backend_telegram' 'Telegram reachable from shopping container' 'Telegram is not reachable from shopping container' \
  check_container_http_code 'shopping_backend' 'https://api.telegram.org'

run_check 'family-backend.service' 'systemd unit is active' 'systemd unit is not active' \
  check_systemd_active 'family-backend.service'

run_check 'redsocks' 'systemd unit is active' 'systemd unit is not active' \
  check_systemd_active 'redsocks'

run_check 'subscription-telegram-proxy.timer' 'systemd timer is active' 'systemd timer is not active' \
  check_systemd_active 'subscription-telegram-proxy.timer'