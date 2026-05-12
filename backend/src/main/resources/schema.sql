-- Safety-net schema initialization.
-- Spring Boot runs this AFTER Hibernate DDL (defer-datasource-initialization=true).
-- IF NOT EXISTS prevents conflicts with Hibernate's own DDL.

CREATE TABLE IF NOT EXISTS users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(255) UNIQUE NOT NULL,
    password       VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    role           VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP,
    last_login_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    token      VARCHAR(255) UNIQUE NOT NULL,
    user_id    BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS tasks (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT,
    user_input     CLOB,
    ai_output      CLOB,
    mode           VARCHAR(50),
    category       VARCHAR(100),
    skill_level    VARCHAR(50),
    intent_type    VARCHAR(50),
    model_name     VARCHAR(100),
    prompt_version VARCHAR(20),
    schema_version VARCHAR(20),
    created_at     TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS conversations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT,
    title           VARCHAR(255),
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      TIMESTAMP,
    last_message_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         CLOB NOT NULL,
    intent_type     VARCHAR(50),
    task_id         BIGINT,
    created_at      TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);

CREATE TABLE IF NOT EXISTS step_progress (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT NOT NULL,
    step_index  INT NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    priority    VARCHAR(20) DEFAULT 'MEDIUM',
    note        CLOB,
    updated_at  TIMESTAMP,
    UNIQUE (task_id, step_index)
);
