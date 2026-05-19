CREATE TABLE IF NOT EXISTS scraped_vacancies (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(512) NOT NULL,
    alternate_url VARCHAR(1024) NOT NULL,
    employer_name VARCHAR(255),
    area_name VARCHAR(255),
    salary_text VARCHAR(512),
    salary_from INTEGER,
    salary_to INTEGER,
    salary_currency VARCHAR(16),
    schedule_name VARCHAR(255),
    work_format_id VARCHAR(64),
    work_format_name VARCHAR(255),
    snippet_requirement TEXT,
    snippet_responsibility TEXT,
    raw_published_text VARCHAR(255),
    published_at TIMESTAMP,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_scraped_vacancies_title ON scraped_vacancies(title);
CREATE INDEX IF NOT EXISTS idx_scraped_vacancies_last_seen_at ON scraped_vacancies(last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_scraped_vacancies_published_at ON scraped_vacancies(published_at DESC);
