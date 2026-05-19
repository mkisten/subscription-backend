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
TELEGRAM_ROUTE_RECOVERY_COOLDOWN_SECONDS=$(clean_value "${TELEGRAM_ROUTE_RECOVERY_COOLDOWN_SECONDS:-180}")

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
    subscription_backend_telegram_auth) echo 'Сервис подписок: создание Telegram auth session' ;;
    vacancy_backend) echo 'Сервис вакансий' ;;
    vacancy_backend_auth_token) echo 'Сервис вакансий: получение auth token' ;;
    vacancy_backend_unsent_queue) echo 'Сервис вакансий: очередь неотправленных' ;;
    hh_parser_backend) echo 'Парсер вакансий HH' ;;
    hh_parser_backend_search) echo 'Парсер вакансий HH: поиск' ;;
    superjob_parser_backend) echo 'Парсер вакансий SuperJob' ;;
    superjob_parser_backend_search) echo 'Парсер вакансий SuperJob: поиск' ;;
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

try_repair_subscription_telegram_route() {
  local stamp_file="$STATE_DIR/subscription_telegram_route_repair.timestamp"
  local now last
  now=$(date +%s)
  last=0
  if [[ -f "$stamp_file" ]]; then
    last=$(cat "$stamp_file" 2>/dev/null || echo 0)
  fi
  if (( now - last < TELEGRAM_ROUTE_RECOVERY_COOLDOWN_SECONDS )); then
    return 0
  fi
  printf '%s\n' "$now" > "$stamp_file"
  systemctl restart redsocks
  systemctl start subscription-telegram-proxy.service
  sleep 3
}

check_subscription_backend_telegram_raw() {
  check_container_http_code 'subscription_backend' 'https://api.telegram.org'
}

check_subscription_backend_telegram() {
  if check_subscription_backend_telegram_raw; then
    return 0
  fi
  try_repair_subscription_telegram_route
  check_subscription_backend_telegram_raw
}

check_subscription_telegram_auth_create_session_raw() {
  local body session_id
  body=$(curl -s --fail --max-time 20 -X POST 'http://127.0.0.1:8080/api/telegram-auth/create-session' \
    -H 'Content-Type: application/json' \
    -d '{"deviceId":"monitor-auth-check","serviceCode":"VACANCY"}' || true)
  [[ -n "$body" ]] || return 1

  session_id=$(BODY="$body" python3 - <<'PY'
import json, os, sys
try:
    body = json.loads(os.environ["BODY"])
except Exception:
    sys.exit(1)
if body.get("status") != "PENDING":
    sys.exit(1)
if not body.get("sessionId") or not body.get("authLink"):
    sys.exit(1)
print(body["sessionId"])
PY
  ) || return 1

  docker exec -e PGPASSWORD=postgres subscription_postgres \
    psql -U postgres -d subscription_db \
    -c "delete from auth_sessions where session_id = '${session_id}' or device_id = 'monitor-auth-check'" >/dev/null 2>&1 || true

  return 0
}

check_subscription_telegram_auth_create_session() {
  if check_subscription_telegram_auth_create_session_raw; then
    return 0
  fi
  try_repair_subscription_telegram_route
  check_subscription_telegram_auth_create_session_raw
}

check_vacancy_auth_token() {
  local telegram_id body
  telegram_id=$(docker exec vacancy_postgres psql -U postgres -d vacancy_service -tAc "select telegram_id from user_settings where auto_update_enabled=true order by coalesce(next_run_at, now()) asc limit 1" 2>/dev/null | tr -d '[:space:]')
  [[ -n "${telegram_id:-}" ]] || return 1
  body=$(docker exec vacancy_backend curl -s --fail --max-time 20 "https://api.subscriptionhhapp.ru/api/auth/token?telegramId=${telegram_id}" 2>/dev/null || true)
  [[ -n "$body" && "$body" == *'"token":'* ]]
}

check_vacancy_unsent_backlog() {
  local count
  count=$(docker exec vacancy_postgres psql -U postgres -d vacancy_service -tAc "select count(*) from vacancies v join user_settings us on us.telegram_id = v.user_telegram_id where v.sent_to_telegram = false and us.telegram_notify = true and v.loaded_at < now() - interval '20 minutes'" 2>/dev/null | tr -d '[:space:]')
  [[ -n "${count:-}" ]] || return 1
  [[ "$count" =~ ^[0-9]+$ ]] || return 1
  (( count == 0 ))
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

run_check_threshold() {
  local key=$1
  local threshold=$2
  local ok_detail=$3
  local down_detail=$4
  shift 4

  local fail_file="$STATE_DIR/${key}.failcount"
  local fail_count=0
  if [[ -f "$fail_file" ]]; then
    fail_count=$(cat "$fail_file" 2>/dev/null || echo 0)
  fi

  if "$@"; then
    printf '0\n' > "$fail_file"
    report_state "$key" UP "$ok_detail"
  else
    fail_count=$((fail_count + 1))
    printf '%s\n' "$fail_count" > "$fail_file"
    if (( fail_count >= threshold )); then
      report_state "$key" DOWN "$down_detail Сбоев подряд: $fail_count."
    fi
  fi
}

run_check 'subscription_backend' 'HTTP health-эндпоинт отвечает кодом 200 на порту 8080.' 'HTTP health-эндпоинт не отвечает кодом 200 на порту 8080.' \
  check_http 'http://127.0.0.1:8080/actuator/health'

run_check_threshold 'subscription_backend_telegram' 2 'Telegram доступен из контейнера сервиса подписок.' 'Telegram недоступен из контейнера сервиса подписок.' \
  check_subscription_backend_telegram

run_check_threshold 'subscription_backend_telegram_auth' 2 'Создание Telegram auth session работает.' 'Сервис подписок не может создать Telegram auth session.' \
  check_subscription_telegram_auth_create_session

run_check 'vacancy_backend' 'HTTP health-эндпоинт отвечает кодом 200 на порту 8081.' 'HTTP health-эндпоинт не отвечает кодом 200 на порту 8081.' \
  check_http 'http://127.0.0.1:8081/api/actuator/health'

run_check_threshold 'vacancy_backend_auth_token' 2 'Сервис вакансий получает auth token через сервис подписок.' 'Сервис вакансий не может получить auth token через сервис подписок.' \
  check_vacancy_auth_token

run_check_threshold 'vacancy_backend_unsent_queue' 2 'У сервиса вакансий нет зависшего хвоста неотправленных вакансий старше 20 минут.' 'У сервиса вакансий накапливается хвост неотправленных вакансий старше 20 минут.' \
  check_vacancy_unsent_backlog

run_check 'hh_parser_backend' 'HTTP health-эндпоинт отвечает кодом 200 на порту 8084.' 'HTTP health-эндпоинт не отвечает кодом 200 на порту 8084.' \
  check_http 'http://127.0.0.1:8084/api/actuator/health'

run_check 'hh_parser_backend_search' 'Парсер возвращает JSON с вакансиями.' 'Парсер не возвращает JSON с вакансиями.' \
  check_http_json_contains 'http://127.0.0.1:8084/api/vacancies?text=java&area=113&page=0&per_page=1&search_field=name' '"items"'

run_check 'superjob_parser_backend' 'HTTP health-эндпоинт отвечает кодом 200 на порту 8087.' 'HTTP health-эндпоинт не отвечает кодом 200 на порту 8087.' \
  check_http 'http://127.0.0.1:8087/api/actuator/health'

run_check 'superjob_parser_backend_search' 'Парсер SuperJob возвращает JSON с вакансиями.' 'Парсер SuperJob не возвращает JSON с вакансиями.' \
  check_http_json_contains 'http://127.0.0.1:8087/api/vacancies?text=java&country=russia&town=4&page=0&per_page=1' '"items"'

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
