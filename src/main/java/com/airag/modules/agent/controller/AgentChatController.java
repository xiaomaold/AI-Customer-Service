package com.airag.modules.agent.controller;

import com.airag.modules.agent.service.AgentChatService;
import com.airag.modules.auth.security.SecurityUtils;
import com.airag.modules.chat.dto.ChatSendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
public class AgentChatController {

    private final AgentChatService agentChatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody ChatSendRequest request) {
        request.setUserId(SecurityUtils.getCurrentUserId());
        return agentChatService.streamChat(request);
    }
}
