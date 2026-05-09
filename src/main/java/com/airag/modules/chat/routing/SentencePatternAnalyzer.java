package com.airag.modules.chat.routing;

public interface SentencePatternAnalyzer {

    SentencePatternFeatures analyze(String question);
}
