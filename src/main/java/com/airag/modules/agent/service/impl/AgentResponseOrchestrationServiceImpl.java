package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentResponseOrchestrationService;
import com.airag.modules.agent.service.AgentSynthesisResult;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.MessageRoleEnum;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatSessionService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentResponseOrchestrationServiceImpl implements AgentResponseOrchestrationService {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    @Value("${rag.chat.model-name:qwen-turbo}")
    private String modelName;

    @Override
    public void handleKnowledgeBaseListResponse(ChatSendRequest request,
                                                SseEmitter emitter,
                                                List<KnowledgeBaseVO> knowledgeBases) {
        String answer = buildKnowledgeBaseListAnswer(knowledgeBases);
        sendText(emitter, answer);
        saveAssistantMessage(request, answer, null);
        sendDone(emitter);
        emitter.complete();
    }

    @Override
    public void handleDirectAnswerResponse(ChatSendRequest request,
                                           SseEmitter emitter,
                                           String answer,
                                           String referenceContent) {
        sendReference(emitter, referenceContent);
        sendText(emitter, answer);
        saveAssistantMessage(request, answer, referenceContent);
        sendDone(emitter);
        emitter.complete();
    }

    @Override
    public void handleFinalAnswerResponse(ChatSendRequest request,
                                          SseEmitter emitter,
                                          String initialReferenceContent,
                                          AgentSynthesisResult synthesisResult) {
        if (synthesisResult.answerReplaced()) {
            sendReplace(emitter, synthesisResult.finalAnswer());
        }
        if (!StrUtil.equals(initialReferenceContent, synthesisResult.finalReferenceContent())) {
            sendReference(emitter, synthesisResult.finalReferenceContent());
        }
        saveAssistantMessage(request, synthesisResult.finalAnswer(), synthesisResult.finalReferenceContent());
        sendDone(emitter);
        emitter.complete();
    }

    @Override
    public void sendText(SseEmitter emitter, String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(token));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void sendReplace(SseEmitter emitter, String answer) {
        if (StrUtil.isBlank(answer)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("replace").data(answer));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void sendReference(SseEmitter emitter, String referenceContent) {
        if (StrUtil.isBlank(referenceContent)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("reference").data(referenceContent));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException exception) {
            log.warn("Send agent done event failed", exception);
        }
    }

    @Override
    public void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException exception) {
            log.warn("Send agent error event failed", exception);
        } finally {
            emitter.completeWithError(new RuntimeException(message));
        }
    }

    private String buildKnowledgeBaseListAnswer(List<KnowledgeBaseVO> knowledgeBases) {
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return "你当前还没有可查看的知识库。";
        }

        String names = knowledgeBases.stream()
                .map(KnowledgeBaseVO::getKnowledgeBaseName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));

        return names + "\n\n以上是你当前可查看的全部知识库，共 " + knowledgeBases.size() + " 个。";
    }

    private void saveAssistantMessage(ChatSendRequest request, String answer, String referenceContent) {
        ChatMessage assistantMessage = buildMessage(
                request.getSessionId(),
                request.getUserId(),
                MessageRoleEnum.ASSISTANT.getCode(),
                answer,
                referenceContent
        );
        assistantMessage.setModelName(modelName);
        assistantMessage.setTokenCount(answer == null ? 0 : answer.length());
        chatMessageService.save(assistantMessage);
        chatSessionService.refreshSessionActiveTime(request.getSessionId());
    }

    private ChatMessage buildMessage(Long sessionId, Long userId, String role, String content, String referenceContent) {
        LocalDateTime now = LocalDateTime.now();
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(IdUtil.getSnowflakeNextId());
        chatMessage.setSessionId(sessionId);
        chatMessage.setUserId(userId);
        chatMessage.setRole(role);
        chatMessage.setContent(content);
        chatMessage.setReferenceContent(referenceContent);
        chatMessage.setCreateTime(now);
        chatMessage.setUpdateTime(now);
        chatMessage.setDeleted(0);
        return chatMessage;
    }
}
