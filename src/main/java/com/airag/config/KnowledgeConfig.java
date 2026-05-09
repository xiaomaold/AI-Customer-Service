package com.airag.config;

import com.airag.modules.knowledge.store.PgVectorKnowledgeEmbeddingStore;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeConfig {

    @Bean
    public ApacheTikaDocumentParser apacheTikaDocumentParser() {
        return new ApacheTikaDocumentParser();
    }

    @Bean
    public DocumentSplitter documentSplitter(KnowledgeProperties knowledgeProperties) {
        return DocumentSplitters.recursive(
                knowledgeProperties.getMaxSegmentSize(),
                knowledgeProperties.getMaxOverlapSize()
        );
    }

    @Bean
    public PgVectorKnowledgeEmbeddingStore pgVectorKnowledgeEmbeddingStore(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate,
            KnowledgeProperties knowledgeProperties) {
        return new PgVectorKnowledgeEmbeddingStore(pgVectorJdbcTemplate, knowledgeProperties.getEmbeddingDimension());
    }

    @Bean
    public EmbeddingStore<TextSegment> knowledgeEmbeddingStore(PgVectorKnowledgeEmbeddingStore store) {
        return store;
    }
}
