#!/usr/bin/env bash
set -euo pipefail

container="amnezia-socks5proxy"
upper=$(docker inspect -f '{{.GraphDriver.Data.UpperDir}}' "$container" 2>/dev/null || true)
[[ -n "${upper:-}" ]] || exit 0

log="$upper/usr/local/3proxy/logs/3proxy.log"
[[ -f "$log" ]] || exit 0

limit=$((50 * 1024 * 1024))
size=$(stat -c%s "$log" 2>/dev/null || echo 0)
[[ "$size" =~ ^[0-9]+$ ]] || exit 0

if (( size > limit )); then
  rm -f "$log.3.gz" || true
  [[ -f "$log.2.gz" ]] && mv -f "$log.2.gz" "$log.3.gz"
  [[ -f "$log.1.gz" ]] && mv -f "$log.1.gz" "$log.2.gz"
  cp "$log" "$log.1"
  gzip -f "$log.1"
  : > "$log"
fi
