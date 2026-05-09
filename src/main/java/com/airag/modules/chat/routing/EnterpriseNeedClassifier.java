package com.airag.modules.chat.routing;

public interface EnterpriseNeedClassifier {

    EnterpriseNeedLevel classify(String question, SentencePatternFeatures sentencePatterns, BusinessSignalFeatures businessSignals);
}
