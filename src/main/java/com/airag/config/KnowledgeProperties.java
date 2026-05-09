package com.airag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "rag.knowledge")
public class KnowledgeProperties {

    private String uploadDir = "data/uploads";

    private Integer maxSegmentSize = 500;

    private Integer maxOverlapSize = 100;

    private Integer retrievalMaxResults = 5;

    private Double minScore = 0.6D;

    private Integer embeddingDimension = 1536;

    private List<String> allowedExtensions = List.of("pdf", "doc", "docx", "txt", "md", "markdown");

    private Async async = new Async();

    @Data
    public static class Async {
        private String exchange = "rag.knowledge.exchange";
        private String queue = "rag.knowledge.document.process";
        private String routingKey = "rag.knowledge.document.process";
    }
}
