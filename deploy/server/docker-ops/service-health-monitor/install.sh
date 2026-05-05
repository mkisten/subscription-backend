#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

install -m 0755 "$SRC_DIR/service-health-monitor.sh" /usr/local/sbin/service-health-monitor.sh
install -m 0644 "$SRC_DIR/service-health-monitor.service" /etc/systemd/system/service-health-monitor.service
install -m 0644 "$SRC_DIR/service-health-monitor.timer" /etc/systemd/system/service-health-monitor.timer
install -d -m 0755 /var/lib/service-health-monitor
if [[ -f "$SRC_DIR/service-health-monitor.env" ]]; then
  install -m 0600 "$SRC_DIR/service-health-monitor.env" /etc/service-health-monitor.env
elif [[ -f "$SRC_DIR/service-health-monitor.env.example" && ! -f /etc/service-health-monitor.env ]]; then
  install -m 0600 "$SRC_DIR/service-health-monitor.env.example" /etc/service-health-monitor.env
fi

systemctl daemon-reload
systemctl enable --now service-health-monitor.timer
systemctl start service-health-monitor.service
systemctl status --no-pager service-health-monitor.timer