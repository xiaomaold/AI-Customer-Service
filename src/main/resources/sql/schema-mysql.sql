CREATE TABLE IF NOT EXISTS kb_knowledge_base
(
    id                  BIGINT       NOT NULL PRIMARY KEY,
    knowledge_base_name VARCHAR(255) NOT NULL,
    description         VARCHAR(1000) NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    document_count      INT          NOT NULL DEFAULT 0,
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_kb_knowledge_base_create_time ON kb_knowledge_base (create_time);
CREATE INDEX idx_kb_knowledge_base_deleted ON kb_knowledge_base (deleted);

CREATE TABLE IF NOT EXISTS kb_document
(
    id                BIGINT        NOT NULL PRIMARY KEY,
    knowledge_base_id BIGINT        NOT NULL,
    document_name     VARCHAR(255)  NOT NULL,
    file_name         VARCHAR(255)  NOT NULL,
    file_ext          VARCHAR(32)   NULL,
    content_type      VARCHAR(255)  NULL,
    file_size         BIGINT        NULL,
    file_hash         VARCHAR(64)   NOT NULL,
    storage_path      VARCHAR(1024) NOT NULL,
    parse_status      VARCHAR(32)   NOT NULL DEFAULT 'PROCESSING',
    chunk_count       INT           NOT NULL DEFAULT 0,
    embedding_model   VARCHAR(128)  NULL,
    remark            VARCHAR(500)  NULL,
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_kb_document_knowledge_base_id ON kb_document (knowledge_base_id);
CREATE INDEX idx_kb_document_kb_id_file_hash ON kb_document (knowledge_base_id, file_hash);
CREATE INDEX idx_kb_document_kb_id_file_name ON kb_document (knowledge_base_id, file_name);
CREATE INDEX idx_kb_document_parse_status ON kb_document (parse_status);
CREATE INDEX idx_kb_document_create_time ON kb_document (create_time);
CREATE INDEX idx_kb_document_deleted ON kb_document (deleted);

CREATE TABLE IF NOT EXISTS chat_session
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    session_name      VARCHAR(255) NOT NULL,
    session_status    VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    pinned            TINYINT      NOT NULL DEFAULT 0,
    last_message_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_chat_session_user_id ON chat_session (user_id);
CREATE INDEX idx_chat_session_pinned ON chat_session (pinned);
CREATE INDEX idx_chat_session_last_message_time ON chat_session (last_message_time);
CREATE INDEX idx_chat_session_deleted ON chat_session (deleted);

CREATE TABLE IF NOT EXISTS chat_message
(
    id                BIGINT      NOT NULL PRIMARY KEY,
    session_id        BIGINT      NOT NULL,
    user_id           BIGINT      NOT NULL,
    role              VARCHAR(32) NOT NULL,
    content           LONGTEXT    NOT NULL,
    reference_content LONGTEXT    NULL,
    model_name        VARCHAR(128) NULL,
    token_count       INT         NULL,
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_chat_message_session_id ON chat_message (session_id);
CREATE INDEX idx_chat_message_user_id ON chat_message (user_id);
CREATE INDEX idx_chat_message_create_time ON chat_message (create_time);
CREATE INDEX idx_chat_message_deleted ON chat_message (deleted);

CREATE TABLE IF NOT EXISTS sys_user
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    nickname        VARCHAR(64)  NULL,
    real_name       VARCHAR(64)  NULL,
    mobile          VARCHAR(32)  NULL,
    email           VARCHAR(128) NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    last_login_time DATETIME     NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username);
CREATE INDEX idx_sys_user_status ON sys_user (status);
CREATE INDEX idx_sys_user_deleted ON sys_user (deleted);

CREATE TABLE IF NOT EXISTS sys_role
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    role_code   VARCHAR(32)  NOT NULL,
    role_name   VARCHAR(64)  NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_role_code ON sys_role (role_code);
CREATE INDEX idx_sys_role_status ON sys_role (status);
CREATE INDEX idx_sys_role_deleted ON sys_role (deleted);

CREATE TABLE IF NOT EXISTS sys_user_role
(
    id          BIGINT   NOT NULL PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    role_id     BIGINT   NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_sys_user_role_user_role ON sys_user_role (user_id, role_id);
CREATE INDEX idx_sys_user_role_user_id ON sys_user_role (user_id);
CREATE INDEX idx_sys_user_role_role_id ON sys_user_role (role_id);

CREATE TABLE IF NOT EXISTS sys_kb_permission
(
    id                BIGINT      NOT NULL PRIMARY KEY,
    knowledge_base_id BIGINT      NOT NULL,
    user_id           BIGINT      NOT NULL,
    permission_type   VARCHAR(32) NOT NULL,
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_sys_kb_permission_kb_user ON sys_kb_permission (knowledge_base_id, user_id);
CREATE INDEX idx_sys_kb_permission_user_id ON sys_kb_permission (user_id);

CREATE TABLE IF NOT EXISTS sys_config
(
    id           BIGINT        NOT NULL PRIMARY KEY,
    config_key   VARCHAR(128)  NOT NULL,
    config_name  VARCHAR(128)  NOT NULL,
    config_value VARCHAR(2000) NULL,
    value_type   VARCHAR(32)   NOT NULL DEFAULT 'STRING',
    remark       VARCHAR(255)  NULL,
    status       VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_config_key ON sys_config (config_key);
CREATE INDEX idx_sys_config_status ON sys_config (status);
CREATE INDEX idx_sys_config_deleted ON sys_config (deleted);

CREATE TABLE IF NOT EXISTS chat_missed_question
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    session_id        BIGINT       NOT NULL,
    knowledge_base_id BIGINT       NULL,
    route_mode        VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN',
    question          VARCHAR(2000) NOT NULL,
    answer            LONGTEXT     NULL,
    miss_reason       VARCHAR(255) NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_chat_missed_question_user_id ON chat_missed_question (user_id);
CREATE INDEX idx_chat_missed_question_session_id ON chat_missed_question (session_id);
CREATE INDEX idx_chat_missed_question_route_mode ON chat_missed_question (route_mode);
CREATE INDEX idx_chat_missed_question_status ON chat_missed_question (status);
CREATE INDEX idx_chat_missed_question_create_time ON chat_missed_question (create_time);
CREATE INDEX idx_chat_missed_question_deleted ON chat_missed_question (deleted);

CREATE TABLE IF NOT EXISTS ai_document_analysis
(
    id                          BIGINT        NOT NULL PRIMARY KEY,
    user_id                     BIGINT        NOT NULL,
    suggested_knowledge_base_id BIGINT        NULL,
    uploaded_knowledge_base_id  BIGINT        NULL,
    uploaded_document_id        BIGINT        NULL,
    original_file_name          VARCHAR(255)  NOT NULL,
    content_type                VARCHAR(255)  NULL,
    file_size                   BIGINT        NULL,
    temp_file_path              VARCHAR(1024) NOT NULL,
    suggested_knowledge_base_name VARCHAR(255) NULL,
    suggested_document_name     VARCHAR(255)  NULL,
    summary                     VARCHAR(1000) NULL,
    tags_json                   VARCHAR(2000) NULL,
    reason                      VARCHAR(1000) NULL,
    recommended_action          VARCHAR(64)   NULL,
    status                      VARCHAR(32)   NOT NULL DEFAULT 'ANALYZED',
    expires_at                  DATETIME      NULL,
    create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                     TINYINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_ai_document_analysis_user_id ON ai_document_analysis (user_id);
CREATE INDEX idx_ai_document_analysis_status ON ai_document_analysis (status);
CREATE INDEX idx_ai_document_analysis_expires_at ON ai_document_analysis (expires_at);
CREATE INDEX idx_ai_document_analysis_deleted ON ai_document_analysis (deleted);
