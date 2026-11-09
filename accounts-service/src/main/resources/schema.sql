CREATE TABLE IF NOT EXISTS bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    birth_date DATE NOT NULL,
    balance INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS outbox_messages (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE IF NOT EXISTS account_idempotency (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_messages(status, created_at);

ALTER TABLE account_idempotency
    ADD COLUMN IF NOT EXISTS operation_type VARCHAR(32);

ALTER TABLE account_idempotency
    ADD COLUMN IF NOT EXISTS request_username VARCHAR(255);

ALTER TABLE account_idempotency
    ADD COLUMN IF NOT EXISTS request_amount INTEGER;

ALTER TABLE account_idempotency
    ADD COLUMN IF NOT EXISTS request_action VARCHAR(16);

ALTER TABLE account_idempotency
    ADD COLUMN IF NOT EXISTS request_sender VARCHAR(255);

ALTER TABLE account_idempotency
    ADD COLUMN IF NOT EXISTS request_recipient VARCHAR(255);