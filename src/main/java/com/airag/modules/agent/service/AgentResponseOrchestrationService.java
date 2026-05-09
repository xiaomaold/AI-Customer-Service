package com.airag.modules.agent.service;

import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AgentResponseOrchestrationService {

    void handleKnowledgeBaseListResponse(ChatSendRequest request,
                                         SseEmitter emitter,
                                         List<KnowledgeBaseVO> knowledgeBases);

    void handleDirectAnswerResponse(ChatSendRequest request,
                                    SseEmitter emitter,
                                    String answer,
                                    String referenceContent);

    void handleFinalAnswerResponse(ChatSendRequest request,
                                   SseEmitter emitter,
                                   String initialReferenceContent,
                                   AgentSynthesisResult synthesisResult);

    void sendText(SseEmitter emitter, String token);

    void sendReplace(SseEmitter emitter, String answer);

    void sendReference(SseEmitter emitter, String referenceContent);

    void sendDone(SseEmitter emitter);

    void sendError(SseEmitter emitter, String message);
}
