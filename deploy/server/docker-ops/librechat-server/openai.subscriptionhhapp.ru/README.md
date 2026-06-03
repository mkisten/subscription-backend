# LibreChat server ops

Files for the `openai.subscriptionhhapp.ru` deployment on `103.249.133.59`.

Includes:

- `nginx/openai.subscriptionhhapp.ru.conf`:
  reverse proxy from `80/443` to local `LibreChat` on `127.0.0.1:3080`
- `monitoring/librechat-disk-monitor.*`:
  disk usage alerts for `/` with thresholds `85%` and `92%`, plus LibreChat HTTP health checks
- `monitoring/amnezia-3proxy-log-maintenance.*`:
  hourly maintenance for the `amnezia-socks5proxy` internal `3proxy.log`

Quick install:

```bash
cd deploy/server/docker-ops/librechat-server/openai.subscriptionhhapp.ru
sudo bash install.sh
```

Before first start, fill `/etc/librechat-disk-monitor.env` if it was created from the example.
If you want early detection of Anthropic-side failures such as `403 Your request was blocked`, also set:

- `ENABLE_ANTHROPIC_CHECK=true`
- `ANTHROPIC_API_KEY=<server-side key used by LibreChat>`

Recommended target paths on the server:

- nginx vhost:
  `/etc/nginx/sites-available/openai.subscriptionhhapp.ru`
- disk monitor script:
  `/usr/local/sbin/librechat-disk-monitor.sh`
- disk monitor unit:
  `/etc/systemd/system/librechat-disk-monitor.service`
- disk monitor timer:
  `/etc/systemd/system/librechat-disk-monitor.timer`
- disk monitor env:
  `/etc/librechat-disk-monitor.env`
- 3proxy maintenance script:
  `/usr/local/sbin/amnezia-3proxy-log-maintenance.sh`
- 3proxy maintenance unit:
  `/etc/systemd/system/amnezia-3proxy-log-maintenance.service`
- 3proxy maintenance timer:
  `/etc/systemd/system/amnezia-3proxy-log-maintenance.timer`

What `install.sh` does:

- installs nginx vhost config for `openai.subscriptionhhapp.ru`
- installs LibreChat monitoring (`85%` and `92%` disk thresholds, plus HTTP health)
- installs `amnezia-socks5proxy` log maintenance
- enables and starts the related `systemd` timers

What it does not do:

- does not issue a TLS certificate
- does not create `/etc/letsencrypt/live/openai.subscriptionhhapp.ru/*`
- does not deploy the LibreChat application itself

Quick diagnostics for `403 Your request was blocked`:

```bash
curl -fsS http://127.0.0.1:3080/health
curl -s -o /dev/null -w '%{http_code}\n' \
  https://api.anthropic.com/v1/models \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H 'anthropic-version: 2023-06-01'
docker logs --tail 200 LibreChat
```

Interpretation:

- `LibreChat health = 200`, `Anthropic = 200`: базовая связка жива, ищите проблему в конфиге модели или payload.
- `LibreChat health = 200`, `Anthropic = 401`: неверный или просроченный `ANTHROPIC_API_KEY`.
- `LibreChat health = 200`, `Anthropic = 403`: вероятнее всего блокировка по egress/IP, policy или unsupported region.
- `LibreChat health != 200`: сначала чините сам контейнер или локальный reverse proxy.
