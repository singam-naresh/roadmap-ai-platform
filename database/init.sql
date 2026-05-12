-- Create the database (run this manually if it doesn't exist)
-- CREATE DATABASE ai_assistant;

-- Tasks table (Hibernate will auto-create this via ddl-auto=update,
-- but this script is provided for reference / manual setup)
CREATE TABLE IF NOT EXISTS tasks (
    id          BIGSERIAL PRIMARY KEY,
    user_input  TEXT        NOT NULL,
    ai_output   TEXT        NOT NULL,
    mode        VARCHAR(20) NOT NULL DEFAULT 'default',
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Index for fast history retrieval
CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks (created_at DESC);
