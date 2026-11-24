CREATE TABLE app_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    account_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    registered_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(0) NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (user_id),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT chk_app_user_status
        CHECK (account_status IN ('PENDING', 'ACTIVE', 'DISABLED')),
    CONSTRAINT fk_app_user_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES app_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_app_user_reviewed_by ON app_user (reviewed_by);
