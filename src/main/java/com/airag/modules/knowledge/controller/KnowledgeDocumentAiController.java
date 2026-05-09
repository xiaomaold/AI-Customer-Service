package com.airag.modules.knowledge.controller;

import com.airag.common.result.ApiResponse;
import com.airag.modules.auth.security.SecurityUtils;
import com.airag.modules.knowledge.dto.AiDocumentAnalyzeResponse;
import com.airag.modules.knowledge.dto.AiDocumentUploadConfirmRequest;
import com.airag.modules.knowledge.dto.KnowledgeDocumentUploadResponse;
import com.airag.modules.knowledge.service.AiDocumentIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge/documents/ai")
public class KnowledgeDocumentAiController {

    private final AiDocumentIngestionService aiDocumentIngestionService;

    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    public ApiResponse<AiDocumentAnalyzeResponse> analyze(@RequestParam(required = false) Long knowledgeBaseId,
                                                          @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(aiDocumentIngestionService.analyze(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUser().getRoles(),
                knowledgeBaseId,
                file
        ));
    }

    @PostMapping(value = "/execute", consumes = "multipart/form-data")
    public ApiResponse<AiDocumentAnalyzeResponse> execute(@RequestParam(required = false) Long knowledgeBaseId,
                                                          @RequestParam(required = false) String instruction,
                                                          @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(aiDocumentIngestionService.execute(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUser().getRoles(),
                knowledgeBaseId,
                instruction,
                file
        ));
    }

    @PostMapping("/confirm-upload")
    public ApiResponse<KnowledgeDocumentUploadResponse> confirmUpload(@Valid @RequestBody AiDocumentUploadConfirmRequest request) {
        return ApiResponse.success(aiDocumentIngestionService.confirmUpload(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUser().getRoles(),
                request
        ));
    }
}
