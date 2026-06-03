#!/usr/bin/env bash
set -euo pipefail

STATE_DIR=/var/lib/librechat-disk-monitor
mkdir -p "$STATE_DIR"

clean_value() {
  printf '%s' "$1" | tr -d '\r' | sed 's/^ *//;s/ *$//'
}

BOT_TOKEN=$(clean_value "${BOT_TOKEN:-}")
CHAT_ID=$(clean_value "${CHAT_ID:-}")
SMTP_HOST=$(clean_value "${SMTP_HOST:-mail.hosting.reg.ru}")
SMTP_PORT=$(clean_value "${SMTP_PORT:-465}")
SMTP_USER=$(clean_value "${SMTP_USER:-}")
SMTP_PASSWORD=$(clean_value "${SMTP_PASSWORD:-}")
EMAIL_FROM=$(clean_value "${EMAIL_FROM:-${SMTP_USER}}")
EMAIL_TO=$(printf '%s' "${EMAIL_TO:-${EMAIL_FROM}}" | tr -d '\r')
LIBRECHAT_HEALTH_URL=$(clean_value "${LIBRECHAT_HEALTH_URL:-http://127.0.0.1:3080/health}")
ENABLE_ANTHROPIC_CHECK=$(clean_value "${ENABLE_ANTHROPIC_CHECK:-false}")
ANTHROPIC_API_URL=$(clean_value "${ANTHROPIC_API_URL:-https://api.anthropic.com/v1/models}")
ANTHROPIC_API_KEY=$(clean_value "${ANTHROPIC_API_KEY:-}")

send_telegram() {
  local text=$1
  [[ -n "${BOT_TOKEN:-}" && -n "${CHAT_ID:-}" ]]
  curl --silent --show-error --fail --max-time 20 \
    -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" \
    -d "chat_id=${CHAT_ID}" \
    --data-urlencode "text=${text}" \
    -d disable_web_page_preview=true >/dev/null
}

send_email() {
  local subject=$1
  local body=$2
  [[ -n ${SMTP_USER:-} && -n ${SMTP_PASSWORD:-} && -n ${EMAIL_FROM:-} && -n ${EMAIL_TO:-} ]]

  local header_to
  local mail_file
  local -a rcpt_args=()

  header_to=$(printf '%s' "$EMAIL_TO" | tr ',' '\n' | while IFS= read -r rcpt; do rcpt=$(clean_value "$rcpt"); [[ -n "$rcpt" ]] && printf '%s\n' "$rcpt"; done | paste -sd ', ' -)
  while IFS= read -r rcpt; do
    rcpt=$(clean_value "$rcpt")
    [[ -n "$rcpt" ]] && rcpt_args+=(--mail-rcpt "$rcpt")
  done < <(printf '%s' "$EMAIL_TO" | tr ',' '\n')

  mail_file=$(mktemp)
  cat > "$mail_file" <<EOF
From: ${EMAIL_FROM}
To: ${header_to}
Subject: ${subject}
MIME-Version: 1.0
Content-Type: text/plain; charset=UTF-8
Content-Transfer-Encoding: 8bit

${body}
EOF

  curl --silent --show-error --fail --ssl-reqd --max-time 30 \
    --url "smtps://${SMTP_HOST}:${SMTP_PORT}" \
    --user "${SMTP_USER}:${SMTP_PASSWORD}" \
    --mail-from "${EMAIL_FROM}" \
    ${rcpt_args[@]} \
    --upload-file "$mail_file" >/dev/null
  rm -f "$mail_file"
}

send_notification() {
  local subject=$1
  local body=$2
  local ok=1

  if send_telegram "$body"; then
    ok=0
  fi

  if send_email "$subject" "$body"; then
    ok=0
  fi

  return $ok
}

human_service_name() {
  case "$1" in
    disk_usage_warn) echo 'Диск сервера: предупреждение' ;;
    disk_usage_crit) echo 'Диск сервера: критический уровень' ;;
    librechat_http) echo 'LibreChat: HTTP health' ;;
    anthropic_api) echo 'Anthropic API' ;;
    *) echo "$1" ;;
  esac
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
    local host service_name status_text subject message
    host=$(hostname)
    service_name=$(human_service_name "$key")
    if [[ "$current" == "UP" ]]; then
      status_text='Восстановлено'
    else
      status_text='Проблема'
    fi
    subject="[$host] $status_text: $service_name"
    message=$(cat <<EOF
$status_text
Сервер: $host
Сервис: $service_name
Детали: $detail
Предыдущее состояние: $previous
Текущее состояние: $current
EOF
)
    send_notification "$subject" "$message"
    printf '%s\n' "$current" > "$state_file"
  fi
}

get_root_disk_usage_percent() {
  df -P / | awk 'NR==2 {gsub(/%/, "", $5); print $5}'
}

check_disk_usage_threshold() {
  local threshold=$1
  local usage
  usage=$(get_root_disk_usage_percent 2>/dev/null || true)
  [[ -n "${usage:-}" ]] || return 1
  [[ "$usage" =~ ^[0-9]+$ ]] || return 1
  (( usage < threshold ))
}

check_http_code() {
  local url=$1
  local expected=$2
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$url" || true)
  [[ "$code" == "$expected" ]]
}

check_anthropic_api() {
  [[ "${ENABLE_ANTHROPIC_CHECK}" == "true" ]] || return 0
  [[ -n "${ANTHROPIC_API_KEY:-}" ]] || return 1

  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 \
    "$ANTHROPIC_API_URL" \
    -H "x-api-key: ${ANTHROPIC_API_KEY}" \
    -H 'anthropic-version: 2023-06-01' || true)

  [[ "$code" == "200" ]]
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

run_check 'disk_usage_warn' 'Корневой диск сервера заполнен меньше чем на 85%.' 'Корневой диск сервера заполнен на 85% или больше.' \
  check_disk_usage_threshold 85

run_check 'disk_usage_crit' 'Корневой диск сервера заполнен меньше чем на 92%.' 'Корневой диск сервера заполнен на 92% или больше.' \
  check_disk_usage_threshold 92

run_check 'librechat_http' 'HTTP health LibreChat отвечает кодом 200.' 'HTTP health LibreChat не отвечает кодом 200.' \
  check_http_code "$LIBRECHAT_HEALTH_URL" 200

if [[ "${ENABLE_ANTHROPIC_CHECK}" == "true" ]]; then
  run_check 'anthropic_api' 'Anthropic API отвечает кодом 200 по ключу сервера.' 'Anthropic API не отвечает кодом 200. Возможны неверный ключ, блокировка по egress/IP или ошибка провайдера.' \
    check_anthropic_api
fi
