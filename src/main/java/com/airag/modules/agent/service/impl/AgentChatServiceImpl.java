package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.AgentAnswerPostProcessor;
import com.airag.modules.agent.service.AgentAnswerSynthesisService;
import com.airag.modules.agent.service.AgentChatService;
import com.airag.modules.agent.service.AgentEvidenceBundle;
import com.airag.modules.agent.service.AgentEvidencePreparationService;
import com.airag.modules.agent.service.AgentExecutionTraceService;
import com.airag.modules.agent.service.AgentHistoryFormatterService;
import com.airag.modules.agent.service.AgentPromptProfileService;
import com.airag.modules.agent.service.AgentQuestionClassifier;
import com.airag.modules.agent.service.AgentResponseOrchestrationService;
import com.airag.modules.agent.service.AgentStreamingService;
import com.airag.modules.agent.service.AgentSynthesisResult;
import com.airag.modules.agent.service.ConversationContextResolver;
import com.airag.modules.agent.tools.KnowledgeAgentToolsFactory;
import com.airag.modules.agent.trace.AgentExecutionTrace;
import com.airag.modules.admin.service.MissedQuestionService;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.auth.security.SecurityUtils;
import com.airag.modules.chat.ai.CustomerSupportAgentAiService;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.MessageRoleEnum;
import com.airag.modules.chat.prompt.PromptTemplateService;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatSessionService;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private static final long SSE_TIMEOUT = 300_000L;

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final PromptTemplateService promptTemplateService;
    private final StreamingChatModel streamingChatModel;
    private final KnowledgeAgentToolsFactory knowledgeAgentToolsFactory;
    private final ConversationContextResolver conversationContextResolver;
    private final AgentQuestionClassifier agentQuestionClassifier;
    private final AgentPromptProfileService agentPromptProfileService;
    private final AgentAnswerPostProcessor agentAnswerPostProcessor;
    private final AgentAnswerSynthesisService agentAnswerSynthesisService;
    private final AgentEvidencePreparationService agentEvidencePreparationService;
    private final AgentExecutionTraceService agentExecutionTraceService;
    private final AgentHistoryFormatterService agentHistoryFormatterService;
    private final AgentResponseOrchestrationService agentResponseOrchestrationService;
    private final AgentStreamingService agentStreamingService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final MissedQuestionService missedQuestionService;

    @Qualifier("sseExecutor")
    private final Executor sseExecutor;

    @Value("${rag.chat.history-limit:10}")
    private Integer historyLimit;

    @Override
    public SseEmitter streamChat(ChatSendRequest request) {
        log.info("Unified agent chat start sessionId={}, knowledgeBaseId={}, userId={}, question={}",
                request.getSessionId(), request.getKnowledgeBaseId(), request.getUserId(),
                StrUtil.blankToDefault(request.getQuestion(), ""));
        chatSessionService.getValidSession(request.getUserId(), request.getSessionId());
        chatSessionService.updateSessionTitleIfNeeded(request.getUserId(), request.getSessionId(), request.getQuestion());
        chatMessageService.save(buildMessage(
                request.getSessionId(),
                request.getUserId(),
                MessageRoleEnum.USER.getCode(),
                request.getQuestion(),
                null
        ));
        chatSessionService.refreshSessionActiveTime(request.getSessionId());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        LoginUser loginUser = SecurityUtils.getCurrentUser();
        sseExecutor.execute(() -> executeStream(request, emitter, loginUser));
        return emitter;
    }

    private void executeStream(ChatSendRequest request, SseEmitter emitter, LoginUser loginUser) {
        if (agentQuestionClassifier.isKnowledgeBaseListQuestion(request.getQuestion())) {
            handleKnowledgeBaseListQuestion(request, emitter, loginUser);
            return;
        }

        List<ChatMessage> recentMessages = chatMessageService.listRecentMessages(request.getSessionId(), historyLimit);
        String history = agentHistoryFormatterService.formatHistory(recentMessages);
        RecentConversationContext conversationContext = conversationContextResolver.resolve(
                loginUser,
                request.getSessionId(),
                request.getKnowledgeBaseId(),
                recentMessages,
                request.getQuestion(),
                request.getCarryoverKnowledgeBaseName(),
                request.getCarryoverDocumentName()
        );
        EvidenceBundle evidenceBundle = enrichQuestionWithEvidence(
                request.getSessionId(),
                loginUser,
                conversationContext,
                request.getKnowledgeBaseId(),
                request.getRouteMode(),
                request.getRouteReason(),
                request.getRouteDomain(),
                request.getRouteIntent(),
                request.getExecutionProfile(),
                request.getExecutionDirective(),
                request.getExecutionTraceId(),
                request.getQuestion()
        );
        if (StrUtil.isNotBlank(evidenceBundle.directAnswer())) {
            log.info("Unified agent chat complete sessionId={}, userId={}, answerLength={}, directAnswer=true",
                    request.getSessionId(), request.getUserId(), evidenceBundle.directAnswer().length());
            agentResponseOrchestrationService.handleDirectAnswerResponse(
                    request,
                    emitter,
                    evidenceBundle.directAnswer(),
                    evidenceBundle.referenceContent()
            );
            return;
        }

        String effectiveQuestion = evidenceBundle.effectiveQuestion();
        try {
            agentResponseOrchestrationService.sendReference(emitter, evidenceBundle.referenceContent());
            String systemPrompt = agentPromptProfileService.buildSystemPrompt(
                    promptTemplateService.unifiedAgentSystemPrompt(),
                    request.getExecutionProfile(),
                    request.getExecutionDirective()
            );
            CustomerSupportAgentAiService agentAiService = AiServices.builder(CustomerSupportAgentAiService.class)
                    .streamingChatModel(streamingChatModel)
                    .tools(knowledgeAgentToolsFactory.create(loginUser, conversationContext))
                    .build();

            String answer = agentStreamingService.streamAnswer(
                    agentAiService,
                    systemPrompt,
                    history,
                    effectiveQuestion,
                    emitter,
                    true
            );
            AgentSynthesisResult synthesisResult = agentAnswerSynthesisService.synthesize(
                    request.getQuestion(),
                    history,
                    effectiveQuestion,
                    answer,
                    request.getExecutionProfile(),
                    evidenceBundle.referenceContent(),
                    retryQuestion -> agentStreamingService.streamAnswer(
                            agentAiService,
                            systemPrompt,
                            history,
                            effectiveQuestion + "\n\n" + retryQuestion,
                            null,
                            false
                    )
            );
            String finalAnswer = synthesisResult.finalAnswer();
            agentExecutionTraceService.trace(AgentExecutionTrace.builder()
                    .stage("SYNTHESIS_COMPLETED")
                    .traceId(request.getExecutionTraceId())
                    .sessionId(request.getSessionId())
                    .userId(request.getUserId())
                    .executionProfile(request.getExecutionProfile())
                    .answerReplaced(synthesisResult.answerReplaced())
                    .build());

            log.info("Unified agent chat complete sessionId={}, userId={}, answerLength={}",
                    request.getSessionId(), request.getUserId(), finalAnswer.length());
            recordMissedQuestionIfNeeded(
                    request,
                    finalAnswer,
                    agentAnswerPostProcessor.buildMissReason(finalAnswer, effectiveQuestion)
            );
            agentResponseOrchestrationService.handleFinalAnswerResponse(
                    request,
                    emitter,
                    evidenceBundle.referenceContent(),
                    synthesisResult
            );
        } catch (Exception exception) {
            log.error("Agent SSE failed", exception);
            agentResponseOrchestrationService.sendError(emitter, "回答生成失败，请稍后重试");
        }
    }

    private void handleKnowledgeBaseListQuestion(ChatSendRequest request, SseEmitter emitter, LoginUser loginUser) {
        try {
            List<KnowledgeBaseVO> knowledgeBases = knowledgeBaseService.list(loginUser.getUserId(), loginUser.getRoles());
            log.info("Programmatic knowledge base list answer sessionId={}, userId={}, count={}",
                    request.getSessionId(), request.getUserId(), knowledgeBases.size());
            agentResponseOrchestrationService.handleKnowledgeBaseListResponse(request, emitter, knowledgeBases);
        } catch (Exception exception) {
            log.error("Programmatic knowledge base list answer failed", exception);
            agentResponseOrchestrationService.sendError(emitter, "获取知识库列表失败，请稍后重试");
        }
    }

    private EvidenceBundle enrichQuestionWithEvidence(Long sessionId,
                                                      LoginUser loginUser,
                                                      RecentConversationContext conversationContext,
                                                      Long requestedKnowledgeBaseId,
                                                      String routeMode,
                                                      String routeReason,
                                                      String routeDomain,
                                                      String routeIntent,
                                                      String executionProfile,
                                                      String executionDirective,
                                                      String executionTraceId,
                                                      String question) {
        AgentEvidenceBundle bundle = agentEvidencePreparationService.prepare(
                loginUser,
                conversationContext,
                requestedKnowledgeBaseId,
                routeMode,
                routeReason,
                routeDomain,
                routeIntent,
                executionProfile,
                executionDirective,
                question
        );
        agentExecutionTraceService.trace(AgentExecutionTrace.builder()
                .stage("EVIDENCE_PREPARED")
                .traceId(executionTraceId)
                .sessionId(sessionId)
                .userId(loginUser.getUserId())
                .executionProfile(executionProfile)
                .knowledgeEvidenceUsed(bundle.knowledgeEvidenceUsed())
                .documentEvidenceUsed(bundle.documentEvidenceUsed())
                .directAnswerUsed(bundle.directAnswerUsed())
                .build());
        return new EvidenceBundle(
                bundle.effectiveQuestion(),
                bundle.referenceContent(),
                bundle.directAnswer(),
                bundle.knowledgeEvidenceUsed(),
                bundle.documentEvidenceUsed(),
                bundle.directAnswerUsed()
        );
    }

    private void recordMissedQuestionIfNeeded(ChatSendRequest request, String finalAnswer, String missReason) {
        if (StrUtil.isBlank(missReason)) {
            return;
        }
        missedQuestionService.recordMissedQuestion(
                request.getUserId(),
                request.getSessionId(),
                request.getKnowledgeBaseId(),
                StrUtil.blankToDefault(request.getRouteMode(), "UNIFIED_AGENT"),
                request.getQuestion(),
                finalAnswer,
                missReason
        );
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

    private record EvidenceBundle(String effectiveQuestion,
                                  String referenceContent,
                                  String directAnswer,
                                  boolean knowledgeEvidenceUsed,
                                  boolean documentEvidenceUsed,
                                  boolean directAnswerUsed) {
    }
}
