#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

install -m 0755 "$SRC_DIR/service-health-monitor.sh" /usr/local/sbin/service-health-monitor.sh
install -m 0644 "$SRC_DIR/service-health-monitor.service" /etc/systemd/system/service-health-monitor.service
install -m 0644 "$SRC_DIR/service-health-monitor.timer" /etc/systemd/system/service-health-monitor.timer
install -d -m 0755 /var/lib/service-health-monitor

systemctl daemon-reload
systemctl enable --now service-health-monitor.timer
systemctl start service-health-monitor.service
systemctl status --no-pager service-health-monitor.timer