package com.airag.modules.knowledge.controller;

import com.airag.common.result.ApiResponse;
import com.airag.modules.auth.security.SecurityUtils;
import com.airag.modules.knowledge.dto.CreateKnowledgeBaseRequest;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import com.airag.modules.knowledge.vo.UserKnowledgePermissionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge/bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @PreAuthorize("hasRole('KB_ADMIN')")
    public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseService.create(request));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> list() {
        return ApiResponse.success(knowledgeBaseService.list(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUser().getRoles()
        ));
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseVO> detail(@PathVariable Long knowledgeBaseId) {
        return ApiResponse.success(knowledgeBaseService.getAccessibleDetail(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUser().getRoles(),
                knowledgeBaseId
        ));
    }

    @GetMapping("/my-permissions")
    public ApiResponse<List<UserKnowledgePermissionVO>> myPermissions() {
        return ApiResponse.success(knowledgeBaseService.listAccessibleByUserId(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUser().getRoles()
        ));
    }

    @DeleteMapping("/{knowledgeBaseId}")
    @PreAuthorize("hasRole('KB_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long knowledgeBaseId) {
        knowledgeBaseService.delete(knowledgeBaseId);
        return ApiResponse.success("Deleted successfully", null);
    }
}
