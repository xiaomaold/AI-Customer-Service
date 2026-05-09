package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentAnswerValidator;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentAnswerValidatorImpl implements AgentAnswerValidator {

    private static final List<String> INTERNAL_FIELD_MARKERS = List.of(
            "DOCUMENT_NAMES_INCLUDED",
            "RESULT_TYPE:",
            "MATCHED_COUNT:",
            "MATCHED_BASE_COUNT:",
            "SEARCH_SCOPE:",
            "ITEM_",
            "BASE_",
            "QUERY_KEYWORD:",
            "KNOWLEDGE_BASE_KEYWORD:",
            "DOCUMENT_KEYWORD:",
            "QUESTION:"
    );

    private static final Pattern DOCUMENT_COUNT_PATTERN = Pattern.compile("(\\d+)\\s*个文档");
    private static final Pattern BOOK_TITLE_PATTERN = Pattern.compile("《[^》]{2,80}》");

    @Override
    public ValidationResult validate(String question, String answer) {
        String sanitized = sanitizeInternalFields(answer);
        boolean changedBySanitize = !StrUtil.equals(answer, sanitized);

        if (isProgressOnlyAnswer(sanitized)) {
            return new ValidationResult(false, true, sanitized, "progress_only_answer");
        }

        if (isCountOnlyQuestion(question) && looksLikeDocumentList(sanitized)) {
            return new ValidationResult(true, false, buildCountOnlyAnswer(sanitized), "count_only_question_with_list_answer");
        }

        if (isRandomDocumentContentQuestion(question) && mentionsInternalProtocol(answer)) {
            return new ValidationResult(false, true, sanitized, "content_request_answered_with_internal_protocol");
        }

        if (isEnterpriseFactQuestion(question) && looksLikeAssertiveEnterpriseFactAnswer(sanitized)) {
            return new ValidationResult(false, true, sanitized, "enterprise_fact_without_confirmed_evidence");
        }

        if (changedBySanitize) {
            return new ValidationResult(true, false, sanitized, "internal_fields_removed");
        }

        return new ValidationResult(true, false, sanitized, "");
    }

    @Override
    public String buildRetryQuestion(String originalQuestion, ValidationResult validationResult) {
        return StrUtil.format("""
                {}

                [系统纠正要求]
                上一版回答存在问题：{}。
                请重新调用必要工具后作答，并严格遵守以下限制：
                1. 只能基于工具真实结果回答。
                2. 不要输出内部字段名、协议字段或工具执行过程。
                3. 不要补造知识库名、文档名、来源文件名、公司名、地址、电话、邮箱等主体信息。
                4. 不要输出“正在查询”“请稍候”“我来帮你查一下”等过程话术。
                5. 如果信息不足，请直接回答“当前未从知识库或工具结果中确认到该信息”。
                """, originalQuestion, StrUtil.blankToDefault(validationResult.reason(), "回答不符合约束"));
    }

    private String sanitizeInternalFields(String answer) {
        if (StrUtil.isBlank(answer)) {
            return "";
        }
        String sanitized = Arrays.stream(answer.split("\\R"))
                .filter(line -> INTERNAL_FIELD_MARKERS.stream().noneMatch(line::contains))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return sanitized.replaceAll("\\n{3,}", "\n\n").trim();
    }

    private boolean isCountOnlyQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        boolean asksCount = question.contains("几个文档")
                || question.contains("多少个文档")
                || question.contains("文档数量")
                || question.contains("共有几份")
                || question.contains("总共几份");
        boolean asksList = question.contains("哪些文档")
                || question.contains("有哪些文档")
                || question.contains("列出文档")
                || question.contains("文档列表")
                || question.contains("包括哪些");
        return asksCount && !asksList;
    }

    private boolean isRandomDocumentContentQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return question.contains("随便一个文档")
                || question.contains("任选一个文档")
                || question.contains("其中一个文档内容")
                || question.contains("任意一个文档内容")
                || question.contains("说一下其中一个文档");
    }

    private boolean isEnterpriseFactQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return question.contains("公司叫什么")
                || question.contains("公司名称")
                || question.contains("公司全称")
                || question.contains("地址是什么")
                || question.contains("公司地址")
                || question.contains("电话是多少")
                || question.contains("联系电话")
                || question.contains("邮箱是什么")
                || question.contains("公司邮箱")
                || question.contains("法人是谁")
                || question.contains("官网是什么")
                || question.contains("公司官网");
    }

    private boolean looksLikeAssertiveEnterpriseFactAnswer(String answer) {
        if (StrUtil.isBlank(answer)) {
            return false;
        }
        boolean hasHedging = answer.contains("当前未从知识库或工具结果中确认到该信息")
                || answer.contains("暂未确认")
                || answer.contains("未检索到")
                || answer.contains("无法确认")
                || answer.contains("信息不足");
        if (hasHedging) {
            return false;
        }
        return answer.contains("公司名称是")
                || answer.contains("公司全称是")
                || answer.contains("地址是")
                || answer.contains("电话是")
                || answer.contains("邮箱是")
                || answer.contains("法人是")
                || answer.contains("官网是")
                || answer.contains("有限公司")
                || answer.contains("股份有限公司");
    }

    private boolean mentionsInternalProtocol(String answer) {
        if (StrUtil.isBlank(answer)) {
            return false;
        }
        return INTERNAL_FIELD_MARKERS.stream().anyMatch(answer::contains)
                || answer.contains("无法直接获取")
                || answer.contains("未提供文档名称")
                || answer.contains("未提供文件名")
                || answer.contains("未提供正文内容");
    }

    private boolean looksLikeDocumentList(String answer) {
        if (StrUtil.isBlank(answer)) {
            return false;
        }
        int titleCount = 0;
        Matcher matcher = BOOK_TITLE_PATTERN.matcher(answer);
        while (matcher.find()) {
            titleCount++;
        }
        int bulletCount = 0;
        for (String line : answer.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("1.") || trimmed.startsWith("2.")) {
                bulletCount++;
            }
        }
        return titleCount >= 2 || bulletCount >= 2;
    }

    private String buildCountOnlyAnswer(String answer) {
        Matcher matcher = DOCUMENT_COUNT_PATTERN.matcher(answer);
        if (matcher.find()) {
            return "该知识库当前共有 " + matcher.group(1) + " 个文档。";
        }
        return "我目前只能确认该知识库包含若干文档。如需，我可以继续列出真实文档名称。";
    }

    private boolean isProgressOnlyAnswer(String answer) {
        if (StrUtil.isBlank(answer)) {
            return false;
        }
        String normalized = answer.replace("…", "").replace("...", "").trim();
        return normalized.startsWith("正在为您查询")
                || normalized.startsWith("正在为你查询")
                || normalized.startsWith("正在查询")
                || normalized.startsWith("请稍候")
                || normalized.startsWith("请稍等")
                || normalized.startsWith("我来帮您查询")
                || normalized.startsWith("我来帮你查询")
                || normalized.startsWith("我先帮您查询")
                || normalized.startsWith("我先帮你查询");
    }
}
