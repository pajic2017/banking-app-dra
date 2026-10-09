CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    account_number VARCHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_accounts_number UNIQUE (account_number),
    CONSTRAINT chk_accounts_customer CHECK (customer_id > 0),
    CONSTRAINT chk_accounts_type CHECK (account_type IN ('CURRENT','SAVINGS')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE','BLOCKED','CLOSED')),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_version CHECK (version >= 0),
    INDEX idx_accounts_customer_id (customer_id,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
