-- Инициализация базы данных для Telegram бота подписок
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Устанавливаем временную зону
SET timezone = 'UTC';

-- Комментарий для базы данных
COMMENT ON DATABASE subscription_db IS 'Database for Telegram subscription bot backend';

-- Таблица payment_history (история платежей)
CREATE TABLE IF NOT EXISTS payment_history (
                                               id BIGSERIAL PRIMARY KEY,
                                               amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    payment_date TIMESTAMP,
    payment_provider VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    subscription_plan VARCHAR(50),
    transaction_id VARCHAR(100) UNIQUE,
    user_id BIGINT NOT NULL
    );

-- Таблица users (пользователи)
CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     email VARCHAR(255),
    first_name VARCHAR(100),
    last_login_at TIMESTAMP,
    last_name VARCHAR(100),
    phone VARCHAR(20),
    subscription_end_date TIMESTAMP,
    subscription_plan VARCHAR(50),
    telegram_id BIGINT UNIQUE NOT NULL,
    trial_used BOOLEAN NOT NULL DEFAULT FALSE,
    username VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subscription_active BOOLEAN NOT NULL DEFAULT FALSE
    );

-- Таблица messages (сообщения)
CREATE TABLE IF NOT EXISTS messages (
                                        id BIGSERIAL PRIMARY KEY,
                                        content TEXT NOT NULL,
                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        direction VARCHAR(10) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    message_type VARCHAR(20) NOT NULL,
    telegram_id BIGINT NOT NULL
    );

-- Таблица auth_sessions (сессии авторизации)
CREATE TABLE IF NOT EXISTS auth_sessions (
                                             id BIGSERIAL PRIMARY KEY,
                                             session_id VARCHAR(36) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_id VARCHAR(255) NOT NULL,
    jwt_token TEXT,
    status VARCHAR(20) NOT NULL,
    telegram_id BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP
    );

-- Таблица payments (платежи)
CREATE TABLE IF NOT EXISTS payments (
                                        id BIGSERIAL PRIMARY KEY,
                                        admin_notes TEXT,
                                        amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    months INTEGER NOT NULL,
    phone_number VARCHAR(20),
    plan VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    telegram_id BIGINT NOT NULL,
    verified_at TIMESTAMP
    );

-- Индексы для таблицы payment_history
CREATE INDEX IF NOT EXISTS idx_payment_history_user_id ON payment_history(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_history_status ON payment_history(status);
CREATE INDEX IF NOT EXISTS idx_payment_history_created_at ON payment_history(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_history_transaction_id ON payment_history(transaction_id);

-- Индексы для таблицы users
CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_users_subscription_end_date ON users(subscription_end_date);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username) WHERE username IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_subscription_active ON users(subscription_active);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE email IS NOT NULL;

-- Индексы для таблицы messages
CREATE INDEX IF NOT EXISTS idx_messages_telegram_id ON messages(telegram_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at);
CREATE INDEX IF NOT EXISTS idx_messages_direction ON messages(direction);
CREATE INDEX IF NOT EXISTS idx_messages_message_type ON messages(message_type);

-- Индексы для таблицы auth_sessions
CREATE INDEX IF NOT EXISTS idx_auth_sessions_session_id ON auth_sessions(session_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_device_id ON auth_sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_telegram_id ON auth_sessions(telegram_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_status ON auth_sessions(status);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_created_at ON auth_sessions(created_at);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_expires_at ON auth_sessions(expires_at);

-- Индексы для таблицы payments
CREATE INDEX IF NOT EXISTS idx_payments_telegram_id ON payments(telegram_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments(created_at);
CREATE INDEX IF NOT EXISTS idx_payments_verified_at ON payments(verified_at);

-- Комментарии к таблицам
COMMENT ON TABLE payment_history IS 'История платежей пользователей';
COMMENT ON TABLE users IS 'Пользователи Telegram бота подписок';
COMMENT ON TABLE messages IS 'История сообщений бота';
COMMENT ON TABLE auth_sessions IS 'Сессии авторизации через Telegram';
COMMENT ON TABLE payments IS 'Платежи пользователей';

-- Комментарии к колонкам payment_history
COMMENT ON COLUMN payment_history.amount IS 'Сумма платежа';
COMMENT ON COLUMN payment_history.currency IS 'Валюта платежа';
COMMENT ON COLUMN payment_history.payment_provider IS 'Платежная система';
COMMENT ON COLUMN payment_history.status IS 'Статус платежа';
COMMENT ON COLUMN payment_history.subscription_plan IS 'План подписки';
COMMENT ON COLUMN payment_history.transaction_id IS 'ID транзакции в платежной системе';

-- Комментарии к колонкам users
COMMENT ON COLUMN users.telegram_id IS 'Уникальный ID пользователя в Telegram';
COMMENT ON COLUMN users.subscription_end_date IS 'Дата окончания подписки';
COMMENT ON COLUMN users.trial_used IS 'Использован ли пробный период';
COMMENT ON COLUMN users.role IS 'Роль пользователя: USER, ADMIN, MODERATOR';
COMMENT ON COLUMN users.subscription_active IS 'Активна ли подписка';

-- Комментарии к колонкам messages
COMMENT ON COLUMN messages.direction IS 'Направление сообщения: IN - входящее, OUT - исходящее';
COMMENT ON COLUMN messages.message_type IS 'Тип сообщения: TEXT, COMMAND, etc';

-- Комментарии к колонкам auth_sessions
COMMENT ON COLUMN auth_sessions.session_id IS 'Уникальный ID сессии';
COMMENT ON COLUMN auth_sessions.device_id IS 'ID устройства, инициировавшего авторизацию';
COMMENT ON COLUMN auth_sessions.telegram_id IS 'ID пользователя Telegram после авторизации';
COMMENT ON COLUMN auth_sessions.status IS 'Статус сессии: PENDING, COMPLETED, EXPIRED';
COMMENT ON COLUMN auth_sessions.jwt_token IS 'JWT токен для авторизации в приложении';

-- Комментарии к колонкам payments
COMMENT ON COLUMN payments.amount IS 'Сумма платежа';
COMMENT ON COLUMN payments.months IS 'Количество месяцев подписки';
COMMENT ON COLUMN payments.plan IS 'План подписки';
COMMENT ON COLUMN payments.status IS 'Статус платежа';
COMMENT ON COLUMN payments.verified_at IS 'Время верификации платежа';

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггеры для обновления updated_at
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auth_sessions_updated_at
    BEFORE UPDATE ON auth_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Вставляем начальные данные если нужно
-- INSERT INTO users (telegram_id, first_name, username, role, subscription_active)
-- VALUES (6927880904, 'Admin', 'admin', 'ADMIN', true)
-- ON CONFLICT (telegram_id) DO NOTHING;