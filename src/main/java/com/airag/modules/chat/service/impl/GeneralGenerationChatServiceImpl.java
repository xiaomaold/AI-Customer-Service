package com.airag.modules.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.query.KnowledgeQueryPlanner;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.ai.GeneralGenerationAiService;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.ChatRouteModeEnum;
import com.airag.modules.chat.enums.MessageRoleEnum;
import com.airag.modules.chat.prompt.PromptTemplateService;
import com.airag.modules.chat.service.ChatAnswerPresentationService;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatSessionService;
import com.airag.modules.chat.service.GeneralGenerationChatService;
import com.airag.modules.chat.service.KnowledgeRetrieverService;
import com.airag.modules.integration.businesscenter.service.BusinessCenterActionService;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralGenerationChatServiceImpl implements GeneralGenerationChatService {

    private static final long SSE_TIMEOUT = 300_000L;
    private static final int HYBRID_RETRIEVAL_QUERY_LIMIT = 5;
    private static final Pattern PHONE_PATTERN = Pattern.compile("(400[- ]?\\d{3,4}[- ]?\\d{4}|1\\d{10}|(?:0\\d{2,3}[- ]?)?\\d{7,8})");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Map<String, List<String>> HYBRID_EVIDENCE_TERMS = Map.of(
            "退款", List.of("退款", "售后"),
            "请假", List.of("请假", "病假", "事假"),
            "报销", List.of("报销"),
            "客服", List.of("客服", "售后"),
            "审批", List.of("审批", "流程")
    );

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatAnswerPresentationService chatAnswerPresentationService;
    private final GeneralGenerationAiService generalGenerationAiService;
    private final KnowledgeRetrieverService knowledgeRetrieverService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PromptTemplateService promptTemplateService;
    private final KnowledgeQueryPlanner knowledgeQueryPlanner;
    private final BusinessCenterActionService businessCenterActionService;

    @Qualifier("sseExecutor")
    private final Executor sseExecutor;

    @Value("${rag.chat.history-limit:10}")
    private Integer historyLimit;

    @Value("${rag.chat.retrieval-top-k:4}")
    private Integer retrievalTopK;

    @Value("${rag.chat.model-name:qwen-turbo}")
    private String modelName;

    @Override
    public SseEmitter streamChat(ChatSendRequest request, LoginUser loginUser) {
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
        sseExecutor.execute(() -> executeStream(request, emitter, loginUser));
        return emitter;
    }

    private void executeStream(ChatSendRequest request, SseEmitter emitter, LoginUser loginUser) {
        List<ChatMessage> recentMessages = chatMessageService.listRecentMessages(request.getSessionId(), historyLimit);
        String history = formatHistory(recentMessages);
        ChatRouteModeEnum routeMode = routeMode(request);

        String businessClarificationAnswer = buildBusinessClarificationAnswerV2(request);
        if (StrUtil.isNotBlank(businessClarificationAnswer)) {
            executeDirectReplyStream(request, emitter, businessClarificationAnswer);
            return;
        }

        String directActionAnswer = buildDirectActionAnswer(request, recentMessages, loginUser);
        if (StrUtil.isNotBlank(directActionAnswer)) {
            executeDirectReplyStream(request, emitter, directActionAnswer);
            return;
        }
        if (shouldUseClarificationReply(request)) {
            executeClarificationStream(request, emitter);
            return;
        }

        RetrievalSelection retrievalSelection = retrieveKnowledge(request, loginUser, routeMode);
        String referenceContent = buildReferenceContent(request, retrievalSelection);

        if (routeMode == ChatRouteModeEnum.HYBRID_GENERATION
                && !hasEffectiveHybridEvidence(request.getQuestion(), retrievalSelection.chunks())) {
            String answer = buildHybridNoEvidenceAnswer(request.getQuestion());
            String finalReferenceContent = chatAnswerPresentationService.enrichReferenceContent(
                    request.getQuestion(),
                    answer,
                    referenceContent
            );
            log.info("Hybrid generation blocked by missing effective evidence sessionId={}, question={}, chunkCount={}, preferredKnowledgeBase={}",
                    request.getSessionId(),
                    StrUtil.blankToDefault(request.getQuestion(), ""),
                    retrievalSelection.chunks().size(),
                    StrUtil.blankToDefault(retrievalSelection.preferredKnowledgeBaseName(), ""));
            sendReference(emitter, finalReferenceContent);
            streamDirectChunks(emitter, answer);
            saveAssistantMessage(request, answer, finalReferenceContent);
            sendDone(emitter);
            emitter.complete();
            return;
        }

        String knowledge = formatKnowledge(retrievalSelection.chunks(), routeMode);
        StringBuilder answerBuilder = new StringBuilder();

        try {
            sendReference(emitter, referenceContent);
            TokenStream tokenStream = generalGenerationAiService.chat(
                    promptTemplateService.generalGenerationSystemPrompt(),
                    knowledge,
                    history,
                    request.getQuestion()
            );
            tokenStream.onPartialResponse(token -> {
                        answerBuilder.append(token);
                        sendText(emitter, token);
                    })
                    .onCompleteResponse(response -> {
                        String answer = answerBuilder.toString().trim();
                        String finalReferenceContent = chatAnswerPresentationService.enrichReferenceContent(
                                request.getQuestion(),
                                answer,
                                referenceContent
                        );
                        if (!StrUtil.equals(referenceContent, finalReferenceContent)) {
                            sendReference(emitter, finalReferenceContent);
                        }
                        saveAssistantMessage(request, answer, finalReferenceContent);
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .onError(error -> {
                        log.error("General generation stream failed routeMode={}, routeReason={}",
                                request.getRouteMode(), request.getRouteReason(), error);
                        sendError(emitter, "生成内容失败，请稍后重试。");
                    })
                    .start();
        } catch (Exception exception) {
            log.error("General generation execute failed routeMode={}, routeReason={}",
                    request.getRouteMode(), request.getRouteReason(), exception);
            sendError(emitter, "生成内容失败，请稍后重试。");
        }
    }

    private ChatRouteModeEnum routeMode(ChatSendRequest request) {
        try {
            return ChatRouteModeEnum.valueOf(
                    StrUtil.blankToDefault(request.getRouteMode(), ChatRouteModeEnum.GENERAL_GENERATION.name())
            );
        } catch (IllegalArgumentException ignored) {
            return ChatRouteModeEnum.GENERAL_GENERATION;
        }
    }

    private String buildDirectActionAnswer(ChatSendRequest request, List<ChatMessage> recentMessages, LoginUser loginUser) {
        String routeAction = StrUtil.blankToDefault(request.getRouteAction(), "");
        return switch (routeAction) {
            case "DOCUMENT_UPLOAD" -> buildDocumentUploadAnswer(request);
            case "DOCUMENT_ANALYZE" -> buildDocumentAnalyzeAnswer();
            case "PRODUCT_QUERY", "ORDER_SUBMISSION", "REFUND_REQUEST", "HUMAN_HANDOFF", "WORK_ORDER_SUBMISSION", "STATUS_QUERY" ->
                    businessCenterActionService.handle(request, recentMessages);
            default -> null;
        };
    }

    private String buildBusinessClarificationAnswer(ChatSendRequest request) {
        if (!"BUSINESS_CLARIFICATION".equals(StrUtil.blankToDefault(request.getRouteReason(), ""))) {
            return null;
        }
        String question = StrUtil.blankToDefault(request.getQuestion(), "");
        if (question.contains("退款") || question.contains("退货")) {
            return "请先确认一下，你是想了解退款规则，还是要提交退款申请？如果你要查规则，请直接说明“退款规则”或“退款流程”；如果你要发起退款，请直接说明“申请退款”，并提供订单号。";
        }
        if (question.contains("请假")) {
            return "请先确认一下，你是想了解请假制度，还是要提交请假工单？如果你要查规则，请直接说明“请假制度”或“请假流程”；如果你要提交工单，请直接说明请假天数和请假类型。";
        }
        if (question.contains("工单")) {
            return "请先确认一下，你是想了解工单处理规则，还是要提交工单？如果你要查规则，请直接说明“工单规则”或“工单流程”；如果你要提交工单，请直接说明要办理的业务和必要信息。";
        }
        if (question.contains("人工") || question.contains("客服")) {
            return "请先确认一下，你是想了解人工客服的联系方式，还是要我帮你转人工？如果你要查规则，请直接说明“人工客服联系方式”；如果你要转人工，请直接说明“转人工”。";
        }
        if (question.contains("订单") || question.contains("下单")) {
            return "请先确认一下，你是想了解下单规则，还是要提交订单？如果你要查规则，请直接说明“下单流程”或“订单规则”；如果你要提交订单，请直接说明产品号和购买数量。";
        }
        if (question.contains("产品") || question.contains("商品")) {
            return "请先确认一下，你是想了解产品规则说明，还是要查询具体产品信息？如果你要查具体产品，请直接说明“查看 P-1002 产品信息”这类表达。";
        }
        return "你的意思我还不能完全确认。请再明确一下，你是想查询规则说明，还是要发起具体业务动作。";
    }

    private String buildDocumentUploadAnswer(ChatSendRequest request) {
        if (request.getKnowledgeBaseId() != null) {
            return "可以，把文档拖到聊天区域，或点击“上传文档给 AI”选择文件。发送后我会优先按你当前限定的知识库帮你分析，并根据权限给出上传建议。";
        }
        return "可以，把文档拖到聊天区域，或点击“上传文档给 AI”选择文件。发送后我会先分析文档，再根据你的权限给出知识库建议或上传确认入口。";
    }

    private String buildDocumentAnalyzeAnswer() {
        return "可以，先把文档拖到聊天区域，或点击“上传文档给 AI”选择文件。发送后再告诉我你想怎么处理，比如总结内容、提取联系方式，或者推荐适合的知识库。";
    }

    private String buildBusinessActionAnswer(String prefix,
                                             ChatSendRequest request,
                                             LoginUser loginUser,
                                             String fallbackKeyword) {
        ContactResolution contacts = resolveBusinessContacts(request, loginUser, fallbackKeyword);
        if (contacts.isEmpty()) {
            return prefix + "如果这是企业内部事项，建议直接联系相关客服或处理渠道自行办理。";
        }
        return prefix + "你可以直接联系这些渠道自行处理：" + String.join("；", contacts.items()) + "。";
    }

    private ContactResolution resolveBusinessContacts(ChatSendRequest request, LoginUser loginUser, String fallbackKeyword) {
        if (loginUser == null) {
            return ContactResolution.empty();
        }
        List<Long> knowledgeBaseIds = resolveActionKnowledgeBaseIds(request, loginUser);
        if (knowledgeBaseIds.isEmpty()) {
            return ContactResolution.empty();
        }

        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(StrUtil.blankToDefault(request.getQuestion(), ""));
        queries.add("联系方式");
        queries.add("客服电话");
        queries.add("客服邮箱");
        queries.add("在线工单");
        queries.add("工单系统");
        if (StrUtil.isNotBlank(fallbackKeyword)) {
            queries.add(fallbackKeyword + "电话");
            queries.add(fallbackKeyword + "邮箱");
            queries.add(fallbackKeyword + "联系方式");
        }

        List<KnowledgeRetrieverService.RetrievedChunk> chunks = new ArrayList<>();
        for (String query : queries) {
            chunks.addAll(knowledgeRetrieverService.retrieve(query, 6, knowledgeBaseIds));
        }
        return extractContactResolution(chunks);
    }

    private List<Long> resolveActionKnowledgeBaseIds(ChatSendRequest request, LoginUser loginUser) {
        if (request.getKnowledgeBaseId() != null) {
            return List.of(request.getKnowledgeBaseId());
        }
        return knowledgeBaseService.list(loginUser.getUserId(), loginUser.getRoles()).stream()
                .map(KnowledgeBaseVO::getId)
                .limit(3)
                .toList();
    }

    private ContactResolution extractContactResolution(List<KnowledgeRetrieverService.RetrievedChunk> chunks) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (KnowledgeRetrieverService.RetrievedChunk chunk : chunks) {
            String content = StrUtil.blankToDefault(chunk.content(), "");

            String phone = firstPatternMatch(content, PHONE_PATTERN);
            if (StrUtil.isNotBlank(phone)) {
                items.add("电话：" + phone);
            }

            String email = firstPatternMatch(content, EMAIL_PATTERN);
            if (StrUtil.isNotBlank(email)) {
                items.add("邮箱：" + email);
            }

            for (String line : content.split("\\R")) {
                String trimmed = line.trim();
                if (StrUtil.isBlank(trimmed)) {
                    continue;
                }
                if (trimmed.contains("在线工单")
                        || trimmed.contains("工单系统")
                        || trimmed.contains("联系客服")
                        || trimmed.contains("客服邮箱")
                        || trimmed.contains("客服电话")) {
                    items.add(trimmed);
                }
                if (items.size() >= 4) {
                    break;
                }
            }
            if (items.size() >= 4) {
                break;
            }
        }
        return new ContactResolution(new ArrayList<>(items));
    }

    private String firstPatternMatch(String content, Pattern pattern) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
    }

    private boolean shouldUseClarificationReply(ChatSendRequest request) {
        String routeReason = StrUtil.blankToDefault(request.getRouteReason(), "");
        return "INVALID_INPUT".equals(routeReason) || "UNCERTAIN_REQUEST".equals(routeReason);
    }

    private void executeClarificationStream(ChatSendRequest request, SseEmitter emitter) {
        String referenceContent = buildReferenceContent(request, new RetrievalSelection(List.of(), null));
        StringBuilder answerBuilder = new StringBuilder();
        try {
            TokenStream tokenStream = generalGenerationAiService.chat(
                    buildClarificationSystemPrompt(request),
                    "",
                    "",
                    StrUtil.blankToDefault(request.getQuestion(), "")
            );
            tokenStream.onPartialResponse(token -> {
                        answerBuilder.append(token);
                        sendText(emitter, token);
                    })
                    .onCompleteResponse(response -> {
                        String answer = answerBuilder.toString().trim();
                        if (StrUtil.isBlank(answer)) {
                            answer = "我还没太理解你的意思，可以换一种说法再问我一次吗？我会继续帮你。";
                        }
                        saveAssistantMessage(request, answer, referenceContent);
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .onError(error -> {
                        log.error("Clarification stream failed routeReason={}", request.getRouteReason(), error);
                        String fallbackAnswer = "我还没太理解你的意思，可以换一种说法再问我一次吗？我会继续帮你。";
                        saveAssistantMessage(request, fallbackAnswer, referenceContent);
                        streamDirectChunks(emitter, fallbackAnswer);
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .start();
        } catch (Exception exception) {
            log.error("Clarification execute failed routeReason={}", request.getRouteReason(), exception);
            String fallbackAnswer = "我还没太理解你的意思，可以换一种说法再问我一次吗？我会继续帮你。";
            saveAssistantMessage(request, fallbackAnswer, referenceContent);
            streamDirectChunks(emitter, fallbackAnswer);
            sendDone(emitter);
            emitter.complete();
        }
    }

    private String buildClarificationSystemPrompt(ChatSendRequest request) {
        String routeReason = StrUtil.blankToDefault(request.getRouteReason(), "");
        String scenario = "INVALID_INPUT".equals(routeReason)
                ? "用户输入是低信号、无意义或难以理解的内容。"
                : "用户的问题过于模糊，暂时无法确认具体意图。";
        String knowledgeBaseContext = request.getKnowledgeBaseId() != null
                ? "用户当前已经限定了一个知识库，请轻描淡写地引导用户提出更具体的知识库相关问题，但不要直接声称你已经知道知识库里的答案。"
                : "用户当前没有限定知识库。";
        return """
                你是一个友好的企业 AI 助手。
                当前任务不是回答用户原问题，而是用自然、简洁的中文请用户换一种更清晰的说法。
                %s
                %s
                请严格遵守：
                1. 只输出 1 到 2 句简短回复。
                2. 不要把用户输入当成真实业务问题来解释或回答。
                3. 不要提到知识库、路由、模型、系统提示词。
                4. 语气友好、自然，避免每次都完全同一句话。
                5. 不要使用列表、标题或模板口吻。
                """.formatted(scenario, knowledgeBaseContext).trim();
    }

    private void executeDirectReplyStream(ChatSendRequest request, SseEmitter emitter, String answer) {
        String referenceContent = buildReferenceContent(request, new RetrievalSelection(List.of(), null));
        saveAssistantMessage(request, answer, referenceContent);
        streamDirectChunks(emitter, answer);
        sendDone(emitter);
        emitter.complete();
    }

    private void streamDirectChunks(SseEmitter emitter, String answer) {
        for (String chunk : splitDirectAnswer(answer)) {
            sendText(emitter, chunk);
            try {
                Thread.sleep(60L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<String> splitDirectAnswer(String answer) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char ch : answer.toCharArray()) {
            current.append(ch);
            if (isDirectAnswerBreakChar(ch) || current.length() >= 6) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private boolean isDirectAnswerBreakChar(char ch) {
        return ch == '，' || ch == '。' || ch == '？' || ch == '！'
                || ch == ',' || ch == '.' || ch == '?' || ch == '!';
    }

    private RetrievalSelection retrieveKnowledge(ChatSendRequest request, LoginUser loginUser, ChatRouteModeEnum routeMode) {
        if (!shouldRetrieveKnowledge(request, routeMode)) {
            return new RetrievalSelection(List.of(), null);
        }
        int topK = retrievalTopK == null || retrievalTopK <= 0 ? 4 : retrievalTopK;
        List<String> queries = buildRetrievalQueries(request.getQuestion(), routeMode);
        if (queries.isEmpty()) {
            return new RetrievalSelection(List.of(), null);
        }

        List<ScoredChunkCandidate> candidates = new ArrayList<>();
        if (request.getKnowledgeBaseId() != null) {
            KnowledgeBaseVO knowledgeBase = knowledgeBaseService.getAccessibleDetail(
                    loginUser.getUserId(),
                    loginUser.getRoles(),
                    request.getKnowledgeBaseId()
            );
            if (knowledgeBase == null) {
                return new RetrievalSelection(List.of(), null);
            }
            for (String query : queries) {
                List<KnowledgeRetrieverService.RetrievedChunk> chunks =
                        knowledgeRetrieverService.retrieve(query, topK, request.getKnowledgeBaseId());
                candidates.addAll(toCandidates(chunks, query, knowledgeBase));
            }
            return selectChunks(routeMode, request.getQuestion(), candidates, topK);
        }

        List<KnowledgeBaseVO> accessibleKnowledgeBases = knowledgeBaseService.list(loginUser.getUserId(), loginUser.getRoles());
        if (accessibleKnowledgeBases.isEmpty()) {
            return new RetrievalSelection(List.of(), null);
        }

        List<Long> accessibleKnowledgeBaseIds = accessibleKnowledgeBases.stream()
                .map(KnowledgeBaseVO::getId)
                .toList();
        Map<Long, KnowledgeBaseVO> knowledgeBaseById = accessibleKnowledgeBases.stream()
                .collect(Collectors.toMap(KnowledgeBaseVO::getId, Function.identity()));

        for (String query : queries) {
            List<KnowledgeRetrieverService.RetrievedChunk> chunks =
                    knowledgeRetrieverService.retrieve(query, topK, accessibleKnowledgeBaseIds);
            candidates.addAll(toCandidates(chunks, query, knowledgeBaseById));
        }

        RetrievalSelection selection = selectChunks(routeMode, request.getQuestion(), candidates, topK);
        log.info("Hybrid retrieval aggregated sessionId={}, question={}, candidateCount={}, selectedCount={}, preferredKnowledgeBase={}",
                request.getSessionId(),
                StrUtil.blankToDefault(request.getQuestion(), ""),
                candidates.size(),
                selection.chunks().size(),
                StrUtil.blankToDefault(selection.preferredKnowledgeBaseName(), ""));
        return selection;
    }

    private boolean shouldRetrieveKnowledge(ChatSendRequest request, ChatRouteModeEnum routeMode) {
        String routeAction = StrUtil.blankToDefault(request.getRouteAction(), "");
        if ("DOCUMENT_UPLOAD".equals(routeAction)
                || "DOCUMENT_ANALYZE".equals(routeAction)
                || isBusinessGuidanceAction(routeAction)) {
            return false;
        }
        if (routeMode == ChatRouteModeEnum.HYBRID_GENERATION) {
            return true;
        }
        String routeReason = StrUtil.blankToDefault(request.getRouteReason(), "");
        if (routeMode == ChatRouteModeEnum.GENERAL_GENERATION
                && "FOLLOW_UP_CONTINUATION".equals(routeReason)) {
            return false;
        }
        if (request.getKnowledgeBaseId() != null) {
            return true;
        }
        String routeDomain = StrUtil.blankToDefault(request.getRouteDomain(), "");
        String routeIntent = StrUtil.blankToDefault(request.getRouteIntent(), "");
        return "ENTERPRISE_REQUIRED".equals(routeReason)
                || "EXPLICIT_KNOWLEDGE_BASE".equals(routeReason)
                || "DOCUMENT_DISCOVERY".equals(routeReason)
                || "STRUCTURED_FACT_QUERY".equals(routeReason)
                || "KNOWLEDGE_REQUIRED".equals(routeReason)
                || "FORM_GENERATION".equals(routeIntent)
                || !"OTHER".equals(routeDomain);
    }

    private boolean isBusinessGuidanceAction(String routeAction) {
        return "ORDER_SUBMISSION".equals(routeAction)
                || "PRODUCT_QUERY".equals(routeAction)
                || "REFUND_REQUEST".equals(routeAction)
                || "HUMAN_HANDOFF".equals(routeAction)
                || "WORK_ORDER_SUBMISSION".equals(routeAction)
                || "STATUS_QUERY".equals(routeAction);
    }

    private List<String> buildRetrievalQueries(String question, ChatRouteModeEnum routeMode) {
        List<String> queries = knowledgeQueryPlanner.buildKnowledgeSearchQueries(question).stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (routeMode == ChatRouteModeEnum.HYBRID_GENERATION && queries.size() > HYBRID_RETRIEVAL_QUERY_LIMIT) {
            return queries.subList(0, HYBRID_RETRIEVAL_QUERY_LIMIT);
        }
        return queries;
    }

    private List<ScoredChunkCandidate> toCandidates(List<KnowledgeRetrieverService.RetrievedChunk> chunks,
                                                    String query,
                                                    KnowledgeBaseVO knowledgeBase) {
        List<ScoredChunkCandidate> candidates = new ArrayList<>();
        for (KnowledgeRetrieverService.RetrievedChunk chunk : chunks) {
            candidates.add(new ScoredChunkCandidate(chunk, query, knowledgeBase.getId(), knowledgeBase.getKnowledgeBaseName()));
        }
        return candidates;
    }

    private List<ScoredChunkCandidate> toCandidates(List<KnowledgeRetrieverService.RetrievedChunk> chunks,
                                                    String query,
                                                    Map<Long, KnowledgeBaseVO> knowledgeBaseById) {
        List<ScoredChunkCandidate> candidates = new ArrayList<>();
        for (KnowledgeRetrieverService.RetrievedChunk chunk : chunks) {
            Long knowledgeBaseId = chunk.knowledgeBaseId();
            KnowledgeBaseVO knowledgeBase = knowledgeBaseId == null ? null : knowledgeBaseById.get(knowledgeBaseId);
            if (knowledgeBase != null) {
                candidates.add(new ScoredChunkCandidate(chunk, query, knowledgeBase.getId(), knowledgeBase.getKnowledgeBaseName()));
            }
        }
        return candidates;
    }

    private RetrievalSelection selectChunks(ChatRouteModeEnum routeMode,
                                            String originalQuestion,
                                            List<ScoredChunkCandidate> candidates,
                                            int topK) {
        if (candidates.isEmpty()) {
            return new RetrievalSelection(List.of(), null);
        }

        List<String> evidenceTerms = routeMode == ChatRouteModeEnum.HYBRID_GENERATION
                ? evidenceTermsForAlias(resolveBusinessAlias(originalQuestion))
                : List.of();

        Map<String, ScoredChunkCandidate> uniqueCandidates = new LinkedHashMap<>();
        for (ScoredChunkCandidate candidate : candidates) {
            int score = candidateScore(candidate, originalQuestion, evidenceTerms);
            String key = deduplicationKey(candidate.chunk());
            ScoredChunkCandidate current = uniqueCandidates.get(key);
            if (current == null || score > current.score()) {
                uniqueCandidates.put(key, candidate.withScore(score));
            }
        }

        List<ScoredChunkCandidate> ranked = uniqueCandidates.values().stream()
                .sorted(Comparator
                        .comparingInt(ScoredChunkCandidate::score).reversed()
                        .thenComparing(candidate -> StrUtil.blankToDefault(candidate.chunk().fileName(), ""))
                        .thenComparing(candidate -> candidate.chunk().chunkIndex(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        String preferredKnowledgeBaseName = resolvePreferredKnowledgeBaseName(ranked, routeMode, evidenceTerms);
        List<ScoredChunkCandidate> preferredCandidates = filterPreferredCandidates(
                ranked,
                routeMode,
                preferredKnowledgeBaseName,
                evidenceTerms
        );

        if (routeMode == ChatRouteModeEnum.HYBRID_GENERATION
                && StrUtil.isBlank(preferredKnowledgeBaseName)
                && !evidenceTerms.isEmpty()) {
            log.info("Hybrid retrieval skipped preferred knowledge base due to missing strong evidence question={}, candidateCount={}, evidenceTerms={}",
                    StrUtil.blankToDefault(originalQuestion, ""),
                    ranked.size(),
                    evidenceTerms);
        }

        List<KnowledgeRetrieverService.RetrievedChunk> selectedChunks = preferredCandidates.stream()
                .limit(topK)
                .map(ScoredChunkCandidate::chunk)
                .toList();

        if (routeMode == ChatRouteModeEnum.HYBRID_GENERATION) {
            log.info("Hybrid retrieval selected question={}, preferredKnowledgeBase={}, selectedFiles={}",
                    StrUtil.blankToDefault(originalQuestion, ""),
                    StrUtil.blankToDefault(preferredKnowledgeBaseName, ""),
                    selectedChunks.stream()
                            .map(chunk -> StrUtil.blankToDefault(chunk.fileName(), ""))
                            .distinct()
                            .toList());
        }

        return new RetrievalSelection(selectedChunks, preferredKnowledgeBaseName);
    }

    private int candidateScore(ScoredChunkCandidate candidate,
                               String originalQuestion,
                               List<String> evidenceTerms) {
        int score = (int) Math.round((candidate.chunk().score() == null ? 0D : candidate.chunk().score()) * 1000);
        String question = StrUtil.blankToDefault(originalQuestion, "");
        String query = StrUtil.blankToDefault(candidate.query(), "");
        String fileName = StrUtil.blankToDefault(candidate.chunk().fileName(), "");
        String content = StrUtil.blankToDefault(candidate.chunk().content(), "");
        String haystack = fileName + "\n" + content;

        score += overlapScore(haystack, question) * 2;
        score += overlapScore(haystack, query) * 3;
        if (containsAny(haystack, evidenceTerms)) {
            score += 240;
        }
        if (containsAny(fileName, evidenceTerms)) {
            score += 160;
        }
        if (query.contains("申请方式") || query.contains("到账时效")) {
            score += 60;
        }
        if (query.contains("政策") || query.contains("制度") || query.contains("规则") || query.contains("流程")) {
            score += 40;
        }
        return score;
    }

    private String resolvePreferredKnowledgeBaseName(List<ScoredChunkCandidate> ranked,
                                                     ChatRouteModeEnum routeMode,
                                                     List<String> evidenceTerms) {
        if (ranked.isEmpty()) {
            return null;
        }

        Map<String, Integer> scoresByKnowledgeBase = new LinkedHashMap<>();
        for (ScoredChunkCandidate candidate : ranked) {
            if (routeMode == ChatRouteModeEnum.HYBRID_GENERATION
                    && !evidenceTerms.isEmpty()
                    && !hasStrongEvidence(candidate, evidenceTerms)) {
                continue;
            }
            int weight = candidate.score();
            if (routeMode == ChatRouteModeEnum.HYBRID_GENERATION && hasStrongEvidence(candidate, evidenceTerms)) {
                weight += 120;
            }
            scoresByKnowledgeBase.merge(candidate.knowledgeBaseName(), weight, Integer::sum);
        }

        return scoresByKnowledgeBase.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<ScoredChunkCandidate> filterPreferredCandidates(List<ScoredChunkCandidate> ranked,
                                                                 ChatRouteModeEnum routeMode,
                                                                 String preferredKnowledgeBaseName,
                                                                 List<String> evidenceTerms) {
        if (ranked.isEmpty()) {
            return ranked;
        }

        if (StrUtil.isNotBlank(preferredKnowledgeBaseName)) {
            List<ScoredChunkCandidate> sameKnowledgeBase = ranked.stream()
                    .filter(candidate -> preferredKnowledgeBaseName.equals(candidate.knowledgeBaseName()))
                    .toList();
            if (!sameKnowledgeBase.isEmpty()) {
                if (routeMode == ChatRouteModeEnum.HYBRID_GENERATION && !evidenceTerms.isEmpty()) {
                    List<ScoredChunkCandidate> strongEvidenceSameKnowledgeBase = sameKnowledgeBase.stream()
                            .filter(candidate -> hasStrongEvidence(candidate, evidenceTerms))
                            .toList();
                    if (!strongEvidenceSameKnowledgeBase.isEmpty()) {
                        return strongEvidenceSameKnowledgeBase;
                    }
                }
                return sameKnowledgeBase;
            }
        }

        if (routeMode != ChatRouteModeEnum.HYBRID_GENERATION || evidenceTerms.isEmpty()) {
            return ranked;
        }

        List<ScoredChunkCandidate> strongEvidenceCandidates = ranked.stream()
                .filter(candidate -> hasStrongEvidence(candidate, evidenceTerms))
                .toList();
        return strongEvidenceCandidates.isEmpty() ? ranked : strongEvidenceCandidates;
    }

    private boolean hasEffectiveHybridEvidence(String question, List<KnowledgeRetrieverService.RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return false;
        }
        String businessAlias = resolveBusinessAlias(question);
        if (StrUtil.isBlank(businessAlias)) {
            return true;
        }
        List<String> evidenceTerms = evidenceTermsForAlias(businessAlias);
        return chunks.stream().anyMatch(chunk -> containsAnyEvidenceTerm(chunk, evidenceTerms));
    }

    private String resolveBusinessAlias(String question) {
        if (StrUtil.isBlank(question)) {
            return null;
        }
        return HYBRID_EVIDENCE_TERMS.keySet().stream()
                .filter(question::contains)
                .findFirst()
                .orElse(null);
    }

    private List<String> evidenceTermsForAlias(String alias) {
        if (StrUtil.isBlank(alias)) {
            return List.of();
        }
        return HYBRID_EVIDENCE_TERMS.getOrDefault(alias, List.of(alias));
    }

    private boolean containsAnyEvidenceTerm(KnowledgeRetrieverService.RetrievedChunk chunk, List<String> evidenceTerms) {
        String haystack = StrUtil.blankToDefault(chunk.fileName(), "")
                + "\n"
                + StrUtil.blankToDefault(chunk.content(), "");
        return containsAny(haystack, evidenceTerms);
    }

    private boolean hasStrongEvidence(ScoredChunkCandidate candidate, List<String> evidenceTerms) {
        return containsAnyEvidenceTerm(candidate.chunk(), evidenceTerms);
    }

    private boolean containsAny(String haystack, List<String> keywords) {
        if (StrUtil.isBlank(haystack) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .filter(StrUtil::isNotBlank)
                .anyMatch(haystack::contains);
    }

    private int overlapScore(String haystack, String needle) {
        if (StrUtil.isBlank(haystack) || StrUtil.isBlank(needle)) {
            return 0;
        }
        int score = 0;
        for (char ch : needle.toCharArray()) {
            if (haystack.indexOf(ch) >= 0) {
                score++;
            }
        }
        return score;
    }

    private String deduplicationKey(KnowledgeRetrieverService.RetrievedChunk chunk) {
        return StrUtil.blankToDefault(chunk.fileName(), "")
                + "#"
                + StrUtil.blankToDefault(String.valueOf(chunk.documentId()), "")
                + "#"
                + StrUtil.blankToDefault(String.valueOf(chunk.chunkIndex()), "")
                + "#"
                + StrUtil.blankToDefault(chunk.content(), "");
    }

    private String formatKnowledge(List<KnowledgeRetrieverService.RetrievedChunk> chunks, ChatRouteModeEnum routeMode) {
        if (chunks == null || chunks.isEmpty()) {
            return routeMode == ChatRouteModeEnum.HYBRID_GENERATION
                    ? "未检索到可作为企业规则依据的知识内容。"
                    : "未检索到可作为企业事实依据的知识内容。";
        }

        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (KnowledgeRetrieverService.RetrievedChunk chunk : chunks) {
            builder.append("参考片段").append(index).append(":\n")
                    .append("来源文件: ").append(StrUtil.blankToDefault(chunk.fileName(), "")).append("\n")
                    .append("内容: ").append(StrUtil.blankToDefault(chunk.content(), "")).append("\n\n");
            index++;
        }
        return builder.toString().trim();
    }

    private String buildHybridNoEvidenceAnswer(String question) {
        if (StrUtil.isNotBlank(question) && question.contains("请假")) {
            return "当前未从知识库中检索到对应企业的请假规则，暂时无法按知识库为你生成请假申请内容。你可以补充请假制度相关文档，或指定对应知识库后我再继续处理。";
        }
        if (StrUtil.isNotBlank(question) && question.contains("报销")) {
            return "当前未从知识库中检索到对应企业的报销规则，暂时无法按知识库为你生成报销模板。你可以补充报销制度相关文档，或指定对应知识库后我再继续处理。";
        }
        if (StrUtil.isNotBlank(question) && question.contains("退款")) {
            return "当前未从知识库中检索到对应企业的退款规则，暂时无法按知识库为你生成退款申请内容。你可以补充退款政策文档，或指定对应知识库后我再继续处理。";
        }
        return "当前未从知识库中检索到可支撑该业务生成请求的企业规则，暂时无法按知识库生成。你可以补充相关制度文档，或指定对应知识库后我再继续处理。";
    }

    private String buildReferenceContent(ChatSendRequest request, RetrievalSelection retrievalSelection) {
        ChatRouteModeEnum routeMode = routeMode(request);
        StringBuilder builder = new StringBuilder("ROUTE_MODE: ")
                .append(routeMode.name())
                .append("\n");
        if (StrUtil.isNotBlank(request.getRouteReason())) {
            builder.append("ROUTE_REASON: ").append(request.getRouteReason()).append("\n");
        }
        if (StrUtil.isNotBlank(request.getRouteDomain())) {
            builder.append("ROUTE_DOMAIN: ").append(request.getRouteDomain()).append("\n");
        }
        if (StrUtil.isNotBlank(request.getRouteIntent())) {
            builder.append("ROUTE_INTENT: ").append(request.getRouteIntent()).append("\n");
        }

        if (routeMode == ChatRouteModeEnum.GENERAL_GENERATION) {
            return builder.toString().trim();
        }

        LinkedHashSet<String> knowledgeBaseNames = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(retrievalSelection.preferredKnowledgeBaseName())) {
            knowledgeBaseNames.add(retrievalSelection.preferredKnowledgeBaseName());
        }

        if (!knowledgeBaseNames.isEmpty()) {
            builder.append("REFERENCE_COUNT: ").append(knowledgeBaseNames.size()).append("\n");
            int index = 1;
            for (String knowledgeBaseName : knowledgeBaseNames) {
                builder.append("REFERENCE_").append(index).append(": ").append(knowledgeBaseName).append("\n");
                index++;
            }
        }

        return builder.toString().trim();
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

    private String formatHistory(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "暂无历史会话。";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message == null || StrUtil.isBlank(message.getContent())) {
                continue;
            }
            builder.append("[")
                    .append(message.getRole())
                    .append("] ")
                    .append(message.getContent())
                    .append("\n");
        }
        return builder.isEmpty() ? "暂无历史会话。" : builder.toString();
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

    private void sendReference(SseEmitter emitter, String referenceContent) {
        if (StrUtil.isBlank(referenceContent)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("reference").data(referenceContent));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException exception) {
            log.warn("Send general generation done event failed", exception);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException exception) {
            log.warn("Send general generation error event failed", exception);
        } finally {
            emitter.completeWithError(new RuntimeException(message));
        }
    }

    private String buildBusinessClarificationAnswerV2(ChatSendRequest request) {
        if (!"BUSINESS_CLARIFICATION".equals(StrUtil.blankToDefault(request.getRouteReason(), ""))) {
            return null;
        }
        String question = StrUtil.blankToDefault(request.getQuestion(), "");
        if (question.contains("退款") || question.contains("退货")) {
            return "请先确认一下你的需求：\n1. 如果你是想查规则，请直接说“退款规则”或“退款流程”；\n2. 如果你是想发起退款，请直接说“申请退款”，并附上订单号。";
        }
        if (question.contains("请假")) {
            return "请先确认一下你的需求：\n1. 如果你是想查规则，请直接说“请假制度”或“请假流程”；\n2. 如果你是想提交请假工单，请直接说明请假天数和请假类型，例如“申请请假，3天年假”。";
        }
        if (question.contains("工单")) {
            return "请先确认一下你的需求：\n1. 如果你是想查规则，请直接说“工单规则”或“工单流程”；\n2. 如果你是想提交工单，请直接说明要办理的业务和必要信息。";
        }
        if (question.contains("人工") || question.contains("客服")) {
            return "请先确认一下你的需求：\n1. 如果你是想查规则或联系方式，请直接说“人工客服联系方式”或“人工客服说明”；\n2. 如果你是想发起动作，请直接说“转人工”。";
        }
        if (question.contains("订单") || question.contains("下单")) {
            return "请先确认一下你的需求：\n1. 如果你是想查规则，请直接说“下单流程”或“订单规则”；\n2. 如果你是想提交订单，请直接说明产品号和购买数量，例如“帮我下单 P-1002 2件”。";
        }
        if (question.contains("产品") || question.contains("商品")) {
            return "请先确认一下你的需求：\n1. 如果你是想查规则说明，请直接说“产品规则”或“商品说明”；\n2. 如果你是想查具体产品，请直接说“查看 P-1002 产品信息”这类表达。";
        }
        return "你的意思我还不能完全确认。请再明确一下，你是想查询规则说明，还是要发起具体业务动作。";
    }

    private record ContactResolution(List<String> items) {
        static ContactResolution empty() {
            return new ContactResolution(List.of());
        }

        boolean isEmpty() {
            return items == null || items.isEmpty();
        }
    }

    private record ScoredChunkCandidate(KnowledgeRetrieverService.RetrievedChunk chunk,
                                        String query,
                                        Long knowledgeBaseId,
                                        String knowledgeBaseName,
                                        int score) {
        private ScoredChunkCandidate(KnowledgeRetrieverService.RetrievedChunk chunk,
                                     String query,
                                     Long knowledgeBaseId,
                                     String knowledgeBaseName) {
            this(chunk, query, knowledgeBaseId, knowledgeBaseName, 0);
        }

        private ScoredChunkCandidate withScore(int score) {
            return new ScoredChunkCandidate(chunk, query, knowledgeBaseId, knowledgeBaseName, score);
        }
    }

    private record RetrievalSelection(List<KnowledgeRetrieverService.RetrievedChunk> chunks,
                                      String preferredKnowledgeBaseName) {
    }
}
