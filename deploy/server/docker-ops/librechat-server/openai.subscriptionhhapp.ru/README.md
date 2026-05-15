# LibreChat server ops

Files for the `openai.subscriptionhhapp.ru` deployment on `103.249.133.59`.

Includes:

- `nginx/openai.subscriptionhhapp.ru.conf`:
  reverse proxy from `80/443` to local `LibreChat` on `127.0.0.1:3080`
- `monitoring/librechat-disk-monitor.*`:
  disk usage alerts for `/` with thresholds `85%` and `92%`
- `monitoring/amnezia-3proxy-log-maintenance.*`:
  hourly maintenance for the `amnezia-socks5proxy` internal `3proxy.log`

Quick install:

```bash
cd deploy/server/docker-ops/librechat-server/openai.subscriptionhhapp.ru
sudo bash install.sh
```

Before first start, fill `/etc/librechat-disk-monitor.env` if it was created from the example.

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
- installs LibreChat disk monitoring (`85%` and `92%`)
- installs `amnezia-socks5proxy` log maintenance
- enables and starts the related `systemd` timers

What it does not do:

- does not issue a TLS certificate
- does not create `/etc/letsencrypt/live/openai.subscriptionhhapp.ru/*`
- does not deploy the LibreChat application itself
