package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.AgentEvidenceBundle;
import com.airag.modules.agent.service.AgentEvidencePreparationService;
import com.airag.modules.agent.service.AgentQuestionClassifier;
import com.airag.modules.agent.service.KnowledgeAgentFacade;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.enums.ChatRouteModeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEvidencePreparationServiceImpl implements AgentEvidencePreparationService {

    private final AgentQuestionClassifier agentQuestionClassifier;
    private final KnowledgeAgentFacade knowledgeAgentFacade;

    @Override
    public AgentEvidenceBundle prepare(LoginUser loginUser,
                                       RecentConversationContext conversationContext,
                                       Long requestedKnowledgeBaseId,
                                       String routeMode,
                                       String routeReason,
                                       String routeDomain,
                                       String routeIntent,
                                       String executionProfile,
                                       String executionDirective,
                                       String question) {
        if (StrUtil.isBlank(question)) {
            return AgentEvidenceBundle.builder()
                    .effectiveQuestion(question)
                    .knowledgeEvidenceUsed(false)
                    .documentEvidenceUsed(false)
                    .directAnswerUsed(false)
                    .build();
        }

        String knowledgeBaseKeyword = agentQuestionClassifier.resolveKnowledgeBaseKeyword(question, conversationContext);
        String documentKeyword = agentQuestionClassifier.resolveDocumentKeyword(question, conversationContext);
        boolean documentProfile = "DOCUMENT_QA".equalsIgnoreCase(executionProfile);
        boolean structuredFactProfile = "STRUCTURED_FACT".equalsIgnoreCase(executionProfile);
        boolean structuredFactQuestion = structuredFactProfile || agentQuestionClassifier.isStructuredFactQuestion(question);

        String knowledgeEvidence = null;
        boolean hasKnowledgeEvidence = false;
        String documentEvidence = null;

        if (structuredFactQuestion) {
            String structuredFactEvidence = knowledgeAgentFacade.searchStructuredFact(loginUser, question, knowledgeBaseKeyword, 5);
            if (hasDocumentEvidence(structuredFactEvidence)) {
                documentEvidence = structuredFactEvidence;
                String referenceContent = buildReferenceContent(
                        requestedKnowledgeBaseId,
                        routeMode,
                        routeReason,
                        routeDomain,
                        routeIntent,
                        conversationContext,
                        knowledgeBaseKeyword,
                        null,
                        documentEvidence
                );
                String directStructuredFactAnswer = buildStructuredFactDirectAnswer(question, documentEvidence);
                if (StrUtil.isNotBlank(directStructuredFactAnswer)) {
                    log.info("Structured fact fast-path hit question={}, knowledgeBaseKeyword={}",
                            StrUtil.blankToDefault(question, ""),
                            StrUtil.blankToDefault(knowledgeBaseKeyword, ""));
                    return AgentEvidenceBundle.builder()
                            .effectiveQuestion(question)
                            .referenceContent(referenceContent)
                            .directAnswer(directStructuredFactAnswer)
                            .knowledgeEvidenceUsed(false)
                            .documentEvidenceUsed(true)
                            .directAnswerUsed(true)
                            .build();
                }
            }
        }

        knowledgeEvidence = knowledgeAgentFacade.searchKnowledge(loginUser, question, knowledgeBaseKeyword, 3);
        hasKnowledgeEvidence = hasKnowledgeEvidence(knowledgeEvidence);
        boolean shouldEscalate = documentProfile || shouldEscalateToDocumentEvidence(
                question,
                routeReason,
                conversationContext,
                knowledgeEvidence,
                documentKeyword
        );

        if (documentEvidence == null && shouldEscalate) {
            String resolvedDocumentKeyword = StrUtil.isNotBlank(documentKeyword) ? documentKeyword : firstSourceFile(knowledgeEvidence);
            documentEvidence = knowledgeAgentFacade.searchDocumentContent(
                    loginUser,
                    question,
                    knowledgeBaseKeyword,
                    resolvedDocumentKeyword,
                    8
            );
        }

        if (!hasDocumentEvidence(documentEvidence) && structuredFactQuestion) {
            documentEvidence = searchStructuredFactDocumentEvidence(loginUser, question, knowledgeBaseKeyword, documentEvidence);
        }

        log.info("Evidence escalation question={}, knowledgeBaseKeyword={}, initialDocumentKeyword={}, hasKnowledgeEvidence={}, escalated={}, hasDocumentEvidence={}",
                StrUtil.blankToDefault(question, ""),
                StrUtil.blankToDefault(knowledgeBaseKeyword, ""),
                StrUtil.blankToDefault(documentKeyword, ""),
                hasKnowledgeEvidence,
                shouldEscalate,
                hasDocumentEvidence(documentEvidence));

        String referenceContent = buildReferenceContent(
                requestedKnowledgeBaseId,
                routeMode,
                routeReason,
                routeDomain,
                routeIntent,
                conversationContext,
                knowledgeBaseKeyword,
                knowledgeEvidence,
                documentEvidence
        );

        String directStructuredFactAnswer = buildStructuredFactDirectAnswer(question, documentEvidence);
        if (StrUtil.isNotBlank(directStructuredFactAnswer)) {
            return AgentEvidenceBundle.builder()
                    .effectiveQuestion(question)
                    .referenceContent(referenceContent)
                    .directAnswer(directStructuredFactAnswer)
                    .knowledgeEvidenceUsed(hasKnowledgeEvidence)
                    .documentEvidenceUsed(hasDocumentEvidence(documentEvidence))
                    .directAnswerUsed(true)
                    .build();
        }

        if (requiresDocumentBackedAnswer(question, routeReason) && !hasDocumentEvidence(documentEvidence)) {
            return AgentEvidenceBundle.builder()
                    .effectiveQuestion("当前未从知识库文档中确认到该制度项的完整枚举信息。")
                    .referenceContent(referenceContent)
                    .build();
        }

        if (!hasKnowledgeEvidence && !hasDocumentEvidence(documentEvidence)) {
            if (shouldAnswerStrictlyFromKnowledge(routeReason, requestedKnowledgeBaseId, knowledgeBaseKeyword, documentKeyword, structuredFactQuestion)) {
                return AgentEvidenceBundle.builder()
                        .effectiveQuestion("当前未从知识库或工具结果中确认到该信息。")
                        .referenceContent(referenceContent)
                        .build();
            }
            return AgentEvidenceBundle.builder()
                    .effectiveQuestion(question)
                    .referenceContent(referenceContent)
                    .build();
        }

        StringBuilder builder = new StringBuilder(question);
        if (hasKnowledgeEvidence) {
            builder.append("\n\n[Prefetched knowledge evidence]\n").append(knowledgeEvidence);
        }
        if (hasDocumentEvidence(documentEvidence)) {
            builder.append("\n\n[Prefetched document evidence]\n").append(documentEvidence);
        }
        appendExecutionProfileInstruction(builder, executionProfile);
        appendExecutionDirective(builder, executionDirective);
        appendStructuredRuleInstruction(builder, question, routeReason, documentEvidence);
        appendScenarioAnswerInstruction(builder, routeDomain, routeIntent);
        builder.append("\nPlease answer from the deepest available evidence. If document body snippets are present, prefer them over metadata-level conclusions.");
        return AgentEvidenceBundle.builder()
                .effectiveQuestion(builder.toString())
                .referenceContent(referenceContent)
                .knowledgeEvidenceUsed(hasKnowledgeEvidence)
                .documentEvidenceUsed(hasDocumentEvidence(documentEvidence))
                .directAnswerUsed(false)
                .build();
    }

    private String buildStructuredFactDirectAnswer(String question, String documentEvidence) {
        if (StrUtil.isBlank(documentEvidence) || !documentEvidence.contains("RESULT_TYPE: STRUCTURED_FACT_SEARCH")) {
            return null;
        }

        List<StructuredFactItem> items = parseStructuredFactItems(documentEvidence);
        if (items.isEmpty()) {
            return null;
        }

        String subject = resolveStructuredFactSubject(question, items);
        StringBuilder builder = new StringBuilder(subject).append("如下：\n");
        for (int i = 0; i < items.size(); i++) {
            StructuredFactItem item = items.get(i);
            builder.append(i + 1).append(". ");
            if (StrUtil.isNotBlank(item.label())) {
                builder.append(item.label()).append("：");
            }
            builder.append(item.value());
            if (StrUtil.isNotBlank(item.documentName())) {
                builder.append("（来源：").append(item.documentName()).append("）");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private List<StructuredFactItem> parseStructuredFactItems(String evidence) {
        List<StructuredFactItem> items = new ArrayList<>();
        String documentName = null;
        String factType = null;
        String factLabel = null;
        String extractedValue = null;
        String snippet = null;

        for (String rawLine : evidence.split("\\R")) {
            String line = StrUtil.blankToDefault(rawLine, "").trim();
            if (line.startsWith("ITEM_")) {
                if (StrUtil.isNotBlank(extractedValue)) {
                    items.add(new StructuredFactItem(
                            documentName,
                            factType,
                            extractedValue,
                            StrUtil.blankToDefault(factLabel, inferStructuredFactLabel(snippet, documentName))
                    ));
                }
                documentName = null;
                factType = null;
                factLabel = null;
                extractedValue = null;
                snippet = null;
                continue;
            }
            if (line.startsWith("document_name:")) {
                documentName = trimToNull(line.substring("document_name:".length()));
                continue;
            }
            if (line.startsWith("fact_type:")) {
                factType = trimToNull(line.substring("fact_type:".length()));
                continue;
            }
            if (line.startsWith("fact_label:")) {
                factLabel = trimToNull(line.substring("fact_label:".length()));
                continue;
            }
            if (line.startsWith("extracted_value:")) {
                extractedValue = trimToNull(line.substring("extracted_value:".length()));
                continue;
            }
            if (line.startsWith("snippet:")) {
                snippet = trimToNull(line.substring("snippet:".length()));
            }
        }

        if (StrUtil.isNotBlank(extractedValue)) {
            items.add(new StructuredFactItem(
                    documentName,
                    factType,
                    extractedValue,
                    StrUtil.blankToDefault(factLabel, inferStructuredFactLabel(snippet, documentName))
            ));
        }
        return items;
    }

    private void appendStructuredRuleInstruction(StringBuilder builder,
                                                 String question,
                                                 String routeReason,
                                                 String documentEvidence) {
        if (StrUtil.isBlank(question) || StrUtil.isBlank(documentEvidence) || !hasDocumentEvidence(documentEvidence)) {
            return;
        }
        if (!"KNOWLEDGE_REQUIRED".equals(routeReason) && !"DOCUMENT_DISCOVERY".equals(routeReason)) {
            return;
        }
        if (!isRuleLikeQuestion(question)) {
            return;
        }

        builder.append("\n\n[Answer format requirements]\n")
                .append("请优先按结构化结果回答，不要泛泛而谈，也不要补充证据里没有的制度内容。\n")
                .append("优先使用以下格式：\n")
                .append("1. 适用类型或范围\n")
                .append("2. 申请条件或限制\n")
                .append("3. 申请方式或审批要求\n")
                .append("4. 注意事项\n")
                .append("如果某一项在证据中没有明确提到，就明确写“知识库未明确说明”，不要自行补充。");
    }

    private void appendExecutionProfileInstruction(StringBuilder builder, String executionProfile) {
        if (builder == null || StrUtil.isBlank(executionProfile)) {
            return;
        }
        if ("DOCUMENT_QA".equalsIgnoreCase(executionProfile)) {
            builder.append("\n\n[Execution profile]\n")
                    .append("This is a document QA task. Stay tightly grounded in the current document and do not drift to unrelated prior session topics.");
            return;
        }
        if ("STRUCTURED_FACT".equalsIgnoreCase(executionProfile)) {
            builder.append("\n\n[Execution profile]\n")
                    .append("This is a structured fact task. Return only explicitly confirmed facts in a concise, field-oriented format.");
        }
    }

    private void appendExecutionDirective(StringBuilder builder, String executionDirective) {
        if (builder == null || StrUtil.isBlank(executionDirective)) {
            return;
        }
        builder.append("\n\n[Strategy execution directive]\n")
                .append(executionDirective.trim());
    }

    private void appendScenarioAnswerInstruction(StringBuilder builder,
                                                 String routeDomain,
                                                 String routeIntent) {
        if (builder == null || (StrUtil.isBlank(routeDomain) && StrUtil.isBlank(routeIntent))) {
            return;
        }

        String guidance = switch (StrUtil.blankToDefault(routeDomain, "")) {
            case "REFUND_AND_RETURNS" -> """
                    退款类问题请尽量覆盖：
                    1. 退款适用条件或范围
                    2. 申请方式或提交渠道
                    3. 审核与到账时效
                    4. 注意事项或限制
                    未在证据中明确写出的内容，不要自行补充。
                    """.trim();
            case "CONTACT_AND_CHANNEL" -> """
                    联系方式类问题请尽量：
                    1. 列出所有已确认的联系方式
                    2. 区分适用场景，如售后、商务、客服
                    3. 只保留证据里明确出现的信息
                    不要把不同场景的联系方式混在一起概括。
                    """.trim();
            case "HR_AND_ADMIN" -> """
                    人事行政类问题请尽量覆盖：
                    1. 适用类型或范围
                    2. 条件、限制或要求
                    3. 提交流程或审批方式
                    4. 注意事项
                    未明确写出的部分请直接说明知识库未明确说明。
                    """.trim();
            case "AFTER_SALES" -> """
                    售后处理类问题请尽量覆盖：
                    1. 处理步骤
                    2. 需要准备的信息或材料
                    3. 处理时效
                    4. 联系渠道或后续动作
                    回答要偏向实际办理指引，而不是泛泛介绍。
                    """.trim();
            case "COMPLAINT_AND_ESCALATION" -> """
                    投诉或升级处理类问题请尽量覆盖：
                    1. 当前建议的处理方式
                    2. 可用渠道或提交流程
                    3. 需要准备的信息
                    4. 是否建议人工介入
                    不要承诺证据中没有明确写出的处理结果。
                    """.trim();
            default -> null;
        };

        if (StrUtil.isBlank(guidance)) {
            return;
        }

        builder.append("\n\n[Customer service answer guidance]\n")
                .append(guidance);
    }

    private boolean isRuleLikeQuestion(String question) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "");
        return normalizedQuestion.contains("规则")
                || normalizedQuestion.contains("制度")
                || normalizedQuestion.contains("政策")
                || normalizedQuestion.contains("流程")
                || normalizedQuestion.contains("规定")
                || normalizedQuestion.contains("哪些")
                || normalizedQuestion.contains("哪几")
                || normalizedQuestion.contains("什么类型")
                || normalizedQuestion.contains("什么种类")
                || normalizedQuestion.contains("包括")
                || normalizedQuestion.contains("都有哪些");
    }

    private String inferStructuredFactLabel(String snippet, String documentName) {
        String haystack = StrUtil.blankToDefault(snippet, "") + "\n" + StrUtil.blankToDefault(documentName, "");
        if (haystack.contains("售后")) {
            return "售后";
        }
        if (haystack.contains("商务")) {
            return "商务";
        }
        if (haystack.contains("客服")) {
            return "客服";
        }
        if (haystack.contains("合作")) {
            return "合作";
        }
        if (haystack.contains("招聘")) {
            return "招聘";
        }
        if (haystack.contains("财务")) {
            return "财务";
        }
        return null;
    }

    private String resolveStructuredFactSubject(String question, List<StructuredFactItem> items) {
        if (StrUtil.isNotBlank(question)) {
            if (question.contains("邮箱")) {
                return "公司相关邮箱";
            }
            if (question.contains("电话") || question.contains("热线") || question.contains("联系方式")) {
                return "公司相关联系方式";
            }
            if (question.contains("地址") || question.contains("位置")) {
                return "公司相关地址";
            }
        }
        if (!items.isEmpty()) {
            String factType = StrUtil.blankToDefault(items.get(0).factType(), "");
            if ("EMAIL".equals(factType)) {
                return "公司相关邮箱";
            }
            if ("PHONE".equals(factType)) {
                return "公司相关联系方式";
            }
            if ("ADDRESS".equals(factType)) {
                return "公司相关地址";
            }
        }
        return "检索到的结构化信息";
    }

    private boolean shouldAnswerStrictlyFromKnowledge(String routeReason,
                                                      Long requestedKnowledgeBaseId,
                                                      String knowledgeBaseKeyword,
                                                      String documentKeyword,
                                                      boolean structuredFactQuestion) {
        if (requestedKnowledgeBaseId != null) {
            return true;
        }
        if (structuredFactQuestion) {
            return true;
        }
        if (StrUtil.isNotBlank(knowledgeBaseKeyword) || StrUtil.isNotBlank(documentKeyword)) {
            return true;
        }
        return "EXPLICIT_KNOWLEDGE_BASE".equals(routeReason)
                || "DOCUMENT_DISCOVERY".equals(routeReason)
                || "STRUCTURED_FACT_QUERY".equals(routeReason)
                || "KNOWLEDGE_REQUIRED".equals(routeReason);
    }

    private boolean hasDocumentEvidence(String documentEvidence) {
        if (StrUtil.isBlank(documentEvidence)) {
            return false;
        }
        if (documentEvidence.contains("RESULT_TYPE: STRUCTURED_FACT_SEARCH")) {
            return documentEvidence.contains("extracted_value:")
                    && !documentEvidence.contains("MATCHED_COUNT: 0");
        }
        return documentEvidence.contains("matched_snippet_count:")
                && !documentEvidence.contains("MATCHED_DOCUMENT_COUNT: 0");
    }

    private boolean hasKnowledgeEvidence(String knowledgeEvidence) {
        return StrUtil.isNotBlank(knowledgeEvidence)
                && knowledgeEvidence.contains("MATCHED_COUNT:")
                && !knowledgeEvidence.contains("MATCHED_COUNT: 0");
    }

    private boolean shouldEscalateToDocumentEvidence(String question,
                                                     String routeReason,
                                                     RecentConversationContext conversationContext,
                                                     String knowledgeEvidence,
                                                     String documentKeyword) {
        if (conversationContext.hasDocument() || StrUtil.isNotBlank(documentKeyword)) {
            return true;
        }
        if (requiresDocumentBackedAnswer(question, routeReason)) {
            return true;
        }
        return hasKnowledgeEvidence(knowledgeEvidence) && sourceFileCount(knowledgeEvidence) == 1;
    }

    private boolean requiresDocumentBackedAnswer(String question, String routeReason) {
        if (!"KNOWLEDGE_REQUIRED".equals(routeReason)
                && !"DOCUMENT_DISCOVERY".equals(routeReason)
                && !"KNOWLEDGE_AND_GENERATION".equals(routeReason)
                && !"KNOWLEDGE_AND_FORM_GENERATION".equals(routeReason)) {
            return false;
        }
        String normalizedQuestion = StrUtil.blankToDefault(question, "");
        boolean enumerationQuestion = normalizedQuestion.contains("哪些")
                || normalizedQuestion.contains("哪几")
                || normalizedQuestion.contains("什么类型")
                || normalizedQuestion.contains("什么种类")
                || normalizedQuestion.contains("包括")
                || normalizedQuestion.contains("都有哪些")
                || normalizedQuestion.contains("可以");
        boolean businessProcessQuestion = normalizedQuestion.contains("请假")
                || (normalizedQuestion.contains("假") && (normalizedQuestion.contains("请") || normalizedQuestion.contains("休")))
                || normalizedQuestion.contains("退款")
                || normalizedQuestion.contains("报销")
                || normalizedQuestion.contains("审批");
        return enumerationQuestion && businessProcessQuestion;
    }

    private String searchStructuredFactDocumentEvidence(LoginUser loginUser,
                                                        String question,
                                                        String knowledgeBaseKeyword,
                                                        String currentEvidence) {
        String latestEvidence = currentEvidence;
        for (String candidate : List.of("公司简介", "联系我们", "联系方式", "企业介绍", "客服")) {
            String evidence = knowledgeAgentFacade.searchDocumentContent(
                    loginUser,
                    question,
                    knowledgeBaseKeyword,
                    candidate,
                    8
            );
            if (hasDocumentEvidence(evidence)) {
                log.info("Structured fact document fallback hit question={}, documentKeyword={}",
                        StrUtil.blankToDefault(question, ""), candidate);
                return evidence;
            }
            if (StrUtil.isBlank(latestEvidence) && StrUtil.isNotBlank(evidence)) {
                latestEvidence = evidence;
            }
        }
        return latestEvidence;
    }

    private String buildReferenceContent(Long requestedKnowledgeBaseId,
                                         String routeMode,
                                         String routeReason,
                                         String routeDomain,
                                         String routeIntent,
                                         RecentConversationContext conversationContext,
                                         String knowledgeBaseKeyword,
                                         String knowledgeEvidence,
                                         String documentEvidence) {
        String autoScopeName = resolveAutoScopeName(requestedKnowledgeBaseId, conversationContext, knowledgeBaseKeyword);
        LinkedHashSet<String> references = collectReferenceKnowledgeBases(
                conversationContext,
                requestedKnowledgeBaseId,
                knowledgeBaseKeyword,
                knowledgeEvidence,
                documentEvidence
        );

        if (StrUtil.isBlank(autoScopeName) && references.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("ROUTE_MODE: ")
                .append(resolveRouteMode(routeMode))
                .append("\n");
        if (StrUtil.isNotBlank(routeReason)) {
            builder.append("ROUTE_REASON: ").append(routeReason).append("\n");
        }
        if (StrUtil.isNotBlank(routeDomain)) {
            builder.append("ROUTE_DOMAIN: ").append(routeDomain).append("\n");
        }
        if (StrUtil.isNotBlank(routeIntent)) {
            builder.append("ROUTE_INTENT: ").append(routeIntent).append("\n");
        }
        if (StrUtil.isNotBlank(autoScopeName)) {
            builder.append("SCOPE_HINT: ").append(autoScopeName).append("\n");
        }

        builder.append("REFERENCE_COUNT: ").append(references.size()).append("\n");
        int referenceIndex = 1;
        for (String reference : references) {
            builder.append("REFERENCE_").append(referenceIndex).append(": ").append(reference).append("\n");
            referenceIndex++;
        }
        return builder.toString().trim();
    }

    private String resolveRouteMode(String routeMode) {
        if (StrUtil.isBlank(routeMode)) {
            return ChatRouteModeEnum.UNIFIED_AGENT.name();
        }
        try {
            return ChatRouteModeEnum.valueOf(routeMode.trim()).name();
        } catch (IllegalArgumentException ignored) {
            return ChatRouteModeEnum.UNIFIED_AGENT.name();
        }
    }

    private String resolveAutoScopeName(Long requestedKnowledgeBaseId,
                                        RecentConversationContext conversationContext,
                                        String knowledgeBaseKeyword) {
        if (requestedKnowledgeBaseId != null) {
            return null;
        }
        if (conversationContext != null
                && conversationContext.hasKnowledgeBase()
                && !conversationContext.isExplicitKnowledgeBaseInQuestion()) {
            return conversationContext.getKnowledgeBaseName();
        }
        return null;
    }

    private LinkedHashSet<String> collectReferenceKnowledgeBases(RecentConversationContext conversationContext,
                                                                 Long requestedKnowledgeBaseId,
                                                                 String knowledgeBaseKeyword,
                                                                 String knowledgeEvidence,
                                                                 String documentEvidence) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        if (conversationContext != null && conversationContext.hasKnowledgeBase()) {
            references.add(conversationContext.getKnowledgeBaseName());
        }
        if (requestedKnowledgeBaseId == null) {
            String resolvedKeyword = trimToNull(knowledgeBaseKeyword);
            if (resolvedKeyword != null) {
                references.add(resolvedKeyword);
            }
        }
        appendValuesByPrefix(references, documentEvidence, "knowledge_base_name:");
        appendValuesByPrefix(references, knowledgeEvidence, "KNOWLEDGE_BASE_KEYWORD:");
        return references;
    }

    private void appendValuesByPrefix(LinkedHashSet<String> target, String content, String prefix) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        for (String line : content.split("\\R")) {
            if (!line.startsWith(prefix)) {
                continue;
            }
            String value = trimToNull(line.substring(prefix.length()));
            if (value != null) {
                target.add(value);
            }
        }
    }

    private String firstSourceFile(String evidence) {
        if (StrUtil.isBlank(evidence)) {
            return null;
        }
        for (String line : evidence.split("\\R")) {
            if (line.startsWith("source_file:")) {
                return trimToNull(line.substring("source_file:".length()));
            }
        }
        return null;
    }

    private int sourceFileCount(String evidence) {
        if (StrUtil.isBlank(evidence)) {
            return 0;
        }
        int count = 0;
        for (String line : evidence.split("\\R")) {
            if (line.startsWith("source_file:")) {
                count++;
            }
        }
        return count;
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private record StructuredFactItem(String documentName, String factType, String value, String label) {
    }
}
