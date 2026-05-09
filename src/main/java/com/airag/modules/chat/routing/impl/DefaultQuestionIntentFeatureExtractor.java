package com.airag.modules.chat.routing.impl;

import com.airag.modules.chat.routing.BusinessSignalAnalyzer;
import com.airag.modules.chat.routing.BusinessSignalFeatures;
import com.airag.modules.chat.routing.EnterpriseNeedClassifier;
import com.airag.modules.chat.routing.EnterpriseNeedLevel;
import com.airag.modules.chat.routing.QuestionIntentFeatureExtractor;
import com.airag.modules.chat.routing.QuestionIntentFeatures;
import com.airag.modules.chat.routing.RouteRuleEngine;
import com.airag.modules.chat.routing.SentencePatternAnalyzer;
import com.airag.modules.chat.routing.SentencePatternFeatures;
import org.springframework.stereotype.Service;

@Service
public class DefaultQuestionIntentFeatureExtractor implements QuestionIntentFeatureExtractor {

    private final SentencePatternAnalyzer sentencePatternAnalyzer;
    private final BusinessSignalAnalyzer businessSignalAnalyzer;
    private final EnterpriseNeedClassifier enterpriseNeedClassifier;
    private final RouteRuleEngine routeRuleEngine;

    public DefaultQuestionIntentFeatureExtractor(SentencePatternAnalyzer sentencePatternAnalyzer,
                                                 BusinessSignalAnalyzer businessSignalAnalyzer,
                                                 EnterpriseNeedClassifier enterpriseNeedClassifier,
                                                 RouteRuleEngine routeRuleEngine) {
        this.sentencePatternAnalyzer = sentencePatternAnalyzer;
        this.businessSignalAnalyzer = businessSignalAnalyzer;
        this.enterpriseNeedClassifier = enterpriseNeedClassifier;
        this.routeRuleEngine = routeRuleEngine;
    }

    @Override
    public QuestionIntentFeatures extract(String question) {
        SentencePatternFeatures sentencePatterns = sentencePatternAnalyzer.analyze(question);
        BusinessSignalFeatures businessSignals = businessSignalAnalyzer.analyze(question);
        EnterpriseNeedLevel enterpriseNeedLevel = enterpriseNeedClassifier.classify(question, sentencePatterns, businessSignals);
        return routeRuleEngine.compose(sentencePatterns, businessSignals, enterpriseNeedLevel);
    }
}
