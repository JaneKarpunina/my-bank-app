CREATE TABLE IF NOT EXISTS idempotency_keys (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS transfer_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    attempts INT NOT NULL DEFAULT 0
);
