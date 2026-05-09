package com.airag.modules.chat.service;

import java.util.List;

public interface KnowledgeRetrieverService {

    List<RetrievedChunk> retrieve(String question, int topK);

    List<RetrievedChunk> retrieve(String question, int topK, Long knowledgeBaseId);

    List<RetrievedChunk> retrieve(String question, int topK, List<Long> knowledgeBaseIds);

    record RetrievedChunk(String chunkId,
                          Long knowledgeBaseId,
                          Long documentId,
                          Integer chunkIndex,
                          String fileName,
                          String content,
                          Double score) {
    }
}
