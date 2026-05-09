package com.airag.modules.knowledge.store;

import com.airag.common.exception.BusinessException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PgVectorKnowledgeEmbeddingStore implements EmbeddingStore<TextSegment> {

    public static final String METADATA_CHUNK_ID = "chunkId";
    public static final String METADATA_DOCUMENT_ID = "documentId";
    public static final String METADATA_KNOWLEDGE_BASE_ID = "knowledgeBaseId";
    public static final String METADATA_CHUNK_INDEX = "chunkIndex";
    public static final String METADATA_FILE_NAME = "fileName";

    private final JdbcTemplate jdbcTemplate;
    private final int embeddingDimension;

    public PgVectorKnowledgeEmbeddingStore(JdbcTemplate jdbcTemplate, int embeddingDimension) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingDimension = embeddingDimension;
    }

    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("请使用 add(embedding, textSegment) 或 addAll(ids, embeddings, embedded)");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("请使用 addAll(ids, embeddings, embedded)");
    }

    @Override
    public String add(Embedding embedding, TextSegment embedded) {
        String id = java.util.UUID.randomUUID().toString();
        addAll(List.of(id), List.of(embedding), List.of(embedded));
        return id;
    }

    public void add(String id, Embedding embedding, TextSegment embedded) {
        addAll(List.of(id), List.of(embedding), List.of(embedded));
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("请使用 addAll(embeddings, embedded)");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = new ArrayList<>(embedded.size());
        for (int i = 0; i < embedded.size(); i++) {
            ids.add(java.util.UUID.randomUUID().toString());
        }
        addAll(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (ids.size() != embeddings.size() || embeddings.size() != embedded.size()) {
            throw new BusinessException("向量入库参数数量不一致");
        }
        String sql = """
                INSERT INTO kb_document_chunk
                (id, knowledge_base_id, document_id, chunk_index, file_name, content, embedding, create_time)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS vector), CURRENT_TIMESTAMP)
                """;
        List<Object[]> batchArgs = new ArrayList<>(ids.size());
        for (int index = 0; index < ids.size(); index++) {
            TextSegment segment = embedded.get(index);
            Metadata metadata = segment.metadata();
            Long knowledgeBaseId = metadata.getLong(METADATA_KNOWLEDGE_BASE_ID);
            Long documentId = metadata.getLong(METADATA_DOCUMENT_ID);
            Integer chunkIndex = metadata.getInteger(METADATA_CHUNK_INDEX);
            String fileName = metadata.getString(METADATA_FILE_NAME);
            if (knowledgeBaseId == null || documentId == null || chunkIndex == null) {
                throw new BusinessException("文本分段元数据缺少 knowledgeBaseId、documentId 或 chunkIndex");
            }
            batchArgs.add(new Object[]{
                    ids.get(index),
                    knowledgeBaseId,
                    documentId,
                    chunkIndex,
                    fileName,
                    segment.text(),
                    toVectorLiteral(embeddings.get(index).vector())
            });
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream().map(item -> "?").collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM kb_document_chunk WHERE id IN (" + placeholders + ")", ids.toArray());
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        String sql = """
                SELECT id,
                       knowledge_base_id,
                       document_id,
                       chunk_index,
                       file_name,
                       content,
                       embedding::text AS embedding_text,
                       1 - (embedding <=> CAST(? AS vector)) AS score
                FROM kb_document_chunk
                WHERE 1 - (embedding <=> CAST(? AS vector)) >= ?
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """;
        String queryVector = toVectorLiteral(request.queryEmbedding().vector());
        List<EmbeddingMatch<TextSegment>> matches = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapEmbeddingMatch(
                        rs.getString("id"),
                        rs.getLong("knowledge_base_id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("file_name"),
                        rs.getString("content"),
                        rs.getString("embedding_text"),
                        rs.getDouble("score")
                ),
                queryVector,
                queryVector,
                request.minScore(),
                queryVector,
                request.maxResults()
        );
        return new EmbeddingSearchResult<>(matches);
    }

    public List<EmbeddingMatch<TextSegment>> keywordSearch(String query, int limit) {
        return keywordSearchByKnowledgeBaseIds(query, limit, null);
    }

    public List<EmbeddingMatch<TextSegment>> keywordSearchByKnowledgeBaseId(String query, int limit, Long knowledgeBaseId) {
        return keywordSearchByKnowledgeBaseIds(query, limit, knowledgeBaseId == null ? null : List.of(knowledgeBaseId));
    }

    public List<EmbeddingMatch<TextSegment>> keywordSearchByKnowledgeBaseIds(String query, int limit, List<Long> knowledgeBaseIds) {
        List<String> keywords = extractKeywordTerms(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        StringBuilder scoreBuilder = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (String keyword : keywords) {
            if (!scoreBuilder.isEmpty()) {
                scoreBuilder.append(" + ");
            }
            scoreBuilder.append("(CASE WHEN file_name ILIKE ? THEN 6 ELSE 0 END)")
                    .append(" + ")
                    .append("(CASE WHEN content ILIKE ? THEN 4 ELSE 0 END)");
            String pattern = "%" + keyword + "%";
            params.add(pattern);
            params.add(pattern);
        }

        StringBuilder whereBuilder = new StringBuilder(" WHERE (");
        for (int index = 0; index < keywords.size(); index++) {
            if (index > 0) {
                whereBuilder.append(" OR ");
            }
            whereBuilder.append("file_name ILIKE ? OR content ILIKE ?");
            String pattern = "%" + keywords.get(index) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        whereBuilder.append(")");

        if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
            String placeholders = knowledgeBaseIds.stream().map(id -> "?").collect(Collectors.joining(","));
            whereBuilder.append(" AND knowledge_base_id IN (").append(placeholders).append(")");
            params.addAll(knowledgeBaseIds);
        }

        String sql = """
                SELECT id,
                       knowledge_base_id,
                       document_id,
                       chunk_index,
                       file_name,
                       content,
                       embedding::text AS embedding_text,
                       (%s) AS score
                FROM kb_document_chunk
                %s
                ORDER BY score DESC, chunk_index ASC
                LIMIT ?
                """.formatted(scoreBuilder, whereBuilder);
        params.add(limit);

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapEmbeddingMatch(
                rs.getString("id"),
                rs.getLong("knowledge_base_id"),
                rs.getLong("document_id"),
                rs.getInt("chunk_index"),
                rs.getString("file_name"),
                rs.getString("content"),
                rs.getString("embedding_text"),
                rs.getDouble("score")
        ), params.toArray());
    }

    public void removeByDocumentId(Long documentId) {
        jdbcTemplate.update("DELETE FROM kb_document_chunk WHERE document_id = ?", documentId);
    }

    public List<TextSegment> listByDocumentId(Long documentId) {
        String sql = """
                SELECT id, knowledge_base_id, document_id, chunk_index, file_name, content
                FROM kb_document_chunk
                WHERE document_id = ?
                ORDER BY chunk_index
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Metadata metadata = new Metadata()
                    .put(METADATA_CHUNK_ID, rs.getString("id"))
                    .put(METADATA_KNOWLEDGE_BASE_ID, rs.getLong("knowledge_base_id"))
                    .put(METADATA_DOCUMENT_ID, rs.getLong("document_id"))
                    .put(METADATA_CHUNK_INDEX, rs.getInt("chunk_index"));
            if (rs.getString("file_name") != null) {
                metadata.put(METADATA_FILE_NAME, rs.getString("file_name"));
            }
            return TextSegment.from(rs.getString("content"), metadata);
        }, documentId);
    }

    public List<EmbeddingMatch<TextSegment>> searchByKnowledgeBaseId(EmbeddingSearchRequest request, Long knowledgeBaseId) {
        return searchByKnowledgeBaseIds(request, List.of(knowledgeBaseId));
    }

    public List<EmbeddingMatch<TextSegment>> searchByKnowledgeBaseIds(EmbeddingSearchRequest request, List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }

        String placeholders = knowledgeBaseIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT id,
                       knowledge_base_id,
                       document_id,
                       chunk_index,
                       file_name,
                       content,
                       embedding::text AS embedding_text,
                       1 - (embedding <=> CAST(? AS vector)) AS score
                FROM kb_document_chunk
                WHERE knowledge_base_id IN (%s)
                  AND 1 - (embedding <=> CAST(? AS vector)) >= ?
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """.formatted(placeholders);
        String queryVector = toVectorLiteral(request.queryEmbedding().vector());
        List<Object> params = new ArrayList<>();
        params.add(queryVector);
        params.addAll(knowledgeBaseIds);
        params.add(queryVector);
        params.add(request.minScore());
        params.add(queryVector);
        params.add(request.maxResults());

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapEmbeddingMatch(
                rs.getString("id"),
                rs.getLong("knowledge_base_id"),
                rs.getLong("document_id"),
                rs.getInt("chunk_index"),
                rs.getString("file_name"),
                rs.getString("content"),
                rs.getString("embedding_text"),
                rs.getDouble("score")
        ), params.toArray());
    }

    private EmbeddingMatch<TextSegment> mapEmbeddingMatch(String id,
                                                          Long knowledgeBaseId,
                                                          Long documentId,
                                                          Integer chunkIndex,
                                                          String fileName,
                                                          String content,
                                                          String embeddingText,
                                                          Double score) {
        Metadata metadata = new Metadata()
                .put(METADATA_CHUNK_ID, id)
                .put(METADATA_KNOWLEDGE_BASE_ID, knowledgeBaseId)
                .put(METADATA_DOCUMENT_ID, documentId)
                .put(METADATA_CHUNK_INDEX, chunkIndex);
        if (fileName != null) {
            metadata.put(METADATA_FILE_NAME, fileName);
        }
        TextSegment segment = TextSegment.from(content, metadata);
        Embedding embedding = Embedding.from(parseVector(embeddingText));
        return new EmbeddingMatch<>(score, id, embedding, segment);
    }

    private List<String> extractKeywordTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = query.trim()
                .replace("帮我", "")
                .replace("给我", "")
                .replace("请帮我", "")
                .replace("生成", "")
                .replace("写一个", "")
                .replace("写一份", "")
                .replace("做一个", "")
                .replace("做一份", "")
                .replace("弄一个", "")
                .replace("弄一份", "")
                .replace("模板", "")
                .replace("表格", "")
                .replace("表单", "")
                .replace("申请表", "")
                .replace("申请书", "")
                .replace("是什么", "")
                .replace("有哪些", "")
                .replace("规则", " 规则 ")
                .replace("流程", " 流程 ")
                .replace("制度", " 制度 ")
                .replace("政策", " 政策 ")
                .replace("说明", " 说明 ")
                .replaceAll("[，。；：、,.!?？\\s]+", " ")
                .trim();

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (!query.isBlank()) {
            keywords.add(query.trim());
        }
        for (String token : normalized.split("\\s+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= 2) {
                keywords.add(trimmed);
            }
        }
        return new ArrayList<>(keywords);
    }

    private String toVectorLiteral(float[] vector) {
        validateVectorDimension(vector);
        String content = IntStream.range(0, vector.length)
                .mapToObj(index -> String.valueOf(vector[index]))
                .collect(Collectors.joining(","));
        return "[" + content + "]";
    }

    private void validateVectorDimension(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new BusinessException("向量内容不能为空");
        }
        if (vector.length != embeddingDimension) {
            throw new BusinessException(
                    "向量维度不匹配：期望 %d 维，实际为 %d 维".formatted(embeddingDimension, vector.length)
            );
        }
    }

    private float[] parseVector(String vectorText) {
        String normalized = vectorText == null ? "" : vectorText.replace("[", "").replace("]", "").trim();
        if (normalized.isEmpty()) {
            return new float[0];
        }
        String[] parts = normalized.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }
}
