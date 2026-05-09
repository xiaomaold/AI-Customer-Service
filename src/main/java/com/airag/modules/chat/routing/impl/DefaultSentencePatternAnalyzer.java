package com.airag.modules.chat.routing.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.chat.routing.QuestionIntentKeywordCatalog;
import com.airag.modules.chat.routing.SentencePatternAnalyzer;
import com.airag.modules.chat.routing.SentencePatternFeatures;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultSentencePatternAnalyzer implements SentencePatternAnalyzer {

    private static final List<String> DEFINITION_PATTERNS = List.of("什么是", "是什么", "是什么意思", "如何理解", "怎么理解");
    private static final List<String> PRINCIPLE_PATTERNS = List.of("原理", "机制", "底层", "怎么实现");
    private static final List<String> COMPARISON_PATTERNS = List.of("区别", "对比", "优缺点", "不同");
    private static final List<String> REASON_PATTERNS = List.of("为什么", "原因");
    private static final List<String> GENERAL_FOLLOW_UP_PATTERNS = List.of(
            "具体一点", "具体说", "具体说一下", "具体位置", "详细一点", "详细说", "详细说说",
            "展开说", "展开说说", "多说一些", "说一些", "再说一些", "继续说", "继续讲",
            "补充一点", "多介绍一些", "讲详细一点"
    );

    @Override
    public SentencePatternFeatures analyze(String question) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "").trim().toLowerCase();
        return new SentencePatternFeatures(
                containsAny(normalizedQuestion, DEFINITION_PATTERNS),
                containsAny(normalizedQuestion, PRINCIPLE_PATTERNS),
                containsAny(normalizedQuestion, COMPARISON_PATTERNS),
                containsAny(normalizedQuestion, REASON_PATTERNS),
                containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.GENERAL_GENERATION_ALIASES),
                containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.DOCUMENT_DISCOVERY_ALIASES),
                containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.KNOWLEDGE_BASE_LIST_ALIASES)
                        && !containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.KNOWLEDGE_BASE_LIST_EXCLUSIONS),
                isFollowUpRewrite(normalizedQuestion),
                containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.CHAT_ALIASES)
        );
    }

    private boolean isFollowUpRewrite(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        if (question.length() <= 12
                && (question.startsWith("再") || question.startsWith("改") || question.startsWith("换"))) {
            return true;
        }
        return containsAny(question, QuestionIntentKeywordCatalog.FOLLOW_UP_GENERATION_ALIASES)
                || containsAny(question, GENERAL_FOLLOW_UP_PATTERNS);
    }

    private boolean containsAny(String question, List<String> patterns) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return patterns.stream().anyMatch(question::contains);
    }
}
