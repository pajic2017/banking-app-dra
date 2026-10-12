CREATE TABLE event_outbox (
    event_id CHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT uk_outbox_event UNIQUE (event_type,aggregate_id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PUBLISHED')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);
CREATE INDEX idx_outbox_pending ON event_outbox(status,next_attempt_at,created_at);
ALTER TABLE transactions ADD COLUMN correlation_id UUID NOT NULL DEFAULT gen_random_uuid();
