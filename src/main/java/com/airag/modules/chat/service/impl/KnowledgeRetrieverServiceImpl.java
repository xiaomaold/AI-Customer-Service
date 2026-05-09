package com.airag.modules.chat.service.impl;

import com.airag.config.KnowledgeProperties;
import com.airag.modules.chat.routing.QuestionIntentKeywordCatalog;
import com.airag.modules.chat.service.KnowledgeRetrieverService;
import com.airag.modules.knowledge.store.PgVectorKnowledgeEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrieverServiceImpl implements KnowledgeRetrieverService {

    private final EmbeddingModel embeddingModel;
    private final KnowledgeProperties knowledgeProperties;
    private final EmbeddingStore<TextSegment> knowledgeEmbeddingStore;
    private final PgVectorKnowledgeEmbeddingStore pgVectorKnowledgeEmbeddingStore;

    @Override
    public List<RetrievedChunk> retrieve(String question, int topK) {
        return retrieve(question, topK, (List<Long>) null);
    }

    @Override
    public List<RetrievedChunk> retrieve(String question, int topK, Long knowledgeBaseId) {
        return retrieve(question, topK, knowledgeBaseId == null ? null : List.of(knowledgeBaseId));
    }

    @Override
    public List<RetrievedChunk> retrieve(String question, int topK, List<Long> knowledgeBaseIds) {
        Embedding embedding = embeddingModel.embed(question).content();
        int maxResults = topK > 0 ? topK : knowledgeProperties.getRetrievalMaxResults();
        String retrievalMode = "VECTOR_MAIN";

        List<EmbeddingMatch<TextSegment>> matches = search(
                buildSearchRequest(embedding, maxResults, knowledgeProperties.getMinScore()),
                knowledgeBaseIds
        );

        if (matches.isEmpty() && knowledgeProperties.getMinScore() > 0D) {
            log.info("Knowledge retrieval miss mode=VECTOR_MAIN, minScore={}, knowledgeBaseIds={}, question={}; retrying mode=VECTOR_FALLBACK",
                    knowledgeProperties.getMinScore(), knowledgeBaseIds, question);
            matches = search(buildSearchRequest(embedding, maxResults, 0D), knowledgeBaseIds);
            if (!matches.isEmpty()) {
                retrievalMode = "VECTOR_FALLBACK";
            }
        }

        if (matches.isEmpty() && shouldUseKeywordFallback(question)) {
            log.info("Knowledge retrieval miss mode=VECTOR_FALLBACK, knowledgeBaseIds={}, question={}; retrying mode=KEYWORD_FALLBACK",
                    knowledgeBaseIds, question);
            matches = keywordSearch(question, maxResults, knowledgeBaseIds);
            if (!matches.isEmpty()) {
                retrievalMode = "KEYWORD_FALLBACK";
            }
        }

        if (!matches.isEmpty()) {
            log.info("Knowledge retrieval hit mode={}, hitCount={}, knowledgeBaseIds={}, question={}",
                    retrievalMode, matches.size(), knowledgeBaseIds, question);
        } else {
            log.info("Knowledge retrieval exhausted mode=NONE, knowledgeBaseIds={}, question={}",
                    knowledgeBaseIds, question);
        }

        return matches.stream()
                .map(match -> new RetrievedChunk(
                        match.embeddingId(),
                        match.embedded().metadata().getLong(PgVectorKnowledgeEmbeddingStore.METADATA_KNOWLEDGE_BASE_ID),
                        match.embedded().metadata().getLong(PgVectorKnowledgeEmbeddingStore.METADATA_DOCUMENT_ID),
                        match.embedded().metadata().getInteger(PgVectorKnowledgeEmbeddingStore.METADATA_CHUNK_INDEX),
                        match.embedded().metadata().getString(PgVectorKnowledgeEmbeddingStore.METADATA_FILE_NAME),
                        match.embedded().text(),
                        match.score()))
                .toList();
    }

    private EmbeddingSearchRequest buildSearchRequest(Embedding embedding, int maxResults, double minScore) {
        return EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    private List<EmbeddingMatch<TextSegment>> search(EmbeddingSearchRequest request, List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            EmbeddingSearchResult<TextSegment> result = knowledgeEmbeddingStore.search(request);
            return result.matches();
        }
        if (knowledgeBaseIds.size() == 1) {
            return pgVectorKnowledgeEmbeddingStore.searchByKnowledgeBaseId(request, knowledgeBaseIds.get(0));
        }
        return pgVectorKnowledgeEmbeddingStore.searchByKnowledgeBaseIds(request, knowledgeBaseIds);
    }

    private List<EmbeddingMatch<TextSegment>> keywordSearch(String question, int maxResults, List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return pgVectorKnowledgeEmbeddingStore.keywordSearch(question, maxResults);
        }
        if (knowledgeBaseIds.size() == 1) {
            return pgVectorKnowledgeEmbeddingStore.keywordSearchByKnowledgeBaseId(question, maxResults, knowledgeBaseIds.get(0));
        }
        return pgVectorKnowledgeEmbeddingStore.keywordSearchByKnowledgeBaseIds(question, maxResults, knowledgeBaseIds);
    }

    private boolean shouldUseKeywordFallback(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return containsAny(question, QuestionIntentKeywordCatalog.BUSINESS_PROCESS_ALIASES)
                || containsAny(question, QuestionIntentKeywordCatalog.KNOWLEDGE_TOPIC_SUFFIXES)
                || containsAny(question, QuestionIntentKeywordCatalog.PHONE_ALIASES)
                || containsAny(question, QuestionIntentKeywordCatalog.EMAIL_ALIASES)
                || containsAny(question, QuestionIntentKeywordCatalog.ADDRESS_ALIASES);
    }

    private boolean containsAny(String question, List<String> candidates) {
        return candidates.stream().anyMatch(question::contains);
    }
}
