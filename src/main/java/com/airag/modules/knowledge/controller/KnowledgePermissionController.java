package com.airag.modules.knowledge.controller;

import com.airag.common.result.ApiResponse;
import com.airag.modules.knowledge.dto.GrantKnowledgePermissionRequest;
import com.airag.modules.knowledge.service.KnowledgePermissionService;
import com.airag.modules.knowledge.vo.KnowledgePermissionGrantVO;
import com.airag.modules.knowledge.vo.KnowledgePermissionUserVO;
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
@RequestMapping("/api/knowledge")
@PreAuthorize("hasRole('KB_ADMIN')")
public class KnowledgePermissionController {

    private final KnowledgePermissionService knowledgePermissionService;

    @GetMapping("/permissions/users")
    public ApiResponse<List<KnowledgePermissionUserVO>> listAssignableUsers() {
        return ApiResponse.success(knowledgePermissionService.listAssignableUsers());
    }

    @GetMapping("/bases/{knowledgeBaseId}/permissions")
    public ApiResponse<List<KnowledgePermissionGrantVO>> listGrants(@PathVariable Long knowledgeBaseId) {
        return ApiResponse.success(knowledgePermissionService.listGrants(knowledgeBaseId));
    }

    @PostMapping("/bases/{knowledgeBaseId}/permissions")
    public ApiResponse<Void> grant(@PathVariable Long knowledgeBaseId,
                                   @Valid @RequestBody GrantKnowledgePermissionRequest request) {
        knowledgePermissionService.grant(knowledgeBaseId, request);
        return ApiResponse.success("授权成功", null);
    }

    @DeleteMapping("/bases/{knowledgeBaseId}/permissions/{userId}")
    public ApiResponse<Void> revoke(@PathVariable Long knowledgeBaseId, @PathVariable Long userId) {
        knowledgePermissionService.revoke(knowledgeBaseId, userId);
        return ApiResponse.success("取消授权成功", null);
    }
}
