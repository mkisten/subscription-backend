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

clean_value() {
  printf '%s' "$1" | tr -d '\r' | sed 's/^ *//;s/ *$//'
}

SMTP_HOST=$(clean_value "${SMTP_HOST:-mail.hosting.reg.ru}")
SMTP_PORT=$(clean_value "${SMTP_PORT:-465}")
SMTP_USER=$(clean_value "${SMTP_USER:-}")
SMTP_PASSWORD=$(clean_value "${SMTP_PASSWORD:-}")
EMAIL_FROM=$(clean_value "${EMAIL_FROM:-${SMTP_USER}}")
EMAIL_TO=$(printf '%s' "${EMAIL_TO:-${EMAIL_FROM}}" | tr -d '\r')

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
    subscription_backend) echo 'Сервис подписок' ;;
    subscription_backend_telegram) echo 'Сервис подписок: доступ к Telegram' ;;
    vacancy_backend) echo 'Сервис вакансий' ;;
    hh_parser_backend) echo 'Парсер вакансий HH' ;;
    hh_parser_backend_search) echo 'Парсер вакансий HH: поиск' ;;
    graylog) echo 'Graylog' ;;
    graylog_gelf_input) echo 'Graylog: GELF input' ;;
    shopping_backend) echo 'Сервис покупок' ;;
    shopping_backend_telegram) echo 'Сервис покупок: доступ к Telegram' ;;
    family-backend.service) echo 'Family backend' ;;
    redsocks) echo 'Прокси redsocks' ;;
    subscription-telegram-proxy.timer) echo 'Таймер проксирования Telegram для сервиса подписок' ;;
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

run_check 'subscription_backend' 'HTTP health-эндпоинт отвечает кодом 200 на порту 8080.' 'HTTP health-эндпоинт не отвечает кодом 200 на порту 8080.' \
  check_http 'http://127.0.0.1:8080/actuator/health'

run_check 'subscription_backend_telegram' 'Telegram доступен из контейнера сервиса подписок.' 'Telegram недоступен из контейнера сервиса подписок.' \
  check_container_http_code 'subscription_backend' 'https://api.telegram.org'

run_check 'vacancy_backend' 'HTTP health-эндпоинт отвечает кодом 200 на порту 8081.' 'HTTP health-эндпоинт не отвечает кодом 200 на порту 8081.' \
  check_http 'http://127.0.0.1:8081/api/actuator/health'

run_check 'hh_parser_backend' 'HTTP health-эндпоинт отвечает кодом 200 на порту 8084.' 'HTTP health-эндпоинт не отвечает кодом 200 на порту 8084.' \
  check_http 'http://127.0.0.1:8084/api/actuator/health'

run_check 'hh_parser_backend_search' 'Парсер возвращает JSON с вакансиями.' 'Парсер не возвращает JSON с вакансиями.' \
  check_http_json_contains 'http://127.0.0.1:8084/api/vacancies?text=java&area=113&page=0&per_page=1&search_field=name' '"items"'

run_check 'graylog' 'HTTP API Graylog отвечает кодом 200.' 'HTTP API Graylog не отвечает кодом 200.' \
  check_http 'http://127.0.0.1:9000/api/'

run_check 'graylog_gelf_input' 'GELF UDP input слушает порт 12201.' 'GELF UDP input не слушает порт 12201.' \
  check_graylog_gelf_listener

run_check 'shopping_backend' 'Docker-контейнер запущен и healthy.' 'Docker-контейнер не запущен или не healthy.' \
  check_docker_health 'shopping_backend'

run_check 'shopping_backend_telegram' 'Telegram доступен из контейнера сервиса покупок.' 'Telegram недоступен из контейнера сервиса покупок.' \
  check_container_http_code 'shopping_backend' 'https://api.telegram.org'

run_check 'family-backend.service' 'systemd-юнит активен.' 'systemd-юнит неактивен.' \
  check_systemd_active 'family-backend.service'

run_check 'redsocks' 'systemd-юнит активен.' 'systemd-юнит неактивен.' \
  check_systemd_active 'redsocks'

run_check 'subscription-telegram-proxy.timer' 'systemd-таймер активен.' 'systemd-таймер неактивен.' \
  check_systemd_active 'subscription-telegram-proxy.timer'