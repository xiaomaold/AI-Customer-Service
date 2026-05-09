package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.ConversationContextResolver;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.service.KnowledgeDocumentService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import com.airag.modules.knowledge.vo.KnowledgeDocumentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextResolverImpl implements ConversationContextResolver {

    private static final int MAX_CARRYOVER_MESSAGES = 4;
    private static final int MAX_GENERIC_REFERENCE_QUESTION_LENGTH = 18;

    private static final List<String> KNOWLEDGE_BASE_REFERENCES = List.of(
            "这个库", "这个知识库", "该库", "这个资料库", "那个库", "该知识库"
    );

    private static final List<String> DOCUMENT_REFERENCES = List.of(
            "这个文档", "该文档", "这篇文档", "那个文档", "这份文档", "这个文件"
    );

    private static final List<String> GENERIC_REFERENCES = List.of(
            "里面", "这里面", "那里面", "其中", "它", "它里面", "其中的"
    );

    private static final List<String> ERROR_FRAGMENTS = List.of(
            "未登录或登录状态已失效",
            "系统繁忙，请稍后重试",
            "Agent 调用失败，请稍后重试"
    );

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentService knowledgeDocumentService;

    @Override
    public RecentConversationContext resolve(LoginUser loginUser,
                                             Long sessionId,
                                             Long selectedKnowledgeBaseId,
                                             List<ChatMessage> recentMessages,
                                             String currentQuestion,
                                             String carryoverKnowledgeBaseName,
                                             String carryoverDocumentName) {
        List<KnowledgeBaseVO> accessibleBases = knowledgeBaseService.list(loginUser.getUserId(), loginUser.getRoles());
        Map<Long, KnowledgeBaseVO> baseById = accessibleBases.stream()
                .collect(Collectors.toMap(KnowledgeBaseVO::getId, Function.identity(), (left, right) -> left));

        HistoricalContext historical = resolveHistoricalContext(loginUser, recentMessages, accessibleBases);
        NameCandidate explicitKnowledgeBase = resolveExplicitKnowledgeBase(
                loginUser, selectedKnowledgeBaseId, currentQuestion, accessibleBases, baseById
        );
        NameCandidate explicitDocument = resolveExplicitDocument(
                loginUser, currentQuestion, explicitKnowledgeBase, historical, accessibleBases
        );
        NameCandidate requestedKnowledgeBaseCarryover = resolveRequestedKnowledgeBaseCarryover(
                carryoverKnowledgeBaseName,
                accessibleBases
        );
        NameCandidate requestedDocumentCarryover = resolveRequestedDocumentCarryover(
                loginUser,
                carryoverDocumentName,
                requestedKnowledgeBaseCarryover,
                historical,
                accessibleBases
        );

        boolean documentReference = containsAny(currentQuestion, DOCUMENT_REFERENCES);
        boolean knowledgeBaseReference = containsAny(currentQuestion, KNOWLEDGE_BASE_REFERENCES);
        boolean genericReference = containsAny(currentQuestion, GENERIC_REFERENCES);
        boolean genericCarryoverAllowed = allowsGenericCarryover(currentQuestion, explicitKnowledgeBase, explicitDocument);

        boolean applyRequestedDocumentCarryover = explicitDocument == null && requestedDocumentCarryover != null;
        boolean applyRequestedKnowledgeBaseCarryover = explicitKnowledgeBase == null
                && (requestedKnowledgeBaseCarryover != null
                || (applyRequestedDocumentCarryover && requestedDocumentCarryover.baseId() != null));
        boolean applyDocumentCarryover = explicitDocument == null
                && !applyRequestedDocumentCarryover
                && historical.documentCandidate() != null
                && (documentReference || (genericReference && genericCarryoverAllowed));
        boolean applyKnowledgeBaseCarryover = explicitKnowledgeBase == null
                && !applyRequestedKnowledgeBaseCarryover
                && historical.knowledgeBaseCandidate() != null
                && (knowledgeBaseReference || applyDocumentCarryover || (genericReference && genericCarryoverAllowed));

        NameCandidate effectiveKnowledgeBase = explicitKnowledgeBase != null
                ? explicitKnowledgeBase
                : applyRequestedKnowledgeBaseCarryover
                ? requestedKnowledgeBaseCarryover != null
                    ? requestedKnowledgeBaseCarryover
                    : knowledgeBaseCandidateFromDocument(requestedDocumentCarryover, baseById)
                : applyKnowledgeBaseCarryover ? historical.knowledgeBaseCandidate() : null;
        NameCandidate effectiveDocument = explicitDocument != null
                ? explicitDocument
                : applyRequestedDocumentCarryover ? requestedDocumentCarryover
                : applyDocumentCarryover ? historical.documentCandidate() : null;

        RecentConversationContext context = RecentConversationContext.builder()
                .currentQuestion(currentQuestion)
                .knowledgeBaseName(effectiveKnowledgeBase == null ? null : effectiveKnowledgeBase.name())
                .documentName(effectiveDocument == null ? null : effectiveDocument.name())
                .sourceMessageId(effectiveDocument != null ? effectiveDocument.sourceMessageId()
                        : effectiveKnowledgeBase == null ? null : effectiveKnowledgeBase.sourceMessageId())
                .explicitKnowledgeBaseInQuestion(explicitKnowledgeBase != null)
                .explicitDocumentInQuestion(explicitDocument != null)
                .applyKnowledgeBaseCarryover(applyRequestedKnowledgeBaseCarryover || applyKnowledgeBaseCarryover)
                .applyDocumentCarryover(applyRequestedDocumentCarryover || applyDocumentCarryover)
                .build();

        log.info("Conversation context resolved sessionId={}, userId={}, carryoverHit={}, knowledgeBaseName={}, documentName={}, explicitKnowledgeBaseInQuestion={}, explicitDocumentInQuestion={}, sourceMessageId={}",
                sessionId,
                loginUser.getUserId(),
                context.hasCarryover(),
                safe(context.getKnowledgeBaseName()),
                safe(context.getDocumentName()),
                context.isExplicitKnowledgeBaseInQuestion(),
                context.isExplicitDocumentInQuestion(),
                context.getSourceMessageId());

        return context;
    }

    private NameCandidate resolveRequestedKnowledgeBaseCarryover(String carryoverKnowledgeBaseName,
                                                                 List<KnowledgeBaseVO> accessibleBases) {
        return matchKnowledgeBase(carryoverKnowledgeBaseName, accessibleBases, null);
    }

    private NameCandidate resolveRequestedDocumentCarryover(LoginUser loginUser,
                                                            String carryoverDocumentName,
                                                            NameCandidate requestedKnowledgeBaseCarryover,
                                                            HistoricalContext historical,
                                                            List<KnowledgeBaseVO> accessibleBases) {
        if (StrUtil.isBlank(carryoverDocumentName)) {
            return null;
        }
        return resolveExplicitDocument(
                loginUser,
                carryoverDocumentName,
                requestedKnowledgeBaseCarryover,
                historical,
                accessibleBases
        );
    }

    private NameCandidate knowledgeBaseCandidateFromDocument(NameCandidate documentCandidate,
                                                             Map<Long, KnowledgeBaseVO> baseById) {
        if (documentCandidate == null || documentCandidate.baseId() == null) {
            return null;
        }
        KnowledgeBaseVO base = baseById.get(documentCandidate.baseId());
        if (base == null || StrUtil.isBlank(base.getKnowledgeBaseName())) {
            return null;
        }
        return new NameCandidate(base.getKnowledgeBaseName(), base.getId(), documentCandidate.sourceMessageId());
    }

    private HistoricalContext resolveHistoricalContext(LoginUser loginUser,
                                                       List<ChatMessage> recentMessages,
                                                       List<KnowledgeBaseVO> accessibleBases) {
        NameCandidate knowledgeBaseCandidate = null;
        NameCandidate documentCandidate = null;
        Map<Long, List<KnowledgeDocumentVO>> documentsByBaseId = buildDocumentsByBaseId(loginUser, accessibleBases);
        List<ChatMessage> candidates = recentUsableMessages(recentMessages);

        for (int index = candidates.size() - 1; index >= 0; index--) {
            ChatMessage message = candidates.get(index);
            if (knowledgeBaseCandidate == null) {
                knowledgeBaseCandidate = matchKnowledgeBase(message.getContent(), accessibleBases, message.getId());
            }
            if (documentCandidate == null) {
                documentCandidate = matchDocument(message.getContent(), documentsByBaseId, knowledgeBaseCandidate, message.getId());
            }
            if (knowledgeBaseCandidate != null && documentCandidate != null) {
                break;
            }
        }

        return new HistoricalContext(knowledgeBaseCandidate, documentCandidate);
    }

    private List<ChatMessage> recentUsableMessages(List<ChatMessage> recentMessages) {
        List<ChatMessage> usableMessages = recentMessages.stream()
                .filter(this::isUsableMessage)
                .toList();
        if (usableMessages.size() <= MAX_CARRYOVER_MESSAGES) {
            return usableMessages;
        }
        return usableMessages.subList(usableMessages.size() - MAX_CARRYOVER_MESSAGES, usableMessages.size());
    }

    private NameCandidate resolveExplicitKnowledgeBase(LoginUser loginUser,
                                                       Long selectedKnowledgeBaseId,
                                                       String currentQuestion,
                                                       List<KnowledgeBaseVO> accessibleBases,
                                                       Map<Long, KnowledgeBaseVO> baseById) {
        if (selectedKnowledgeBaseId != null) {
            KnowledgeBaseVO selected = baseById.get(selectedKnowledgeBaseId);
            if (selected == null) {
                selected = knowledgeBaseService.getAccessibleDetail(
                        loginUser.getUserId(),
                        loginUser.getRoles(),
                        selectedKnowledgeBaseId
                );
            }
            if (selected != null) {
                return new NameCandidate(selected.getKnowledgeBaseName(), selectedKnowledgeBaseId, null);
            }
        }

        return matchKnowledgeBase(currentQuestion, accessibleBases, null);
    }

    private NameCandidate resolveExplicitDocument(LoginUser loginUser,
                                                  String currentQuestion,
                                                  NameCandidate explicitKnowledgeBase,
                                                  HistoricalContext historical,
                                                  List<KnowledgeBaseVO> accessibleBases) {
        Map<Long, List<KnowledgeDocumentVO>> documentsByBaseId;
        if (explicitKnowledgeBase != null && explicitKnowledgeBase.baseId() != null) {
            documentsByBaseId = Map.of(
                    explicitKnowledgeBase.baseId(),
                    knowledgeDocumentService.listDocuments(
                            loginUser.getUserId(),
                            loginUser.getRoles(),
                            explicitKnowledgeBase.baseId()
                    )
            );
        } else if (historical.knowledgeBaseCandidate() != null && historical.knowledgeBaseCandidate().baseId() != null) {
            documentsByBaseId = Map.of(
                    historical.knowledgeBaseCandidate().baseId(),
                    knowledgeDocumentService.listDocuments(
                            loginUser.getUserId(),
                            loginUser.getRoles(),
                            historical.knowledgeBaseCandidate().baseId()
                    )
            );
        } else {
            documentsByBaseId = buildDocumentsByBaseId(loginUser, accessibleBases);
        }

        return matchDocument(currentQuestion, documentsByBaseId, explicitKnowledgeBase, null);
    }

    private Map<Long, List<KnowledgeDocumentVO>> buildDocumentsByBaseId(LoginUser loginUser, List<KnowledgeBaseVO> accessibleBases) {
        return accessibleBases.stream().collect(Collectors.toMap(
                KnowledgeBaseVO::getId,
                base -> knowledgeDocumentService.listDocuments(loginUser.getUserId(), loginUser.getRoles(), base.getId()),
                (left, right) -> left
        ));
    }

    private NameCandidate matchKnowledgeBase(String text, List<KnowledgeBaseVO> accessibleBases, Long sourceMessageId) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String normalizedText = normalize(text);
        return accessibleBases.stream()
                .map(base -> new NameCandidate(base.getKnowledgeBaseName(), base.getId(), sourceMessageId))
                .map(candidate -> new ScoredNameCandidate(candidate, matchScore(normalizedText, normalizeKnowledgeBaseText(candidate.name()))))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator
                        .comparingInt(ScoredNameCandidate::score).reversed()
                        .thenComparing((ScoredNameCandidate candidate) -> candidate.nameCandidate().name().length(), Comparator.reverseOrder()))
                .map(ScoredNameCandidate::nameCandidate)
                .findFirst()
                .orElse(null);
    }

    private NameCandidate matchDocument(String text,
                                        Map<Long, List<KnowledgeDocumentVO>> documentsByBaseId,
                                        NameCandidate preferredKnowledgeBase,
                                        Long sourceMessageId) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String normalizedText = normalize(text);
        List<ScoredNameCandidate> candidates = new ArrayList<>();

        if (preferredKnowledgeBase != null && preferredKnowledgeBase.baseId() != null) {
            List<KnowledgeDocumentVO> preferredDocuments = documentsByBaseId.getOrDefault(preferredKnowledgeBase.baseId(), List.of());
            candidates.addAll(toDocumentCandidates(preferredDocuments, preferredKnowledgeBase.baseId(), normalizedText, sourceMessageId));
        }

        if (candidates.isEmpty()) {
            documentsByBaseId.forEach((baseId, documents) ->
                    candidates.addAll(toDocumentCandidates(documents, baseId, normalizedText, sourceMessageId)));
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingInt(ScoredNameCandidate::score).reversed()
                        .thenComparing((ScoredNameCandidate candidate) -> candidate.nameCandidate().name().length(), Comparator.reverseOrder()))
                .map(ScoredNameCandidate::nameCandidate)
                .findFirst()
                .orElse(null);
    }

    private List<ScoredNameCandidate> toDocumentCandidates(List<KnowledgeDocumentVO> documents,
                                                           Long baseId,
                                                           String normalizedText,
                                                           Long sourceMessageId) {
        return documents.stream()
                .map(document -> {
                    int score = Math.max(
                            matchScore(normalizedText, normalizeDocumentText(document.getDocumentName())),
                            matchScore(normalizedText, normalizeDocumentText(document.getFileName()))
                    );
                    return new ScoredNameCandidate(
                            new NameCandidate(bestDocumentName(document), baseId, sourceMessageId),
                            score
                    );
                })
                .filter(candidate -> candidate.score() > 0)
                .toList();
    }

    private String bestDocumentName(KnowledgeDocumentVO document) {
        if (StrUtil.isNotBlank(document.getDocumentName())) {
            return document.getDocumentName();
        }
        return StrUtil.blankToDefault(document.getFileName(), "");
    }

    private boolean isUsableMessage(ChatMessage message) {
        if (message == null || StrUtil.isBlank(message.getContent())) {
            return false;
        }
        return ERROR_FRAGMENTS.stream().noneMatch(message.getContent()::contains);
    }

    private boolean containsAny(String text, List<String> patterns) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        return patterns.stream().anyMatch(text::contains);
    }

    private boolean allowsGenericCarryover(String currentQuestion,
                                           NameCandidate explicitKnowledgeBase,
                                           NameCandidate explicitDocument) {
        if (explicitKnowledgeBase != null || explicitDocument != null || StrUtil.isBlank(currentQuestion)) {
            return false;
        }
        String trimmed = currentQuestion.trim();
        if (trimmed.length() > MAX_GENERIC_REFERENCE_QUESTION_LENGTH) {
            return false;
        }
        return !looksLikeStandaloneQuestion(trimmed);
    }

    private boolean looksLikeStandaloneQuestion(String currentQuestion) {
        String normalized = normalize(currentQuestion);
        return normalized.startsWith("为什么")
                || normalized.startsWith("怎么")
                || normalized.startsWith("如何")
                || normalized.startsWith("哪里")
                || normalized.startsWith("哪儿")
                || normalized.startsWith("哪个")
                || normalized.startsWith("谁")
                || normalized.startsWith("什么")
                || normalized.startsWith("请问")
                || normalized.startsWith("麻烦")
                || normalized.contains("请假")
                || normalized.contains("退款")
                || normalized.contains("报销")
                || normalized.contains("客服")
                || normalized.contains("地址")
                || normalized.contains("邮箱")
                || normalized.contains("电话");
    }

    private int matchScore(String normalizedText, String normalizedCandidate) {
        if (StrUtil.isBlank(normalizedText) || StrUtil.isBlank(normalizedCandidate)) {
            return 0;
        }
        if (normalizedText.equals(normalizedCandidate)) {
            return 100;
        }
        if (normalizedText.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedText)) {
            return 90;
        }
        int overlap = overlapScore(normalizedText, normalizedCandidate);
        return overlap >= 50 ? overlap : 0;
    }

    private int overlapScore(String left, String right) {
        List<Character> remaining = left.chars()
                .mapToObj(ch -> (char) ch)
                .collect(Collectors.toCollection(ArrayList::new));
        int common = 0;
        for (char ch : right.toCharArray()) {
            int index = remaining.indexOf(ch);
            if (index >= 0) {
                common++;
                remaining.remove(index);
            }
        }
        if (common == 0) {
            return 0;
        }
        return common * 100 / Math.max(left.length(), right.length());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s._\\-()（）]+", "")
                .trim();
    }

    private String normalizeKnowledgeBaseText(String value) {
        return normalize(value)
                .replace("知识库", "")
                .replace("文档库", "")
                .replace("资料库", "")
                .replace("库", "")
                .replace("pdf", "");
    }

    private String normalizeDocumentText(String value) {
        return normalize(value)
                .replace("pdf", "")
                .replace("docx", "")
                .replace("doc", "")
                .replace("txt", "");
    }

    private String safe(String value) {
        return StrUtil.blankToDefault(value, "");
    }

    private record HistoricalContext(NameCandidate knowledgeBaseCandidate, NameCandidate documentCandidate) {
    }

    private record NameCandidate(String name, Long baseId, Long sourceMessageId) {
    }

    private record ScoredNameCandidate(NameCandidate nameCandidate, int score) {
    }
}
