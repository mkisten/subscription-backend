#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="subscription_backend"
CHAIN="SUB_TG_PROXY"
TG_HOST="api.telegram.org"
REDSOCKS_PORT="12345"
WAIT_SECONDS="${WAIT_SECONDS:-180}"

resolve_container_net() {
  CONTAINER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$CONTAINER_NAME" 2>/dev/null || true)
  GATEWAY_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.Gateway}}{{end}}' "$CONTAINER_NAME" 2>/dev/null || true)
  [[ -n "${CONTAINER_IP:-}" && -n "${GATEWAY_IP:-}" ]]
}

DEADLINE=$((SECONDS + WAIT_SECONDS))
until resolve_container_net; do
  if (( SECONDS >= DEADLINE )); then
    echo "Cannot resolve container IP/gateway for $CONTAINER_NAME after ${WAIT_SECONDS}s" >&2
    exit 1
  fi
  sleep 2
done

TG_IPS=$(getent ahostsv4 "$TG_HOST" | awk '{print $1}' | sort -u)
if [[ -z "${TG_IPS:-}" ]]; then
  echo "No IPv4 resolved for $TG_HOST" >&2
  exit 1
fi

iptables -t nat -N "$CHAIN" 2>/dev/null || true
iptables -t nat -F "$CHAIN"
for ip in $TG_IPS; do
  iptables -t nat -A "$CHAIN" -d "$ip"/32 -p tcp --dport 443 -j DNAT --to-destination "$GATEWAY_IP:$REDSOCKS_PORT"
done

for num in $(iptables -t nat -L PREROUTING --line-numbers -n | awk '/SUB_TG_PROXY/ {print $1}' | sort -rn); do
  iptables -t nat -D PREROUTING "$num"
done

iptables -t nat -I PREROUTING 1 -s "$CONTAINER_IP"/32 -p tcp -j "$CHAIN"

systemctl is-active redsocks >/dev/null || systemctl restart redsocks
