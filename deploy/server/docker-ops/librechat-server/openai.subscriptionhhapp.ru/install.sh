#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled
mkdir -p /etc/systemd/system

install -m 644 "$SCRIPT_DIR/nginx/openai.subscriptionhhapp.ru.conf" /etc/nginx/sites-available/openai.subscriptionhhapp.ru
rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/openai.subscriptionhhapp.ru /etc/nginx/sites-enabled/openai.subscriptionhhapp.ru

install -m 755 "$SCRIPT_DIR/monitoring/librechat-disk-monitor.sh" /usr/local/sbin/librechat-disk-monitor.sh
install -m 644 "$SCRIPT_DIR/monitoring/librechat-disk-monitor.service" /etc/systemd/system/librechat-disk-monitor.service
install -m 644 "$SCRIPT_DIR/monitoring/librechat-disk-monitor.timer" /etc/systemd/system/librechat-disk-monitor.timer

if [[ ! -f /etc/librechat-disk-monitor.env ]]; then
  install -m 600 "$SCRIPT_DIR/monitoring/librechat-disk-monitor.env.example" /etc/librechat-disk-monitor.env
fi

install -m 755 "$SCRIPT_DIR/monitoring/amnezia-3proxy-log-maintenance.sh" /usr/local/sbin/amnezia-3proxy-log-maintenance.sh
install -m 644 "$SCRIPT_DIR/monitoring/amnezia-3proxy-log-maintenance.service" /etc/systemd/system/amnezia-3proxy-log-maintenance.service
install -m 644 "$SCRIPT_DIR/monitoring/amnezia-3proxy-log-maintenance.timer" /etc/systemd/system/amnezia-3proxy-log-maintenance.timer

nginx -t
systemctl daemon-reload
systemctl enable --now nginx
systemctl reload nginx

systemctl enable --now librechat-disk-monitor.timer
systemctl start librechat-disk-monitor.service

systemctl enable --now amnezia-3proxy-log-maintenance.timer
systemctl start amnezia-3proxy-log-maintenance.service

echo "Installed LibreChat server ops."
echo "Review /etc/librechat-disk-monitor.env if needed."
