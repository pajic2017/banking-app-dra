CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(39) NOT NULL UNIQUE,
    idempotency_key VARCHAR(100) COLLATE "C" NOT NULL UNIQUE,
    request_hash CHAR(64) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    from_account_id BIGINT,
    to_account_id BIGINT,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    description VARCHAR(240) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    failure_code VARCHAR(40),
    attempts INTEGER NOT NULL DEFAULT 0,
    last_retry_code VARCHAR(40),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_transaction_type CHECK (transaction_type IN ('DEPOSIT','WITHDRAWAL','TRANSFER')),
    CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING','COMPLETED','REJECTED')),
    CONSTRAINT chk_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_transaction_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_transaction_accounts CHECK (
        (transaction_type='DEPOSIT' AND from_account_id IS NULL AND to_account_id IS NOT NULL AND to_account_id>0) OR
        (transaction_type='WITHDRAWAL' AND from_account_id IS NOT NULL AND from_account_id>0 AND to_account_id IS NULL) OR
        (transaction_type='TRANSFER' AND from_account_id IS NOT NULL AND from_account_id>0 AND to_account_id IS NOT NULL AND to_account_id>0 AND from_account_id<>to_account_id)),
    CONSTRAINT chk_transaction_failure CHECK ((status='REJECTED' AND failure_code IS NOT NULL) OR (status<>'REJECTED' AND failure_code IS NULL))
);
CREATE INDEX idx_transaction_pending ON transactions(next_attempt_at,created_at) WHERE status='PENDING';
CREATE INDEX idx_transaction_from ON transactions(from_account_id,created_at,id);
CREATE INDEX idx_transaction_to ON transactions(to_account_id,created_at,id);
