-- Инициализация базы данных для сервиса вакансий
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
SET timezone = 'UTC';

COMMENT ON DATABASE vacancy_service IS 'Database for HH.ru vacancy monitoring service';

-- Основная таблица настроек пользователя
CREATE TABLE IF NOT EXISTS user_settings (
                                             telegram_id BIGINT PRIMARY KEY,
                                             search_query VARCHAR(255) NOT NULL DEFAULT 'Python разработчик',
    days INTEGER NOT NULL DEFAULT 1,
    exclude_keywords VARCHAR(1000) DEFAULT '',
    telegram_notify BOOLEAN DEFAULT false,
    auto_update_enabled BOOLEAN DEFAULT false,
    auto_update_interval INTEGER DEFAULT 30,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    theme VARCHAR(50) DEFAULT 'light',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- Таблица типов работы для пользователя (многие ко многим)
CREATE TABLE IF NOT EXISTS user_work_types (
                                               telegram_id BIGINT NOT NULL,
                                               work_type VARCHAR(100) NOT NULL,
    PRIMARY KEY (telegram_id, work_type),
    FOREIGN KEY (telegram_id) REFERENCES user_settings(telegram_id) ON DELETE CASCADE
    );

-- Таблица стран для пользователя (многие ко многим)
CREATE TABLE IF NOT EXISTS user_countries (
                                              telegram_id BIGINT NOT NULL,
                                              country VARCHAR(100) NOT NULL,
    PRIMARY KEY (telegram_id, country),
    FOREIGN KEY (telegram_id) REFERENCES user_settings(telegram_id) ON DELETE CASCADE
    );

-- Таблица вакансий
CREATE TABLE IF NOT EXISTS vacancies (
                                         id VARCHAR(255) NOT NULL,
    user_telegram_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    employer VARCHAR(255),
    city VARCHAR(100),
    schedule VARCHAR(50),
    salary VARCHAR(100),
    url VARCHAR(500),
    sent_to_telegram BOOLEAN DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    published_at TIMESTAMP,
    loaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, user_telegram_id)
    );

-- Индексы для оптимизации производительности
CREATE INDEX IF NOT EXISTS idx_user_settings_telegram_id ON user_settings(telegram_id);
CREATE INDEX IF NOT EXISTS idx_user_settings_next_run_at ON user_settings(next_run_at);
CREATE INDEX IF NOT EXISTS idx_user_work_types_telegram_id ON user_work_types(telegram_id);
CREATE INDEX IF NOT EXISTS idx_user_countries_telegram_id ON user_countries(telegram_id);
CREATE INDEX IF NOT EXISTS idx_vacancies_user_telegram_id ON vacancies(user_telegram_id);
CREATE INDEX IF NOT EXISTS idx_vacancies_status ON vacancies(status);
CREATE INDEX IF NOT EXISTS idx_vacancies_published_at ON vacancies(published_at);
CREATE INDEX IF NOT EXISTS idx_vacancies_loaded_at ON vacancies(loaded_at);

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггер для автоматического обновления updated_at в user_settings
CREATE OR REPLACE TRIGGER update_user_settings_updated_at
    BEFORE UPDATE ON user_settings
                         FOR EACH ROW
                         EXECUTE FUNCTION update_updated_at_column();

-- Вставляем начальные данные для тестирования (опционально)
INSERT INTO user_settings (telegram_id, search_query, days, telegram_notify)
VALUES (123456789, 'Java разработчик', 1, true)
    ON CONFLICT (telegram_id) DO NOTHING;

INSERT INTO user_work_types (telegram_id, work_type)
VALUES (123456789, 'remote')
    ON CONFLICT (telegram_id, work_type) DO NOTHING;

INSERT INTO user_countries (telegram_id, country)
VALUES (123456789, 'russia')
    ON CONFLICT (telegram_id, country) DO NOTHING;
