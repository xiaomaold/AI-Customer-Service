CREATE TABLE bc_user (
    id           BIGINT PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    display_name VARCHAR(64)  NOT NULL,
    user_type    VARCHAR(32)  NOT NULL,
    created_time DATETIME     NOT NULL,
    updated_time DATETIME     NOT NULL
);

CREATE TABLE bc_product (
    id           BIGINT PRIMARY KEY,
    product_no   VARCHAR(64)   NOT NULL UNIQUE,
    product_name VARCHAR(128)  NOT NULL,
    price        DECIMAL(12,2) NOT NULL,
    description  VARCHAR(512),
    created_time DATETIME      NOT NULL,
    updated_time DATETIME      NOT NULL
);

CREATE TABLE bc_order (
    id                    BIGINT PRIMARY KEY,
    order_no              VARCHAR(64)   NOT NULL UNIQUE,
    user_id               BIGINT        NOT NULL,
    user_type             VARCHAR(32)   NOT NULL,
    product_id            BIGINT        NOT NULL,
    product_no            VARCHAR(64)   NOT NULL,
    product_name_snapshot VARCHAR(128)  NOT NULL,
    unit_price_snapshot   DECIMAL(12,2) NOT NULL,
    quantity              INT           NOT NULL,
    total_amount          DECIMAL(12,2) NOT NULL,
    status                VARCHAR(32)   NOT NULL,
    cancel_reason         VARCHAR(255),
    source_channel        VARCHAR(32)   NOT NULL,
    created_time          DATETIME      NOT NULL,
    updated_time          DATETIME      NOT NULL
);

CREATE TABLE bc_work_order (
    id               BIGINT PRIMARY KEY,
    work_order_no    VARCHAR(64)  NOT NULL UNIQUE,
    user_id          BIGINT       NOT NULL,
    user_type        VARCHAR(32)  NOT NULL,
    work_order_type  VARCHAR(32)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    title            VARCHAR(128) NOT NULL,
    content          VARCHAR(1000),
    related_order_no VARCHAR(64),
    ext_json         TEXT,
    reject_reason    VARCHAR(255),
    processed_by     BIGINT,
    process_remark   VARCHAR(255),
    processed_time   DATETIME,
    source_channel   VARCHAR(32)  NOT NULL,
    created_time     DATETIME     NOT NULL,
    updated_time     DATETIME     NOT NULL
);
