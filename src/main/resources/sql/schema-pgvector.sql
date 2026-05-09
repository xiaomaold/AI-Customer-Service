CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_document_chunk
(
    id               VARCHAR(64)  PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    document_id      BIGINT       NOT NULL,
    chunk_index      INT          NOT NULL,
    file_name        VARCHAR(255) NULL,
    content          TEXT         NOT NULL,
    embedding        VECTOR(1024) NOT NULL,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_document_chunk_knowledge_base_id ON kb_document_chunk (knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_chunk_document_id ON kb_document_chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_chunk_chunk_index ON kb_document_chunk (chunk_index);
CREATE INDEX IF NOT EXISTS idx_kb_document_chunk_embedding
    ON kb_document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
