package com.airag.modules.agent.query.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.query.KnowledgeQueryPlanner;
import com.airag.modules.chat.routing.QuestionIntentFeatureExtractor;
import com.airag.modules.chat.routing.QuestionIntentFeatures;
import com.airag.modules.chat.routing.QuestionIntentKeywordCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class KnowledgeQueryPlannerImpl implements KnowledgeQueryPlanner {

    private static final List<String> KNOWLEDGE_TOPIC_SUFFIXES = List.of(
            "规则", "流程", "制度", "政策", "规范", "办法", "说明", "指引", "要求", "标准"
    );

    private static final List<String> GENERATION_WRAPPERS = List.of(
            "帮我", "给我", "请帮我", "麻烦帮我",
            "生成", "写", "做", "弄", "整理"
    );

    private static final List<String> FORM_SUFFIXES = List.of(
            "申请表", "登记表", "审批表", "表格", "表单", "模板", "申请书", "邮件", "通知", "表"
    );

    private static final List<String> GENERIC_TOKENS = List.of(
            "一个", "一份", "一张", "一下", "用于", "关于", "相关", "企业", "公司", "对应"
    );

    private final QuestionIntentFeatureExtractor featureExtractor;

    public KnowledgeQueryPlannerImpl(QuestionIntentFeatureExtractor featureExtractor) {
        this.featureExtractor = featureExtractor;
    }

    @Override
    public List<String> buildKnowledgeSearchQueries(String question) {
        if (StrUtil.isBlank(question)) {
            return List.of("");
        }

        String safeQuestion = StrUtil.blankToDefault(question, "").trim();
        QuestionIntentFeatures features = featureExtractor.extract(safeQuestion);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(safeQuestion);

        if (features.knowledgeRequired()) {
            queries.addAll(buildKnowledgeTopicQueries(safeQuestion, features.generationRequested()));
        }
        if (features.phoneQuery()) {
            queries.addAll(QuestionIntentKeywordCatalog.PHONE_FACT_QUERIES);
        }
        if (features.emailQuery()) {
            queries.addAll(QuestionIntentKeywordCatalog.EMAIL_FACT_QUERIES);
        }
        if (features.addressQuery()) {
            queries.addAll(QuestionIntentKeywordCatalog.ADDRESS_FACT_QUERIES);
        }
        if (features.companyIntroQuery()) {
            queries.add("公司简介");
            queries.add("企业介绍");
        }

        return queries.stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    @Override
    public List<String> buildDocumentSearchQueries(String question, String documentName) {
        String safeQuestion = StrUtil.blankToDefault(question, "");
        List<String> queries = new ArrayList<>();
        queries.add(StrUtil.format("围绕文档《{}》回答：{}", documentName, safeQuestion));

        if (isEnumeratingDocumentQuestion(question)) {
            queries.add(StrUtil.format("文档《{}》中的完整相关信息：{}", documentName, safeQuestion));
        }
        if (safeQuestion.contains("项目")) {
            queries.add(StrUtil.format("文档《{}》中的项目经历", documentName));
        }
        if (safeQuestion.contains("简历")) {
            queries.add(StrUtil.format("文档《{}》中的个人信息和项目经历", documentName));
        }
        if (containsAny(safeQuestion, QuestionIntentKeywordCatalog.DOCUMENT_CONTACT_ALIASES)) {
            queries.add(StrUtil.format("文档《{}》中的姓名和联系方式", documentName));
        }

        return queries.stream().filter(StrUtil::isNotBlank).distinct().toList();
    }

    @Override
    public boolean isEnumeratingDocumentQuestion(String question) {
        return containsAny(question, QuestionIntentKeywordCatalog.DOCUMENT_ENUMERATION_ALIASES);
    }

    private List<String> buildKnowledgeTopicQueries(String question, boolean generationRequested) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String normalized = trimTrailingPunctuation(question);
        String topic = generationRequested
                ? extractBusinessTopicFromGeneration(normalized)
                : extractKnowledgeTopic(normalized);

        if (StrUtil.isBlank(topic)) {
            topic = extractKnowledgeTopic(normalized);
        }

        if (!containsInterrogative(normalized) && !generationRequested) {
            queries.add(normalized + "是什么");
            queries.add(normalized + "有哪些");
        }

        if (StrUtil.isBlank(topic)) {
            return new ArrayList<>(queries);
        }

        queries.add(topic);
        queries.add(topic + "规则");
        queries.add(topic + "流程");
        queries.add(topic + "制度");
        queries.add(topic + "政策");
        queries.add(topic + "说明");

        if (topic.contains("退款")) {
            queries.add("退款规则");
            queries.add("退款政策");
            queries.add("退款与售后政策");
            queries.add("售后政策");
            queries.add("退款申请方式");
            queries.add("审核与到账时效");
        }
        if (topic.contains("请假")) {
            queries.add("请假制度");
            queries.add("请假流程");
            queries.add("请假规定");
            if (normalized.contains("生病") || normalized.contains("病假")) {
                queries.add("病假规定");
                queries.add("病假流程");
            }
        }
        if (topic.contains("报销")) {
            queries.add("报销制度");
            queries.add("报销流程");
            queries.add("报销规定");
        }

        return new ArrayList<>(queries);
    }

    private String extractBusinessTopicFromGeneration(String question) {
        String wholeQuestion = trimTrailingPunctuation(question);
        String aliasFromWholeQuestion = matchBusinessAlias(wholeQuestion);
        if (StrUtil.isNotBlank(aliasFromWholeQuestion)) {
            return aliasFromWholeQuestion;
        }

        String leadingClause = splitLeadingClause(question);
        String normalized = trimTrailingPunctuation(leadingClause);

        for (String wrapper : GENERATION_WRAPPERS) {
            normalized = normalized.replace(wrapper, "");
        }
        for (String token : GENERIC_TOKENS) {
            normalized = normalized.replace(token, "");
        }
        for (String suffix : FORM_SUFFIXES) {
            normalized = normalized.replace(suffix, "");
        }

        String aliasMatch = matchBusinessAlias(normalized);
        if (StrUtil.isNotBlank(aliasMatch)) {
            return aliasMatch;
        }

        return normalized.trim();
    }

    private String extractKnowledgeTopic(String question) {
        String normalized = trimTrailingPunctuation(question);
        if (isLeaveEnumerationQuestion(normalized)) {
            return "请假";
        }
        for (String suffix : KNOWLEDGE_TOPIC_SUFFIXES) {
            if (!normalized.endsWith(suffix) || normalized.length() <= suffix.length()) {
                continue;
            }
            return normalized.substring(0, normalized.length() - suffix.length());
        }
        return null;
    }

    private String splitLeadingClause(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        String[] separators = {"，", ",", "。", "；", ";", "：", ":"};
        for (String separator : separators) {
            int index = normalized.indexOf(separator);
            if (index > 0) {
                return normalized.substring(0, index);
            }
        }
        return normalized;
    }

    private String matchBusinessAlias(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        if (isLeaveEnumerationQuestion(text)) {
            return "请假";
        }
        return QuestionIntentKeywordCatalog.BUSINESS_PROCESS_ALIASES.stream()
                .filter(alias -> !"申请".equals(alias))
                .filter(text::contains)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .findFirst()
                .orElse(null);
    }

    private boolean isLeaveEnumerationQuestion(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        return text.contains("假")
                && (text.contains("请") || text.contains("休"))
                && (text.contains("哪些")
                || text.contains("哪几")
                || text.contains("什么类型")
                || text.contains("什么种类")
                || text.contains("包括")
                || text.contains("都有哪些")
                || text.contains("可以"));
    }

    private boolean containsInterrogative(String question) {
        return question.contains("什么")
                || question.contains("哪些")
                || question.contains("多少")
                || question.contains("怎么")
                || question.contains("如何")
                || question.contains("吗")
                || question.contains("?");
    }

    private String trimTrailingPunctuation(String question) {
        return StrUtil.blankToDefault(question, "")
                .trim()
                .replaceAll("[？?。！!；;，,]+$", "");
    }

    private boolean containsAny(String question, List<String> aliases) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return aliases.stream().anyMatch(question::contains);
    }
}
