package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.AgentQuestionClassifier;
import com.airag.modules.chat.routing.QuestionIntentFeatureExtractor;
import com.airag.modules.chat.routing.QuestionIntentFeatures;
import com.airag.modules.chat.routing.QuestionIntentKeywordCatalog;
import org.springframework.stereotype.Service;

@Service
public class AgentQuestionClassifierImpl implements AgentQuestionClassifier {

    private final QuestionIntentFeatureExtractor featureExtractor;

    public AgentQuestionClassifierImpl(QuestionIntentFeatureExtractor featureExtractor) {
        this.featureExtractor = featureExtractor;
    }

    @Override
    public boolean isKnowledgeBaseListQuestion(String question) {
        return featureExtractor.extract(question).knowledgeBaseListQuestion();
    }

    @Override
    public String resolveKnowledgeBaseKeyword(String question, RecentConversationContext conversationContext) {
        if (conversationContext.hasKnowledgeBase()) {
            return conversationContext.getKnowledgeBaseName();
        }
        if (StrUtil.isBlank(question) || !question.contains("知识库")) {
            return null;
        }

        int end = question.indexOf("知识库");
        int start = end;
        while (start > 0 && !isKeywordBoundary(question.charAt(start - 1))) {
            start--;
        }
        return trimToNull(question.substring(start, end + 3));
    }

    @Override
    public String resolveDocumentKeyword(String question, RecentConversationContext conversationContext) {
        if (conversationContext.hasDocument()) {
            return conversationContext.getDocumentName();
        }
        if (StrUtil.isBlank(question)) {
            return null;
        }

        String normalized = question;
        for (String stopWord : QuestionIntentKeywordCatalog.DOCUMENT_STOP_WORDS) {
            normalized = normalized.replace(stopWord, "");
        }
        normalized = normalized.trim();

        if (normalized.contains("简历")) {
            return "简历";
        }
        if (normalized.contains("合同")) {
            return "合同";
        }
        if (normalized.contains("手册")) {
            return "手册";
        }
        if (conversationContext.hasKnowledgeBase() && question.toLowerCase().contains("pdf")) {
            return question;
        }
        return null;
    }

    @Override
    public boolean isStructuredFactQuestion(String question) {
        QuestionIntentFeatures features = featureExtractor.extract(question);
        return features.structuredFact();
    }

    private boolean isKeywordBoundary(char current) {
        return Character.isWhitespace(current)
                || current == '，'
                || current == ','
                || current == '。'
                || current == '.'
                || current == '：'
                || current == ':'
                || current == '？'
                || current == '?'
                || current == '；';
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
