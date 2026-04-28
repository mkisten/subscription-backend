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