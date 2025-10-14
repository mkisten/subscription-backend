-- Инициализация базы данных для Telegram бота подписок
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Устанавливаем временную зону
SET timezone = 'UTC';

-- Комментарий для базы данных
COMMENT ON DATABASE subscription_db IS 'Database for Telegram subscription bot backend';

-- Создаем таблицу пользователей (если не создана через JPA)
-- Эта таблица будет создана автоматически JPA, но можно добавить кастомные индексы

-- Добавляем колонку role если её нет
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Индекс для быстрого поиска по telegram_id
CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegram_id);

-- Индекс для поиска по дате окончания подписки
CREATE INDEX IF NOT EXISTS idx_users_subscription_end_date ON users(subscription_end_date);

-- Индекс для поиска по username (если используется)
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username) WHERE username IS NOT NULL;

-- Индекс для поиска по роли
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- Создаем таблицу для сессий авторизации
CREATE TABLE IF NOT EXISTS auth_sessions (
                                             session_id VARCHAR(36) PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    telegram_id BIGINT,
    status VARCHAR(20) NOT NULL,
    jwt_token TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- Индексы для таблицы auth_sessions
CREATE INDEX IF NOT EXISTS idx_auth_sessions_device_id ON auth_sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_created_at ON auth_sessions(created_at);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_status ON auth_sessions(status);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_telegram_id ON auth_sessions(telegram_id);

-- Комментарии к таблицам
COMMENT ON TABLE users IS 'Пользователи Telegram бота подписок';
COMMENT ON COLUMN users.telegram_id IS 'Уникальный ID пользователя в Telegram';
COMMENT ON COLUMN users.subscription_end_date IS 'Дата окончания подписки';
COMMENT ON COLUMN users.trial_used IS 'Использован ли пробный период';
COMMENT ON COLUMN users.role IS 'Роль пользователя: USER, ADMIN, MODERATOR';

-- Комментарии к таблице auth_sessions
COMMENT ON TABLE auth_sessions IS 'Сессии авторизации через Telegram';
COMMENT ON COLUMN auth_sessions.session_id IS 'Уникальный ID сессии';
COMMENT ON COLUMN auth_sessions.device_id IS 'ID устройства, инициировавшего авторизацию';
COMMENT ON COLUMN auth_sessions.telegram_id IS 'ID пользователя Telegram после авторизации';
COMMENT ON COLUMN auth_sessions.status IS 'Статус сессии: PENDING, COMPLETED, EXPIRED';
COMMENT ON COLUMN auth_sessions.jwt_token IS 'JWT токен для авторизации в приложении';
COMMENT ON COLUMN auth_sessions.created_at IS 'Время создания сессии';
COMMENT ON COLUMN auth_sessions.updated_at IS 'Время последнего обновления сессии';

-- Создаем триггер для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_auth_sessions_updated_at
    BEFORE UPDATE ON auth_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();