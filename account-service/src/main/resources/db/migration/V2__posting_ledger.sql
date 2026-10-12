-- Receipts, journal and balance changes commit together in the Account database.
CREATE TABLE posting_receipts (
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    instruction_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    posting_type VARCHAR(16) NOT NULL,
    from_account_id BIGINT,
    to_account_id BIGINT,
    amount DECIMAL(19,4) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(40),
    recorded_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_receipt_type CHECK (posting_type IN ('DEPOSIT','WITHDRAWAL','TRANSFER')),
    CONSTRAINT chk_receipt_amount CHECK (amount > 0),
    CONSTRAINT chk_receipt_status CHECK (status IN ('APPLYING','COMPLETED','REJECTED')),
    CONSTRAINT chk_receipt_failure CHECK ((status='REJECTED' AND failure_code IS NOT NULL) OR (status<>'REJECTED' AND failure_code IS NULL))
) ENGINE=InnoDB;

CREATE TABLE ledger_entries (
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    line_no INT NOT NULL,
    account_id BIGINT,
    ledger_role VARCHAR(16) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id, line_no),
    CONSTRAINT fk_ledger_receipt FOREIGN KEY (operation_id) REFERENCES posting_receipts(operation_id),
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT chk_ledger_line CHECK (line_no IN (1,2)),
    CONSTRAINT chk_ledger_amount CHECK (amount <> 0),
    CONSTRAINT chk_ledger_role CHECK ((ledger_role='CUSTOMER' AND account_id IS NOT NULL) OR (ledger_role='CLEARING' AND account_id IS NULL)),
    INDEX idx_ledger_account (account_id,created_at)
) ENGINE=InnoDB;
