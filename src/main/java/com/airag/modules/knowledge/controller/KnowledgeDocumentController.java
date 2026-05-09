package com.airag.modules.knowledge.controller;

import com.airag.common.result.ApiResponse;
import com.airag.modules.auth.security.SecurityUtils;
import com.airag.modules.knowledge.dto.KnowledgeDocumentUploadResponse;
import com.airag.modules.knowledge.service.KnowledgeDocumentService;
import com.airag.modules.knowledge.vo.KnowledgeChunkVO;
import com.airag.modules.knowledge.vo.KnowledgeDocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('KB_ADMIN')")
    public ApiResponse<KnowledgeDocumentUploadResponse> upload(@RequestParam Long knowledgeBaseId,
                                                               @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(knowledgeDocumentService.upload(knowledgeBaseId, file));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeDocumentVO>> listDocuments(@RequestParam(required = false) Long knowledgeBaseId) {
        return ApiResponse.success(knowledgeDocumentService.listDocuments(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUser().getRoles(),
                knowledgeBaseId
        ));
    }

    @GetMapping("/{documentId}/chunks")
    public ApiResponse<List<KnowledgeChunkVO>> listChunks(@PathVariable Long documentId) {
        return ApiResponse.success(knowledgeDocumentService.listChunks(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUser().getRoles(),
                documentId
        ));
    }

    @PostMapping("/{documentId}/rebuild-index")
    @PreAuthorize("hasRole('KB_ADMIN')")
    public ApiResponse<KnowledgeDocumentUploadResponse> rebuildIndex(@PathVariable Long documentId) {
        return ApiResponse.success(knowledgeDocumentService.rebuildIndex(documentId));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('KB_ADMIN')")
    public ApiResponse<Void> deleteDocument(@PathVariable Long documentId) {
        knowledgeDocumentService.deleteDocument(documentId);
        return ApiResponse.success("删除成功", null);
    }
}
