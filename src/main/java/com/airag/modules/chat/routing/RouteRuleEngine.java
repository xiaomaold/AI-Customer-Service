package com.airag.modules.chat.routing;

public interface RouteRuleEngine {

    QuestionIntentFeatures compose(SentencePatternFeatures sentencePatterns,
                                   BusinessSignalFeatures businessSignals,
                                   EnterpriseNeedLevel enterpriseNeedLevel);
}
