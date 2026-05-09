package com.airag.modules.chat.routing;

public interface QuestionIntentFeatureExtractor {

    QuestionIntentFeatures extract(String question);
}
