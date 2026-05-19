# Server Deployment

Этот комплект нужен для недокерного деплоя двух сервисов на одном сервере:
- `hh-parser-backend`
- `vacancy-backend`

Схема работы:
1. `hh-parser-backend` поднимается как обычный Spring Boot `jar` на `127.0.0.1:8084`.
2. `vacancy-backend` поднимается как отдельный `jar`.
3. `vacancy-backend` получает `HH_BASE_URL=http://127.0.0.1:8084/api` и начинает брать вакансии из внутреннего парсера.

## Что должно быть на сервере
- Java 17
- Maven 3.9+
- PostgreSQL
- systemd
- git

## Каталоги
Рекомендуемый layout:
- `/opt/subscription-backend`
- `/opt/subscription-backend/hh-parser-backend`
- `/opt/subscription-backend/vacancy-backend`
- `/etc/subscription-backend/hh-parser-backend.env`
- `/etc/subscription-backend/vacancy-backend.env`

## Порядок деплоя
1. Склонировать/обновить репозиторий в `/opt/subscription-backend`.
2. Создать БД для `hh-parser-backend`.
3. Положить env-файлы из каталога `env/` в `/etc/subscription-backend/` и заполнить реальные значения.
4. Установить unit-файлы из каталога `systemd/` в `/etc/systemd/system/`.
5. Собрать и запустить `hh-parser-backend`.
6. Обновить и перезапустить `vacancy-backend`.

## Ключевая связка
В `vacancy-backend.env` обязательно должно быть:

`HH_BASE_URL=http://127.0.0.1:8084/api`

Без этого сервис вакансий продолжит смотреть наружу на `api.hh.ru`.

## Команды
Сборка и рестарт парсера:

```bash
cd /opt/subscription-backend/hh-parser-backend
mvn -q -DskipTests package
sudo systemctl restart hh-parser-backend
sudo systemctl status hh-parser-backend --no-pager
```

Сборка и рестарт сервиса вакансий:

```bash
cd /opt/subscription-backend/vacancy-backend
mvn -q -DskipTests package
sudo systemctl restart vacancy-backend
sudo systemctl status vacancy-backend --no-pager
```

Проверка связки:

```bash
curl http://127.0.0.1:8084/api/actuator/health
curl "http://127.0.0.1:8084/api/vacancies?text=java&page=0&per_page=20"
```

## Docker server ops: Telegram proxy for `subscription_backend`

Если `subscription_backend` развернут в Docker и должен ходить в Telegram через `redsocks`, используйте комплект:
- `deploy/server/docker-ops/subscription-telegram-proxy/subscription-telegram-proxy.sh`
- `deploy/server/docker-ops/subscription-telegram-proxy/subscription-telegram-proxy.service`
- `deploy/server/docker-ops/subscription-telegram-proxy/subscription-telegram-proxy.timer`
- `deploy/server/docker-ops/subscription-telegram-proxy/install.sh`

Этот вариант защищает от проблемы после reboot или recreate контейнера:
- не использует захардкоженный container IP
- ждёт появления `subscription_backend` после boot
- чистит старые `iptables`-правила перед установкой нового redirect
- периодически переустанавливает маршрут через `systemd timer`
- после рестарта `redsocks` автоматически повторно применяет Telegram-routing для `subscription_backend`

Важно:
- авторизация через Telegram bot для сервиса вакансий зависит не только от `subscription_backend`, но и от рабочей связки `redsocks + subscription-telegram-proxy`
- если маршрут до Telegram деградирует, backend может оставаться `healthy`, но `create-session` и отправка bot-сообщений начнут падать

Установка на сервере:

```bash
cd /opt/subscription-backend/deploy/server/docker-ops/subscription-telegram-proxy
sudo bash install.sh
```

## Docker server ops: Service health monitor with Telegram alerts

Если стек развернут в Docker и нужно получать уведомления о падении сервисов и ключевых зависимостей в Telegram и по email, используйте комплект:
- `deploy/server/docker-ops/service-health-monitor/service-health-monitor.sh`
- `deploy/server/docker-ops/service-health-monitor/service-health-monitor.service`
- `deploy/server/docker-ops/service-health-monitor/service-health-monitor.timer`
- `deploy/server/docker-ops/service-health-monitor/service-health-monitor.env.example`
- `deploy/server/docker-ops/service-health-monitor/install.sh`

Что проверяет монитор:
- `subscription_backend` health
- доступ к Telegram из `subscription_backend`
- создание `Telegram auth session` через `POST /api/telegram-auth/create-session`
- `vacancy_backend` health
- получение `auth token` сервисом вакансий через `api.subscriptionhhapp.ru/api/auth/token` (с алертом после 2 подряд сбоев)
- `hh_parser_backend` health
- реальный поисковый запрос к `hh_parser_backend`
- `graylog` health
- наличие GELF UDP input у `graylog`
- `shopping_backend` health
- доступ к Telegram из `shopping_backend`
- `family-backend.service`
- `redsocks`
- `subscription-telegram-proxy.timer`

Уведомления отправляются только при смене состояния `UP -> DOWN` и `DOWN -> UP`.
Состояния хранятся в `/var/lib/service-health-monitor/*.state`.
SMTP-настройки задаются через `/etc/service-health-monitor.env`. В `EMAIL_TO` можно указать несколько адресов через запятую.
Для Telegram-route у `subscription_backend` monitor также умеет делать self-heal:
- `systemctl restart redsocks`
- `systemctl start subscription-telegram-proxy.service`
- затем повторная проверка маршрута и auth session

Установка на сервере:

```bash
cd /opt/subscription-backend/deploy/server/docker-ops/service-health-monitor
sudo cp service-health-monitor.env.example service-health-monitor.env
sudo bash install.sh
```
