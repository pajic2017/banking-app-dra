CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    phone VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_customers_version CHECK (version >= 0),
    INDEX idx_customers_status_id (status, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4;
