package com.airag.modules.knowledge.service;

import com.airag.modules.knowledge.dto.AiDocumentAnalyzeResponse;
import com.airag.modules.knowledge.dto.AiDocumentUploadConfirmRequest;
import com.airag.modules.knowledge.dto.KnowledgeDocumentUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AiDocumentIngestionService {

    AiDocumentAnalyzeResponse analyze(Long userId, List<String> roles, Long knowledgeBaseId, MultipartFile file);

    AiDocumentAnalyzeResponse execute(Long userId,
                                      List<String> roles,
                                      Long knowledgeBaseId,
                                      String instruction,
                                      MultipartFile file);

    KnowledgeDocumentUploadResponse confirmUpload(Long userId, List<String> roles, AiDocumentUploadConfirmRequest request);
}
