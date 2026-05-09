package com.airag.modules.chat.controller;

import com.airag.common.result.ApiResponse;
import com.airag.modules.auth.security.SecurityUtils;
import com.airag.modules.chat.dto.CreateSessionRequest;
import com.airag.modules.chat.dto.RenameSessionRequest;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatSessionService;
import com.airag.modules.chat.vo.ChatMessageVO;
import com.airag.modules.chat.vo.ChatSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    @PostMapping
    public ApiResponse<ChatSessionVO> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.success(chatSessionService.createSession(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<ChatSessionVO>> listSessions() {
        return ApiResponse.success(chatSessionService.listByUserId(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/{sessionId}/name")
    public ApiResponse<ChatSessionVO> renameSession(@PathVariable Long sessionId,
                                                    @Valid @RequestBody RenameSessionRequest request) {
        return ApiResponse.success(chatSessionService.renameSession(SecurityUtils.getCurrentUserId(), sessionId, request));
    }

    @PutMapping("/{sessionId}/pin")
    public ApiResponse<ChatSessionVO> togglePinSession(@PathVariable Long sessionId) {
        return ApiResponse.success(chatSessionService.togglePinSession(SecurityUtils.getCurrentUserId(), sessionId));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<ChatMessageVO>> listMessages(@PathVariable Long sessionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        chatSessionService.getValidSession(userId, sessionId);
        return ApiResponse.success(chatMessageService.listSessionMessages(userId, sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        chatSessionService.deleteSession(userId, sessionId);
        return ApiResponse.success("删除成功", null);
    }
}
