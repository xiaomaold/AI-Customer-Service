package com.airag.modules.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.airag.modules.admin.service.MissedQuestionService;
import com.airag.modules.chat.ai.CustomerSupportAiService;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.MessageRoleEnum;
import com.airag.modules.chat.prompt.PromptTemplateService;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatSessionService;
import com.airag.modules.chat.service.KnowledgeRetrieverService;
import com.airag.modules.chat.service.RagChatService;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatServiceImpl implements RagChatService {

    private static final Long SSE_TIMEOUT = 300000L;

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final KnowledgeRetrieverService knowledgeRetrieverService;
    private final CustomerSupportAiService customerSupportAiService;
    private final PromptTemplateService promptTemplateService;
    private final MissedQuestionService missedQuestionService;

    @Qualifier("sseExecutor")
    private final Executor sseExecutor;

    @Value("${rag.chat.history-limit:10}")
    private Integer historyLimit;

    @Value("${rag.chat.retrieval-top-k:5}")
    private Integer retrievalTopK;

    @Value("${rag.chat.model-name:qwen-turbo}")
    private String modelName;

    @Override
    public SseEmitter streamChat(ChatSendRequest request) {
        chatSessionService.getValidSession(request.getUserId(), request.getSessionId());
        chatSessionService.updateSessionTitleIfNeeded(request.getUserId(), request.getSessionId(), request.getQuestion());
        ChatMessage userMessage = buildMessage(
                request.getSessionId(),
                request.getUserId(),
                MessageRoleEnum.USER.getCode(),
                request.getQuestion(),
                null
        );
        chatMessageService.save(userMessage);
        chatSessionService.refreshSessionActiveTime(request.getSessionId());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sseExecutor.execute(() -> executeStream(request, emitter));
        return emitter;
    }

    private void executeStream(ChatSendRequest request, SseEmitter emitter) {
        List<KnowledgeRetrieverService.RetrievedChunk> chunks =
                knowledgeRetrieverService.retrieve(request.getQuestion(), retrievalTopK, request.getKnowledgeBaseId());
        String knowledge = formatKnowledge(chunks);
        String history = formatHistory(chatMessageService.listRecentMessages(request.getSessionId(), historyLimit));
        StringBuilder answerBuilder = new StringBuilder();

        try {
            TokenStream tokenStream = customerSupportAiService.chat(
                    promptTemplateService.ragSystemPrompt(),
                    knowledge,
                    history,
                    request.getQuestion()
            );
            tokenStream.onPartialResponse(token -> {
                        answerBuilder.append(token);
                        sendText(emitter, token);
                    })
                    .onCompleteResponse(response -> {
                        String referenceSummary = buildReferenceSummary(chunks);
                        recordMissedQuestionIfNeeded(request, answerBuilder.toString(), chunks);
                        saveAssistantMessage(request, answerBuilder.toString(), referenceSummary);
                        sendReference(emitter, referenceSummary);
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .onError(error -> {
                        log.error("流式对话失败", error);
                        sendError(emitter, "模型调用失败，请稍后重试");
                    })
                    .start();
        } catch (Exception exception) {
            log.error("SSE 对话处理异常", exception);
            sendError(emitter, "系统繁忙，请稍后重试");
        }
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
        assistantMessage.setTokenCount(answer.length());
        chatMessageService.save(assistantMessage);
        chatSessionService.refreshSessionActiveTime(request.getSessionId());
    }

    private void recordMissedQuestionIfNeeded(ChatSendRequest request,
                                              String answer,
                                              List<KnowledgeRetrieverService.RetrievedChunk> chunks) {
        String missReason = buildMissReason(answer, chunks);
        if (missReason == null) {
            return;
        }
        missedQuestionService.recordMissedQuestion(
                request.getUserId(),
                request.getSessionId(),
                request.getKnowledgeBaseId(),
                "RAG",
                request.getQuestion(),
                answer,
                missReason
        );
    }

    private String buildMissReason(String answer, List<KnowledgeRetrieverService.RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "NO_RETRIEVED_CHUNKS";
        }
        if (StrUtil.isBlank(answer)) {
            return "EMPTY_ANSWER";
        }
        if (answer.contains("未检索到") || answer.contains("未确认") || answer.contains("查不到")) {
            return "ANSWER_STILL_UNRESOLVED";
        }
        return null;
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

    private String formatKnowledge(List<KnowledgeRetrieverService.RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "未检索到可直接参考的知识内容。";
        }
        StringBuilder builder = new StringBuilder();
        for (KnowledgeRetrieverService.RetrievedChunk chunk : chunks) {
            builder.append("片段编号：")
                    .append(chunk.chunkId())
                    .append("，文档编号：")
                    .append(chunk.documentId())
                    .append("，相似度：")
                    .append(String.format("%.4f", chunk.score()))
                    .append("\n")
                    .append(chunk.content())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String buildReferenceSummary(List<KnowledgeRetrieverService.RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return null;
        }

        Map<String, Integer> references = new LinkedHashMap<>();
        for (KnowledgeRetrieverService.RetrievedChunk chunk : chunks) {
            String key = StrUtil.blankToDefault(
                    chunk.fileName(),
                    "文档 " + chunk.documentId()
            );
            references.merge(key, 1, Integer::sum);
        }

        StringBuilder builder = new StringBuilder("本次回复参考了以下资料：");
        int index = 0;
        for (Map.Entry<String, Integer> entry : references.entrySet()) {
            if (index > 0) {
                builder.append("、");
            }
            builder.append(entry.getKey());
            index++;
            if (index >= 3) {
                break;
            }
        }
        if (references.size() > 3) {
            builder.append("等资料");
        }
        return builder.toString();
    }

    private String formatHistory(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "暂无历史会话。";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            builder.append("[")
                    .append(message.getRole())
                    .append("] ")
                    .append(message.getContent())
                    .append("\n");
        }
        return builder.toString();
    }

    private void sendText(SseEmitter emitter, String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(token));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException exception) {
            log.warn("发送 SSE 完成事件失败", exception);
        }
    }

    private void sendReference(SseEmitter emitter, String referenceSummary) {
        if (StrUtil.isBlank(referenceSummary)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("reference").data(referenceSummary));
        } catch (IOException exception) {
            log.warn("发送 SSE 参考来源事件失败", exception);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException exception) {
            log.warn("发送 SSE 错误事件失败", exception);
        } finally {
            emitter.completeWithError(new RuntimeException(message));
        }
    }
}
