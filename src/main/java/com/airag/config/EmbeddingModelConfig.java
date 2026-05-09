package com.airag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingModelConfig {

    @Bean
    @ConfigurationProperties(prefix = "langchain4j.community.dashscope.embedding-model")
    public QwenEmbeddingModelProperties qwenEmbeddingModelProperties() {
        return new QwenEmbeddingModelProperties();
    }

    @Bean
    public EmbeddingModel embeddingModel(QwenEmbeddingModelProperties properties) {
        return QwenEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .build();
    }

    public static class QwenEmbeddingModelProperties {
        private String apiKey = "";
        private String modelName = "text-embedding-v4";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }
}
