ALTER TABLE chat_session
    ADD COLUMN pinned TINYINT NOT NULL DEFAULT 0 AFTER session_status;

CREATE INDEX idx_chat_session_pinned ON chat_session (pinned);
