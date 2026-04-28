#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/subscription-backend
APP_DIR="$APP_ROOT/hh-parser-backend"
SERVICE_NAME=hh-parser-backend

cd "$APP_ROOT"
git pull

cd "$APP_DIR"
mvn -q -DskipTests package

sudo systemctl daemon-reload
sudo systemctl enable "$SERVICE_NAME"
sudo systemctl restart "$SERVICE_NAME"
sudo systemctl status "$SERVICE_NAME" --no-pager