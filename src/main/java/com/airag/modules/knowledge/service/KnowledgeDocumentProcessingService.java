package com.airag.modules.knowledge.service;

public interface KnowledgeDocumentProcessingService {

    void processUploadedDocument(Long documentId);

    void rebuildIndex(Long documentId);
}
