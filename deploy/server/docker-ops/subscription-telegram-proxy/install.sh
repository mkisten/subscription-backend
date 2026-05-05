#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

install -m 0755 "$SRC_DIR/subscription-telegram-proxy.sh" /usr/local/sbin/subscription-telegram-proxy.sh
install -m 0644 "$SRC_DIR/subscription-telegram-proxy.service" /etc/systemd/system/subscription-telegram-proxy.service
install -m 0644 "$SRC_DIR/subscription-telegram-proxy.timer" /etc/systemd/system/subscription-telegram-proxy.timer

systemctl daemon-reload
systemctl enable --now subscription-telegram-proxy.timer
systemctl start subscription-telegram-proxy.service
systemctl status --no-pager subscription-telegram-proxy.timer
