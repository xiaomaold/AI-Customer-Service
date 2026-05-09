package com.airag.modules.knowledge.service;

import com.airag.modules.knowledge.dto.KnowledgeDocumentUploadResponse;
import com.airag.modules.knowledge.vo.KnowledgeChunkVO;
import com.airag.modules.knowledge.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

public interface KnowledgeDocumentService {

    KnowledgeDocumentUploadResponse upload(Long knowledgeBaseId, MultipartFile file);

    KnowledgeDocumentUploadResponse uploadStoredFile(Long knowledgeBaseId,
                                                     String originalFilename,
                                                     String contentType,
                                                     Long fileSize,
                                                     Path sourcePath,
                                                     String documentName);

    List<KnowledgeDocumentVO> listDocuments(Long userId, List<String> roles, Long knowledgeBaseId);

    List<KnowledgeChunkVO> listChunks(Long userId, List<String> roles, Long documentId);

    void deleteDocument(Long documentId);

    KnowledgeDocumentUploadResponse rebuildIndex(Long documentId);
}
