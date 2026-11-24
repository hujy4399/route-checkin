-- Requires MySQL 8.0.16+ so that CHECK constraints are enforced.
-- V1 creates app_user; this migration creates the other 14 tables.

CREATE TABLE app_role (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(30) NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(200) NULL,
    CONSTRAINT pk_app_role PRIMARY KEY (role_id),
    CONSTRAINT uq_app_role_code UNIQUE (role_code),
    CONSTRAINT uq_app_role_name UNIQUE (role_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (role_id),
    CONSTRAINT fk_user_role_assigner FOREIGN KEY (assigned_by) REFERENCES app_user (user_id),
    INDEX idx_user_role_role (role_id),
    INDEX idx_user_role_assigned_by (assigned_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE map_point (
    point_id BIGINT NOT NULL AUTO_INCREMENT,
    point_name VARCHAR(100) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    address_text VARCHAR(255) NULL,
    description VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    point_status VARCHAR(20) NOT NULL,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_map_point PRIMARY KEY (point_id),
    CONSTRAINT chk_map_point_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_map_point_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_map_point_status CHECK (point_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_map_point_creator FOREIGN KEY (created_by) REFERENCES app_user (user_id),
    INDEX idx_map_point_created_by (created_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `route` (
    route_id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    description LONGTEXT NOT NULL,
    cover_url VARCHAR(500) NULL,
    publisher_id BIGINT NOT NULL,
    route_kind VARCHAR(20) NOT NULL,
    route_status VARCHAR(20) NOT NULL,
    recommended_minutes INT NOT NULL,
    available_from DATETIME(0) NULL,
    available_until DATETIME(0) NULL,
    checkin_bonus_points INT NOT NULL DEFAULT 0,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME(0) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(0) NULL,
    CONSTRAINT pk_route PRIMARY KEY (route_id),
    CONSTRAINT chk_route_kind CHECK (route_kind IN ('NORMAL', 'OFFICIAL_EVENT')),
    CONSTRAINT chk_route_status CHECK (route_status IN ('DRAFT', 'PENDING', 'PUBLISHED', 'REJECTED', 'CLOSED')),
    CONSTRAINT chk_route_minutes CHECK (recommended_minutes > 0),
    CONSTRAINT chk_route_bonus CHECK (checkin_bonus_points >= 0),
    CONSTRAINT chk_route_availability CHECK (
        available_from IS NULL OR available_until IS NULL OR available_until > available_from
    ),
    CONSTRAINT fk_route_publisher FOREIGN KEY (publisher_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_route_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user (user_id),
    INDEX idx_route_status_published (route_status, published_at DESC),
    INDEX idx_route_publisher_created (publisher_id, created_at DESC),
    INDEX idx_route_reviewed_by (reviewed_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE route_point (
    route_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    point_id BIGINT NOT NULL,
    stay_minutes INT NULL,
    note VARCHAR(300) NULL,
    CONSTRAINT pk_route_point PRIMARY KEY (route_id, sequence_no),
    CONSTRAINT uq_route_point_point UNIQUE (route_id, point_id),
    CONSTRAINT chk_route_point_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_route_point_stay CHECK (stay_minutes >= 0),
    CONSTRAINT fk_route_point_route FOREIGN KEY (route_id) REFERENCES `route` (route_id),
    CONSTRAINT fk_route_point_point FOREIGN KEY (point_id) REFERENCES map_point (point_id),
    INDEX idx_route_point_point_route (point_id, route_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE keyword (
    keyword_id BIGINT NOT NULL AUTO_INCREMENT,
    keyword_name VARCHAR(50) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT pk_keyword PRIMARY KEY (keyword_id),
    CONSTRAINT uq_keyword_name UNIQUE (keyword_name),
    CONSTRAINT chk_keyword_active CHECK (is_active IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE route_keyword (
    route_id BIGINT NOT NULL,
    keyword_id BIGINT NOT NULL,
    CONSTRAINT pk_route_keyword PRIMARY KEY (route_id, keyword_id),
    CONSTRAINT fk_route_keyword_route FOREIGN KEY (route_id) REFERENCES `route` (route_id),
    CONSTRAINT fk_route_keyword_keyword FOREIGN KEY (keyword_id) REFERENCES keyword (keyword_id),
    INDEX idx_route_keyword_keyword_route (keyword_id, route_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE route_like (
    like_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    first_liked_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_route_like PRIMARY KEY (like_id),
    CONSTRAINT uq_route_like_user_route UNIQUE (user_id, route_id),
    CONSTRAINT chk_route_like_active CHECK (is_active IN (0, 1)),
    CONSTRAINT fk_route_like_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_route_like_route FOREIGN KEY (route_id) REFERENCES `route` (route_id),
    INDEX idx_route_like_route_active (route_id, is_active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE route_favorite (
    favorite_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    first_favorited_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_route_favorite PRIMARY KEY (favorite_id),
    CONSTRAINT uq_route_favorite_user_route UNIQUE (user_id, route_id),
    CONSTRAINT chk_route_favorite_active CHECK (is_active IN (0, 1)),
    CONSTRAINT fk_route_favorite_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_route_favorite_route FOREIGN KEY (route_id) REFERENCES `route` (route_id),
    INDEX idx_route_favorite_route_active (route_id, is_active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE route_checkin (
    checkin_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    checked_in_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(300) NULL,
    CONSTRAINT pk_route_checkin PRIMARY KEY (checkin_id),
    CONSTRAINT uq_route_checkin_user_route UNIQUE (user_id, route_id),
    CONSTRAINT fk_route_checkin_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_route_checkin_route FOREIGN KEY (route_id) REFERENCES `route` (route_id),
    INDEX idx_route_checkin_route_time (route_id, checked_in_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE point_rule (
    rule_code VARCHAR(40) NOT NULL,
    rule_name VARCHAR(80) NOT NULL,
    points INT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_point_rule PRIMARY KEY (rule_code),
    CONSTRAINT chk_point_rule_points CHECK (points >= 0),
    CONSTRAINT chk_point_rule_active CHECK (is_active IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE reward_product (
    product_id BIGINT NOT NULL AUTO_INCREMENT,
    product_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    image_url VARCHAR(500) NULL,
    points_price INT NOT NULL,
    stock_quantity INT NOT NULL,
    product_status VARCHAR(20) NOT NULL,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_reward_product PRIMARY KEY (product_id),
    CONSTRAINT chk_reward_product_price CHECK (points_price > 0),
    CONSTRAINT chk_reward_product_stock CHECK (stock_quantity >= 0),
    CONSTRAINT chk_reward_product_status CHECK (product_status IN ('ON_SALE', 'OFF_SALE')),
    INDEX idx_reward_product_status_price (product_status, points_price)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE redemption_order (
    redemption_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_points_snapshot INT NOT NULL,
    order_status VARCHAR(20) NOT NULL,
    pickup_code VARCHAR(30) NOT NULL,
    redeemed_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fulfilled_at DATETIME(0) NULL,
    CONSTRAINT pk_redemption_order PRIMARY KEY (redemption_id),
    CONSTRAINT uq_redemption_order_pickup_code UNIQUE (pickup_code),
    CONSTRAINT chk_redemption_order_quantity CHECK (quantity > 0),
    CONSTRAINT chk_redemption_order_price CHECK (unit_points_snapshot > 0),
    CONSTRAINT chk_redemption_order_status CHECK (order_status IN ('PENDING_PICKUP', 'FULFILLED', 'CANCELLED')),
    CONSTRAINT fk_redemption_order_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_redemption_order_product FOREIGN KEY (product_id) REFERENCES reward_product (product_id),
    INDEX idx_redemption_order_user_time (user_id, redeemed_at DESC),
    INDEX idx_redemption_order_status_time (order_status, redeemed_at),
    INDEX idx_redemption_order_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE point_ledger (
    ledger_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    change_points INT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    rule_code VARCHAR(40) NULL,
    like_id BIGINT NULL,
    favorite_id BIGINT NULL,
    checkin_id BIGINT NULL,
    redemption_id BIGINT NULL,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(200) NULL,
    CONSTRAINT pk_point_ledger PRIMARY KEY (ledger_id),
    CONSTRAINT uq_point_ledger_event_key UNIQUE (event_key),
    CONSTRAINT chk_point_ledger_change CHECK (change_points <> 0),
    CONSTRAINT chk_point_ledger_type CHECK (event_type IN (
        'RULE_REWARD', 'BONUS_DEBIT', 'BONUS_CREDIT',
        'REDEEM_DEBIT', 'REDEEM_REFUND', 'ADMIN_ADJUST'
    )),
    CONSTRAINT fk_point_ledger_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_point_ledger_rule FOREIGN KEY (rule_code) REFERENCES point_rule (rule_code),
    CONSTRAINT fk_point_ledger_like FOREIGN KEY (like_id) REFERENCES route_like (like_id),
    CONSTRAINT fk_point_ledger_favorite FOREIGN KEY (favorite_id) REFERENCES route_favorite (favorite_id),
    CONSTRAINT fk_point_ledger_checkin FOREIGN KEY (checkin_id) REFERENCES route_checkin (checkin_id),
    CONSTRAINT fk_point_ledger_redemption FOREIGN KEY (redemption_id) REFERENCES redemption_order (redemption_id),
    INDEX idx_point_ledger_user_time (user_id, created_at DESC),
    INDEX idx_point_ledger_rule (rule_code),
    INDEX idx_point_ledger_like (like_id),
    INDEX idx_point_ledger_favorite (favorite_id),
    INDEX idx_point_ledger_checkin (checkin_id),
    INDEX idx_point_ledger_redemption (redemption_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
