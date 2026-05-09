package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.query.KnowledgeQueryPlanner;
import com.airag.modules.agent.service.KnowledgeAgentFacade;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.routing.QuestionIntentFeatureExtractor;
import com.airag.modules.chat.routing.QuestionIntentFeatures;
import com.airag.modules.chat.routing.QuestionIntentKeywordCatalog;
import com.airag.modules.chat.service.KnowledgeRetrieverService;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.service.KnowledgeDocumentService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import com.airag.modules.knowledge.vo.KnowledgeDocumentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeAgentFacadeImpl implements KnowledgeAgentFacade {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(400[- ]?\\d{3,4}[- ]?\\d{4}|1\\d{10}|(?:0\\d{2,3}[- ]?)?\\d{7,8})"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    );

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeRetrieverService knowledgeRetrieverService;
    private final KnowledgeQueryPlanner knowledgeQueryPlanner;
    private final QuestionIntentFeatureExtractor featureExtractor;

    @Override
    public String queryKnowledgeBases(LoginUser loginUser, String keyword, Integer limit) {
        List<KnowledgeBaseVO> bases = accessibleKnowledgeBases(loginUser);
        String normalizedKeyword = normalizeKeyword(keyword);
        int size = normalizeLimit(limit, 10, 20);
        List<KnowledgeBaseVO> matches = rankKnowledgeBases(bases, normalizedKeyword, size).stream()
                .map(ScoredKnowledgeBase::knowledgeBase)
                .toList();

        log.info("Agent tool=queryKnowledgeBases userId={}, keyword={}, normalizedKeyword={}, limit={}, accessibleBases={}, resultCount={}, matchedNames={}",
                loginUser.getUserId(), safe(keyword), normalizedKeyword, size, bases.size(), matches.size(),
                matches.stream().map(KnowledgeBaseVO::getKnowledgeBaseName).toList());

        StringBuilder builder = new StringBuilder();
        builder.append("RESULT_TYPE: KNOWLEDGE_BASES\n")
                .append("QUERY_KEYWORD: ").append(safe(keyword)).append("\n")
                .append("MATCHED_COUNT: ").append(matches.size()).append("\n");

        if (matches.isEmpty()) {
            builder.append("MESSAGE: ")
                    .append(StrUtil.isBlank(normalizedKeyword) ? "当前没有可查看的知识库。" : "没有找到匹配的知识库。")
                    .append("\n");
            return builder.toString();
        }

        int index = 1;
        for (KnowledgeBaseVO base : matches) {
            builder.append("\nITEM_").append(index).append(":\n")
                    .append("name: ").append(base.getKnowledgeBaseName()).append("\n")
                    .append("description: ").append(StrUtil.blankToDefault(base.getDescription(), "")).append("\n")
                    .append("document_count: ").append(base.getDocumentCount()).append("\n");
            index++;
        }
        return builder.toString();
    }

    @Override
    public String queryKnowledgeDocuments(LoginUser loginUser,
                                          String knowledgeBaseKeyword,
                                          String documentKeyword,
                                          Integer limit,
                                          boolean includeDocumentNames) {
        int size = normalizeLimit(limit, 10, 20);
        List<KnowledgeBaseVO> accessibleBases = accessibleKnowledgeBases(loginUser);
        String normalizedBaseKeyword = normalizeKeyword(knowledgeBaseKeyword);
        String normalizedDocumentKeyword = normalizeKeyword(documentKeyword);
        List<KnowledgeBaseVO> matchedBases = rankKnowledgeBases(accessibleBases, normalizedBaseKeyword, size).stream()
                .map(ScoredKnowledgeBase::knowledgeBase)
                .toList();

        log.info("Agent tool=queryKnowledgeDocuments userId={}, knowledgeBaseKeyword={}, documentKeyword={}, limit={}, matchedBases={}",
                loginUser.getUserId(), safe(knowledgeBaseKeyword), safe(documentKeyword), size,
                matchedBases.stream().map(KnowledgeBaseVO::getKnowledgeBaseName).toList());

        StringBuilder builder = new StringBuilder();
        builder.append("RESULT_TYPE: KNOWLEDGE_DOCUMENTS\n")
                .append("KNOWLEDGE_BASE_KEYWORD: ").append(safe(knowledgeBaseKeyword)).append("\n")
                .append("DOCUMENT_KEYWORD: ").append(safe(documentKeyword)).append("\n")
                .append("DOCUMENT_NAMES_INCLUDED: ").append(includeDocumentNames).append("\n")
                .append("MATCHED_BASE_COUNT: ").append(matchedBases.size()).append("\n");

        if (matchedBases.isEmpty()) {
            builder.append("MESSAGE: 没有找到匹配的知识库，已尝试从全局可访问文档中继续查找。\n");
            appendGlobalDocumentCandidates(builder, loginUser, knowledgeBaseKeyword, documentKeyword, size, accessibleBases);
            return builder.toString();
        }

        int emitted = 0;
        int baseIndex = 1;
        for (KnowledgeBaseVO base : matchedBases) {
            List<KnowledgeDocumentVO> documents = rankDocuments(listDocuments(loginUser, base.getId()), normalizedDocumentKeyword, size - emitted).stream()
                    .map(ScoredDocument::document)
                    .toList();

            log.info("Agent tool=queryKnowledgeDocuments base={}, matchedDocuments={}",
                    base.getKnowledgeBaseName(),
                    documents.stream().map(KnowledgeDocumentVO::getDocumentName).toList());

            builder.append("\nBASE_").append(baseIndex).append(":\n")
                    .append("knowledge_base_name: ").append(base.getKnowledgeBaseName()).append("\n")
                    .append("knowledge_base_document_count: ").append(base.getDocumentCount()).append("\n")
                    .append("matched_document_count: ").append(documents.size()).append("\n");

            if (includeDocumentNames) {
                int documentIndex = 1;
                for (KnowledgeDocumentVO document : documents) {
                    builder.append("document_").append(documentIndex).append("_name: ")
                            .append(StrUtil.blankToDefault(document.getDocumentName(), "")).append("\n")
                            .append("document_").append(documentIndex).append("_file_name: ")
                            .append(StrUtil.blankToDefault(document.getFileName(), "")).append("\n");
                    documentIndex++;
                }
            }

            emitted += documents.size();
            baseIndex++;
            if (emitted >= size) {
                break;
            }
        }

        if (emitted == 0 && StrUtil.isNotBlank(normalizedDocumentKeyword)) {
            builder.append("\nMESSAGE: 找到了相关知识库，但没有找到匹配文档，已补充全局可访问文档候选。\n");
            appendGlobalDocumentCandidates(builder, loginUser, knowledgeBaseKeyword, documentKeyword, size, accessibleBases);
        }

        return builder.toString();
    }

    @Override
    public String searchKnowledge(LoginUser loginUser, String question, String knowledgeBaseKeyword, Integer topK) {
        int size = normalizeLimit(topK, 3, 5);
        Long knowledgeBaseId = resolveKnowledgeBaseId(loginUser, knowledgeBaseKeyword);
        List<Long> accessibleKnowledgeBaseIds = accessibleKnowledgeBases(loginUser).stream()
                .map(KnowledgeBaseVO::getId)
                .toList();
        boolean fallbackToGlobal = StrUtil.isNotBlank(knowledgeBaseKeyword) && knowledgeBaseId == null;

        log.info("Agent tool=searchKnowledge userId={}, knowledgeBaseKeyword={}, resolvedKnowledgeBaseId={}, topK={}, question={}",
                loginUser.getUserId(), safe(knowledgeBaseKeyword), knowledgeBaseId, size, safe(question));

        List<KnowledgeRetrieverService.RetrievedChunk> chunks = retrieveKnowledgeWithFallbackQueries(
                question,
                size,
                knowledgeBaseId,
                accessibleKnowledgeBaseIds
        );

        StringBuilder builder = new StringBuilder();
        builder.append("RESULT_TYPE: KNOWLEDGE_SEARCH\n")
                .append("QUESTION: ").append(safe(question)).append("\n")
                .append("KNOWLEDGE_BASE_KEYWORD: ").append(safe(knowledgeBaseKeyword)).append("\n")
                .append("SEARCH_SCOPE: ")
                .append(fallbackToGlobal ? "GLOBAL_FALLBACK" : (knowledgeBaseId == null ? "GLOBAL" : "MATCHED_KNOWLEDGE_BASE"))
                .append("\n")
                .append("MATCHED_COUNT: ").append(chunks.size()).append("\n");

        if (chunks.isEmpty()) {
            builder.append("MESSAGE: 没有检索到相关知识片段。\n");
            return builder.toString();
        }

        appendChunks(builder, chunks);
        return builder.toString();
    }

    @Override
    public String searchDocumentContent(LoginUser loginUser,
                                        String question,
                                        String knowledgeBaseKeyword,
                                        String documentKeyword,
                                        Integer topK) {
        int size = normalizeDocumentSearchLimit(question, topK);
        List<KnowledgeBaseVO> accessibleBases = accessibleKnowledgeBases(loginUser);
        List<KnowledgeBaseVO> matchedBases = rankKnowledgeBases(accessibleBases, normalizeKeyword(knowledgeBaseKeyword), 3).stream()
                .map(ScoredKnowledgeBase::knowledgeBase)
                .toList();
        List<DocumentTarget> targets = resolveDocumentTargets(loginUser, matchedBases, documentKeyword);

        StringBuilder builder = new StringBuilder();
        builder.append("RESULT_TYPE: DOCUMENT_CONTENT_SEARCH\n")
                .append("QUESTION: ").append(safe(question)).append("\n")
                .append("KNOWLEDGE_BASE_KEYWORD: ").append(safe(knowledgeBaseKeyword)).append("\n")
                .append("DOCUMENT_KEYWORD: ").append(safe(documentKeyword)).append("\n")
                .append("MATCHED_BASE_COUNT: ").append(matchedBases.size()).append("\n")
                .append("MATCHED_DOCUMENT_COUNT: ").append(targets.size()).append("\n");

        if (targets.isEmpty()) {
            builder.append("MESSAGE: 没有找到可用于查看正文的匹配文档。\n");
            appendGlobalDocumentCandidates(builder, loginUser, knowledgeBaseKeyword, documentKeyword, size, accessibleBases);
            return builder.toString();
        }

        int itemIndex = 1;
        boolean hasAnySnippet = false;
        for (DocumentTarget target : targets) {
            List<KnowledgeRetrieverService.RetrievedChunk> chunks = retrieveDocumentChunks(question, target, size);
            if (chunks.isEmpty()) {
                continue;
            }
            hasAnySnippet = true;
            builder.append("\nDOCUMENT_").append(itemIndex).append(":\n")
                    .append("knowledge_base_name: ").append(target.knowledgeBase().getKnowledgeBaseName()).append("\n")
                    .append("document_name: ").append(StrUtil.blankToDefault(target.document().getDocumentName(), "")).append("\n")
                    .append("file_name: ").append(StrUtil.blankToDefault(target.document().getFileName(), "")).append("\n")
                    .append("matched_snippet_count: ").append(chunks.size()).append("\n");
            appendChunks(builder, chunks);
            itemIndex++;
        }

        if (!hasAnySnippet) {
            builder.append("MESSAGE: 已定位到文档，但暂未检索到可用于回答的正文片段。\n");
        }
        return builder.toString();
    }

    @Override
    public String searchStructuredFact(LoginUser loginUser,
                                       String question,
                                       String knowledgeBaseKeyword,
                                       Integer topK) {
        QuestionIntentFeatures features = featureExtractor.extract(question);
        List<FactType> factTypes = resolveFactTypes(features);
        if (factTypes.isEmpty()) {
            return buildStructuredFactResult(question, knowledgeBaseKeyword, List.of(), false);
        }

        boolean aggregateStructuredFacts = shouldAggregateStructuredFacts(question);
        int size = aggregateStructuredFacts ? normalizeLimit(topK, 8, 12) : normalizeLimit(topK, 3, 5);
        List<KnowledgeBaseVO> accessibleBases = accessibleKnowledgeBases(loginUser);
        List<KnowledgeBaseVO> matchedBases = rankKnowledgeBases(accessibleBases, normalizeKeyword(knowledgeBaseKeyword), 3).stream()
                .map(ScoredKnowledgeBase::knowledgeBase)
                .toList();
        List<KnowledgeBaseVO> basesToSearch = matchedBases.isEmpty() ? accessibleBases : matchedBases;
        List<FactHit> hits = new ArrayList<>();
        int targetCount;
        if (aggregateStructuredFacts) {
            hits.addAll(retrieveStructuredFactHitsGlobally(question, factTypes, basesToSearch, size));
            targetCount = basesToSearch.size();
        } else {
            List<DocumentTarget> targets = resolveStructuredFactTargets(loginUser, basesToSearch, false);
            for (DocumentTarget target : targets) {
                hits.addAll(retrieveStructuredFactHits(question, target, factTypes, size));
            }
            targetCount = targets.size();
        }

        List<FactHit> rankedHits = deduplicateAndSortFactHits(hits, question, size);
        log.info("Agent tool=searchStructuredFact userId={}, knowledgeBaseKeyword={}, factTypes={}, targetCount={}, hitCount={}, question={}",
                loginUser.getUserId(),
                safe(knowledgeBaseKeyword),
                factTypes.stream().map(FactType::name).toList(),
                targetCount,
                rankedHits.size(),
                safe(question));

        return buildStructuredFactResult(question, knowledgeBaseKeyword, rankedHits, aggregateStructuredFacts);
    }

    private List<KnowledgeRetrieverService.RetrievedChunk> retrieveKnowledgeWithFallbackQueries(String question,
                                                                                                int topK,
                                                                                                Long knowledgeBaseId,
                                                                                                List<Long> accessibleKnowledgeBaseIds) {
        List<String> queries = knowledgeQueryPlanner.buildKnowledgeSearchQueries(question);
        List<KnowledgeRetrieverService.RetrievedChunk> merged = new ArrayList<>();
        int retrievalLimit = Math.max(topK * 3, 8);

        for (String query : queries) {
            if (knowledgeBaseId != null) {
                merged.addAll(knowledgeRetrieverService.retrieve(query, retrievalLimit, knowledgeBaseId));
            } else {
                merged.addAll(knowledgeRetrieverService.retrieve(query, retrievalLimit, accessibleKnowledgeBaseIds));
            }
        }
        return rerankAndDeduplicateChunks(merged, question, topK, null);
    }

    private List<KnowledgeRetrieverService.RetrievedChunk> rerankAndDeduplicateChunks(List<KnowledgeRetrieverService.RetrievedChunk> chunks,
                                                                                      String question,
                                                                                      int limit,
                                                                                      String preferredFileName) {
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> ranked = new ArrayList<>();
        for (KnowledgeRetrieverService.RetrievedChunk chunk : chunks) {
            if (isDuplicateChunk(ranked, chunk)) {
                continue;
            }
            ranked.add(new ScoredChunk(chunk, chunkRelevanceScore(chunk, question, preferredFileName)));
        }

        ranked.sort(Comparator
                .comparingInt(ScoredChunk::score).reversed()
                .thenComparing(candidate -> safe(candidate.chunk().fileName()))
                .thenComparing(candidate -> candidate.chunk().chunkIndex(), Comparator.nullsLast(Comparator.naturalOrder())));

        return ranked.stream()
                .map(ScoredChunk::chunk)
                .limit(limit)
                .toList();
    }

    private List<DocumentTarget> resolveDocumentTargets(LoginUser loginUser,
                                                        List<KnowledgeBaseVO> matchedBases,
                                                        String documentKeyword) {
        return resolveDocumentTargets(loginUser, matchedBases, documentKeyword, 2, 2);
    }

    private List<DocumentTarget> resolveDocumentTargets(LoginUser loginUser,
                                                        List<KnowledgeBaseVO> matchedBases,
                                                        String documentKeyword,
                                                        int perBaseLimit,
                                                        int targetLimit) {
        String normalizedDocumentKeyword = normalizeKeyword(documentKeyword);
        List<DocumentTarget> targets = new ArrayList<>();

        if (!matchedBases.isEmpty()) {
            for (KnowledgeBaseVO base : matchedBases) {
                List<KnowledgeDocumentVO> documents = listDocuments(loginUser, base.getId());
                List<KnowledgeDocumentVO> matchedDocuments = rankDocuments(documents, normalizedDocumentKeyword, perBaseLimit).stream()
                        .map(ScoredDocument::document)
                        .toList();
                if (matchedDocuments.isEmpty()
                        && StrUtil.isBlank(documentKeyword)
                        && matchedBases.size() == 1
                        && documents.size() == 1) {
                    matchedDocuments = List.of(documents.get(0));
                }
                for (KnowledgeDocumentVO document : matchedDocuments) {
                    targets.add(new DocumentTarget(base, document));
                    if (targets.size() >= targetLimit) {
                        return targets;
                    }
                }
            }
            return targets;
        }

        if (StrUtil.isBlank(documentKeyword)) {
            return targets;
        }

        for (KnowledgeBaseVO base : accessibleKnowledgeBases(loginUser)) {
            List<KnowledgeDocumentVO> matchedDocuments = rankDocuments(listDocuments(loginUser, base.getId()), normalizedDocumentKeyword, perBaseLimit).stream()
                    .map(ScoredDocument::document)
                    .toList();
            for (KnowledgeDocumentVO document : matchedDocuments) {
                targets.add(new DocumentTarget(base, document));
                if (targets.size() >= targetLimit) {
                    return targets;
                }
            }
        }
        return targets;
    }

    private List<KnowledgeRetrieverService.RetrievedChunk> retrieveDocumentChunks(String question,
                                                                                  DocumentTarget target,
                                                                                  int topK) {
        String documentName = StrUtil.blankToDefault(target.document().getDocumentName(), target.document().getFileName());
        int retrievalLimit = knowledgeQueryPlanner.isEnumeratingDocumentQuestion(question)
                ? Math.max(topK * 4, 12)
                : Math.max(topK * 3, topK);
        List<KnowledgeRetrieverService.RetrievedChunk> chunks = new ArrayList<>();
        for (String query : knowledgeQueryPlanner.buildDocumentSearchQueries(question, documentName)) {
            chunks.addAll(knowledgeRetrieverService.retrieve(query, retrievalLimit, target.knowledgeBase().getId()));
        }

        List<KnowledgeRetrieverService.RetrievedChunk> reranked = rerankAndDeduplicateChunks(
                chunks,
                question,
                retrievalLimit,
                StrUtil.blankToDefault(target.document().getFileName(), target.document().getDocumentName())
        );

        List<KnowledgeRetrieverService.RetrievedChunk> byDocumentId = reranked.stream()
                .filter(chunk -> target.document().getId() != null && target.document().getId().equals(chunk.documentId()))
                .limit(topK)
                .toList();
        if (!byDocumentId.isEmpty()) {
            return byDocumentId;
        }
        return reranked.stream()
                .filter(chunk -> fileMatchesDocument(chunk.fileName(), target.document()))
                .limit(topK)
                .toList();
    }

    private List<FactHit> retrieveStructuredFactHits(String question,
                                                     DocumentTarget target,
                                                     List<FactType> factTypes,
                                                     int topK) {
        List<FactHit> hits = new ArrayList<>();
        String documentName = StrUtil.blankToDefault(target.document().getDocumentName(), target.document().getFileName());
        int retrievalLimit = Math.max(topK * 4, 12);

        for (FactType factType : factTypes) {
            List<KnowledgeRetrieverService.RetrievedChunk> chunks = new ArrayList<>();
            for (String query : buildStructuredFactQueries(question, factType, documentName)) {
                chunks.addAll(knowledgeRetrieverService.retrieve(query, retrievalLimit, target.knowledgeBase().getId()));
            }

            List<KnowledgeRetrieverService.RetrievedChunk> reranked = rerankAndDeduplicateChunks(
                    chunks,
                    question + " " + factType.name(),
                    retrievalLimit,
                    StrUtil.blankToDefault(target.document().getFileName(), target.document().getDocumentName())
            );

            for (KnowledgeRetrieverService.RetrievedChunk chunk : reranked) {
                if (!matchesTargetDocument(chunk, target)) {
                    continue;
                }
                String extractedValue = extractStructuredFactValue(chunk.content(), factType);
                if (StrUtil.isBlank(extractedValue)) {
                    continue;
                }
                hits.add(new FactHit(target, factType, extractedValue, chunk, structuredFactScore(chunk, question, extractedValue)));
            }
        }
        return hits;
    }

    private List<FactHit> retrieveStructuredFactHitsGlobally(String question,
                                                             List<FactType> factTypes,
                                                             List<KnowledgeBaseVO> bases,
                                                             int limit) {
        if (bases.isEmpty()) {
            return List.of();
        }

        Map<Long, KnowledgeBaseVO> knowledgeBaseById = new LinkedHashMap<>();
        List<Long> knowledgeBaseIds = new ArrayList<>();
        for (KnowledgeBaseVO base : bases) {
            if (base.getId() == null) {
                continue;
            }
            knowledgeBaseById.put(base.getId(), base);
            knowledgeBaseIds.add(base.getId());
        }
        if (knowledgeBaseIds.isEmpty()) {
            return List.of();
        }

        int retrievalLimit = Math.max(limit * 8, 24);
        List<FactHit> hits = new ArrayList<>();
        for (FactType factType : factTypes) {
            List<KnowledgeRetrieverService.RetrievedChunk> chunks = new ArrayList<>();
            for (String query : globalStructuredFactQueries(question, factType)) {
                chunks.addAll(knowledgeRetrieverService.retrieve(query, retrievalLimit, knowledgeBaseIds));
            }

            List<KnowledgeRetrieverService.RetrievedChunk> reranked = rerankAndDeduplicateChunks(
                    chunks,
                    question + " " + factType.name(),
                    retrievalLimit,
                    null
            );

            for (KnowledgeRetrieverService.RetrievedChunk chunk : reranked) {
                String extractedValue = extractStructuredFactValue(chunk.content(), factType);
                if (StrUtil.isBlank(extractedValue)) {
                    continue;
                }
                KnowledgeBaseVO knowledgeBase = chunk.knowledgeBaseId() == null ? null : knowledgeBaseById.get(chunk.knowledgeBaseId());
                if (knowledgeBase == null) {
                    continue;
                }
                KnowledgeDocumentVO document = KnowledgeDocumentVO.builder()
                        .id(chunk.documentId())
                        .knowledgeBaseId(knowledgeBase.getId())
                        .documentName(chunk.fileName())
                        .fileName(chunk.fileName())
                        .build();
                DocumentTarget target = new DocumentTarget(knowledgeBase, document);
                hits.add(new FactHit(target, factType, extractedValue, chunk, structuredFactScore(chunk, question, extractedValue)));
            }
        }
        return hits;
    }

    private List<String> buildStructuredFactQueries(String question, FactType factType, String documentName) {
        List<String> queries = new ArrayList<>();
        queries.add(StrUtil.format("围绕文档《{}》回答：{}", documentName, question));

        List<String> aliases = switch (factType) {
            case PHONE -> QuestionIntentKeywordCatalog.PHONE_FACT_QUERIES;
            case EMAIL -> QuestionIntentKeywordCatalog.EMAIL_FACT_QUERIES;
            case ADDRESS -> QuestionIntentKeywordCatalog.ADDRESS_FACT_QUERIES;
        };

        for (String alias : aliases) {
            queries.add(StrUtil.format("文档《{}》中的{}", documentName, alias));
        }
        return queries.stream().distinct().toList();
    }

    private List<String> globalStructuredFactQueries(String question, FactType factType) {
        List<String> queries = new ArrayList<>();
        queries.add(question);
        switch (factType) {
            case PHONE -> queries.addAll(QuestionIntentKeywordCatalog.PHONE_FACT_QUERIES);
            case EMAIL -> queries.addAll(QuestionIntentKeywordCatalog.EMAIL_FACT_QUERIES);
            case ADDRESS -> queries.addAll(QuestionIntentKeywordCatalog.ADDRESS_FACT_QUERIES);
        }
        queries.addAll(QuestionIntentKeywordCatalog.STRUCTURED_FACT_DOCUMENT_ALIASES);
        return queries.stream().distinct().toList();
    }

    private String buildStructuredFactResult(String question,
                                             String knowledgeBaseKeyword,
                                             List<FactHit> hits,
                                             boolean aggregateStructuredFacts) {
        StringBuilder builder = new StringBuilder();
        builder.append("RESULT_TYPE: STRUCTURED_FACT_SEARCH\n")
                .append("QUESTION: ").append(safe(question)).append("\n")
                .append("KNOWLEDGE_BASE_KEYWORD: ").append(safe(knowledgeBaseKeyword)).append("\n")
                .append("AGGREGATED: ").append(aggregateStructuredFacts).append("\n")
                .append("MATCHED_COUNT: ").append(hits.size()).append("\n");

        if (hits.isEmpty()) {
            builder.append("MESSAGE: 当前未从相关文档中抽取到可确认的结构化字段。\n");
            return builder.toString();
        }

        int index = 1;
        for (FactHit hit : hits) {
            builder.append("\nITEM_").append(index).append(":\n")
                    .append("knowledge_base_name: ").append(hit.target().knowledgeBase().getKnowledgeBaseName()).append("\n")
                    .append("document_name: ").append(StrUtil.blankToDefault(hit.target().document().getDocumentName(), "")).append("\n")
                    .append("file_name: ").append(StrUtil.blankToDefault(hit.target().document().getFileName(), "")).append("\n")
                    .append("fact_type: ").append(hit.factType().name()).append("\n")
                    .append("fact_label: ").append(inferFactLabel(hit)).append("\n")
                    .append("extracted_value: ").append(hit.extractedValue()).append("\n")
                    .append("snippet: ").append(StrUtil.blankToDefault(hit.chunk().content(), "")).append("\n");
            index++;
        }
        return builder.toString();
    }

    private List<DocumentTarget> resolveStructuredFactTargets(LoginUser loginUser,
                                                              List<KnowledgeBaseVO> bases,
                                                              boolean aggregateStructuredFacts) {
        LinkedHashMap<String, DocumentTarget> targets = new LinkedHashMap<>();
        int perBaseLimit = aggregateStructuredFacts ? 4 : 2;
        int targetLimit = aggregateStructuredFacts ? 12 : 6;
        for (String alias : QuestionIntentKeywordCatalog.STRUCTURED_FACT_DOCUMENT_ALIASES) {
            for (DocumentTarget target : resolveDocumentTargets(loginUser, bases, alias, perBaseLimit, targetLimit)) {
                targets.putIfAbsent(structuredFactTargetKey(target), target);
                if (targets.size() >= targetLimit) {
                    return new ArrayList<>(targets.values());
                }
            }
        }
        return new ArrayList<>(targets.values());
    }

    private String structuredFactTargetKey(DocumentTarget target) {
        return safe(String.valueOf(target.knowledgeBase().getId()))
                + "#"
                + safe(String.valueOf(target.document().getId()))
                + "#"
                + safe(target.document().getFileName());
    }

    private List<FactHit> deduplicateAndSortFactHits(List<FactHit> hits, String question, int limit) {
        Map<String, FactHit> uniqueHits = new LinkedHashMap<>();
        for (FactHit hit : hits) {
            String key = hit.factType().name()
                    + "#"
                    + normalizeKeyword(hit.extractedValue())
                    + "#"
                    + normalizeKeyword(inferFactLabel(hit));
            uniqueHits.merge(key, hit, (left, right) -> left.score() >= right.score() ? left : right);
        }

        return uniqueHits.values().stream()
                .sorted(Comparator
                        .comparingInt(FactHit::score).reversed()
                        .thenComparing(hit -> safe(hit.target().document().getDocumentName())))
                .limit(limit)
                .toList();
    }

    private boolean shouldAggregateStructuredFacts(String question) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "");
        return (normalizedQuestion.contains("公司") || normalizedQuestion.contains("企业"))
                && (normalizedQuestion.contains("邮箱")
                || normalizedQuestion.contains("电话")
                || normalizedQuestion.contains("热线")
                || normalizedQuestion.contains("联系方式")
                || normalizedQuestion.contains("地址"));
    }

    private String inferFactLabel(FactHit hit) {
        String haystack = StrUtil.blankToDefault(hit.chunk().content(), "")
                + "\n"
                + StrUtil.blankToDefault(hit.target().document().getDocumentName(), "")
                + "\n"
                + StrUtil.blankToDefault(hit.target().document().getFileName(), "");
        if (haystack.contains("售后")) {
            return "售后";
        }
        if (haystack.contains("商务")) {
            return "商务";
        }
        if (haystack.contains("客服")) {
            return "客服";
        }
        if (haystack.contains("合作")) {
            return "合作";
        }
        if (haystack.contains("招聘")) {
            return "招聘";
        }
        if (haystack.contains("财务")) {
            return "财务";
        }
        if (haystack.contains("公司")) {
            return "公司";
        }
        return "";
    }

    private List<FactType> resolveFactTypes(QuestionIntentFeatures features) {
        List<FactType> factTypes = new ArrayList<>();
        if (features.phoneQuery()) {
            factTypes.add(FactType.PHONE);
        }
        if (features.emailQuery()) {
            factTypes.add(FactType.EMAIL);
        }
        if (features.addressQuery()) {
            factTypes.add(FactType.ADDRESS);
        }
        return factTypes;
    }

    private boolean matchesTargetDocument(KnowledgeRetrieverService.RetrievedChunk chunk, DocumentTarget target) {
        if (target.document().getId() != null && target.document().getId().equals(chunk.documentId())) {
            return true;
        }
        return fileMatchesDocument(chunk.fileName(), target.document());
    }

    private String extractStructuredFactValue(String content, FactType factType) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        return switch (factType) {
            case PHONE -> firstPatternMatch(content, PHONE_PATTERN);
            case EMAIL -> firstPatternMatch(content, EMAIL_PATTERN);
            case ADDRESS -> extractAddressLine(content);
        };
    }

    private String firstPatternMatch(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
    }

    private String extractAddressLine(String content) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.contains("地址") || trimmed.contains("位于") || trimmed.contains("位置")) {
                return trimmed;
            }
        }
        return null;
    }

    private int structuredFactScore(KnowledgeRetrieverService.RetrievedChunk chunk, String question, String extractedValue) {
        return chunkRelevanceScore(chunk, question, null)
                + overlapScore(normalizeKeyword(chunk.content()), normalizeKeyword(extractedValue)) * 4
                + 200;
    }

    private boolean isDuplicateChunk(List<ScoredChunk> ranked, KnowledgeRetrieverService.RetrievedChunk candidate) {
        String candidateKey = deduplicationKey(candidate);
        return ranked.stream().anyMatch(existing -> StrUtil.equals(deduplicationKey(existing.chunk()), candidateKey));
    }

    private String deduplicationKey(KnowledgeRetrieverService.RetrievedChunk chunk) {
        if (chunk == null) {
            return "";
        }
        String normalizedContent = normalizeSnippet(chunk.content());
        if (StrUtil.isNotBlank(normalizedContent)) {
            return normalizedContent;
        }
        return safe(chunk.fileName()) + "#" + safe(String.valueOf(chunk.chunkIndex()));
    }

    private String normalizeSnippet(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("\\s+", "")
                .replace("，", "")
                .replace("。", "")
                .replace(",", "")
                .replace(".", "")
                .replace(":", "")
                .replace("：", "")
                .trim();
    }

    private int chunkRelevanceScore(KnowledgeRetrieverService.RetrievedChunk chunk,
                                    String question,
                                    String preferredFileName) {
        int score = (int) Math.round((chunk.score() == null ? 0D : chunk.score()) * 1000);
        String normalizedQuestion = normalizeKeyword(question);
        String normalizedContent = normalizeKeyword(chunk.content());
        String normalizedFileName = normalizeKeyword(chunk.fileName());
        String normalizedPreferredFile = normalizeKeyword(preferredFileName);

        if (StrUtil.isNotBlank(normalizedQuestion)) {
            score += overlapScore(normalizedContent, normalizedQuestion) * 3;
            score += overlapScore(normalizedFileName, normalizedQuestion) * 2;
        }
        if (StrUtil.isNotBlank(normalizedPreferredFile) && normalizedFileName.contains(normalizedPreferredFile)) {
            score += 120;
        }
        return score;
    }

    private int normalizeDocumentSearchLimit(String question, Integer topK) {
        return knowledgeQueryPlanner.isEnumeratingDocumentQuestion(question)
                ? normalizeLimit(topK, 8, 12)
                : normalizeLimit(topK, 3, 5);
    }

    private boolean fileMatchesDocument(String fileName, KnowledgeDocumentVO document) {
        String normalizedFileName = normalizeDocumentKeyword(fileName);
        String normalizedDocumentName = normalizeDocumentKeyword(document.getDocumentName());
        String normalizedStoredFileName = normalizeDocumentKeyword(document.getFileName());
        return StrUtil.isNotBlank(normalizedFileName)
                && (normalizedFileName.contains(normalizedDocumentName)
                || normalizedDocumentName.contains(normalizedFileName)
                || normalizedFileName.contains(normalizedStoredFileName)
                || normalizedStoredFileName.contains(normalizedFileName));
    }

    private List<ScoredKnowledgeBase> rankKnowledgeBases(List<KnowledgeBaseVO> bases, String normalizedKeyword, int limit) {
        return bases.stream()
                .map(base -> new ScoredKnowledgeBase(base, knowledgeBaseScore(base, normalizedKeyword)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator
                        .comparingInt(ScoredKnowledgeBase::score).reversed()
                        .thenComparing(candidate -> safe(candidate.knowledgeBase().getKnowledgeBaseName()))
                        .thenComparing(candidate -> candidate.knowledgeBase().getDocumentCount(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<ScoredDocument> rankDocuments(List<KnowledgeDocumentVO> documents, String normalizedKeyword, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return documents.stream()
                .map(document -> new ScoredDocument(document, documentScore(document, normalizedKeyword)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator
                        .comparingInt(ScoredDocument::score).reversed()
                        .thenComparing(candidate -> safe(candidate.document().getDocumentName()))
                        .thenComparing(candidate -> safe(candidate.document().getFileName())))
                .limit(limit)
                .toList();
    }

    private int knowledgeBaseScore(KnowledgeBaseVO base, String normalizedKeyword) {
        if (StrUtil.isBlank(normalizedKeyword)) {
            return 1;
        }
        String semanticKeyword = normalizeKnowledgeBaseKeyword(normalizedKeyword);
        int nameScore = textMatchScore(normalizeKnowledgeBaseKeyword(base.getKnowledgeBaseName()), semanticKeyword);
        int descriptionScore = textMatchScore(normalizeKnowledgeBaseKeyword(base.getDescription()), semanticKeyword) / 2;
        return Math.max(nameScore, descriptionScore);
    }

    private int documentScore(KnowledgeDocumentVO document, String normalizedKeyword) {
        if (StrUtil.isBlank(normalizedKeyword)) {
            return 1;
        }
        String semanticKeyword = normalizeDocumentKeyword(normalizedKeyword);
        int documentNameScore = textMatchScore(normalizeDocumentKeyword(document.getDocumentName()), semanticKeyword);
        int fileNameScore = textMatchScore(normalizeDocumentKeyword(document.getFileName()), semanticKeyword);
        return Math.max(documentNameScore, fileNameScore);
    }

    private int textMatchScore(String candidate, String normalizedKeyword) {
        if (StrUtil.isBlank(normalizedKeyword)) {
            return 1;
        }
        if (StrUtil.isBlank(candidate)) {
            return 0;
        }
        if (candidate.equals(normalizedKeyword)) {
            return 100;
        }

        int score = 0;
        if (candidate.startsWith(normalizedKeyword) || normalizedKeyword.startsWith(candidate)) {
            score = Math.max(score, 90);
        }
        if (candidate.contains(normalizedKeyword) || normalizedKeyword.contains(candidate)) {
            score = Math.max(score, 80);
        }
        int overlapScore = overlapScore(candidate, normalizedKeyword);
        if (overlapScore >= 30) {
            score = Math.max(score, overlapScore);
        }
        return score;
    }

    private int overlapScore(String candidate, String keyword) {
        if (StrUtil.isBlank(candidate) || StrUtil.isBlank(keyword)) {
            return 0;
        }
        List<Character> remaining = new ArrayList<>();
        for (char ch : candidate.toCharArray()) {
            remaining.add(ch);
        }

        int common = 0;
        for (char ch : keyword.toCharArray()) {
            int index = remaining.indexOf(ch);
            if (index >= 0) {
                common++;
                remaining.remove(index);
            }
        }
        return common == 0 ? 0 : common * 100 / Math.max(candidate.length(), keyword.length());
    }

    private String normalizeKeyword(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s._\\-()（）]+", "")
                .trim();
    }

    private String normalizeKnowledgeBaseKeyword(String value) {
        return normalizeKeyword(value)
                .replace("知识库", "")
                .replace("文档库", "")
                .replace("资料库", "")
                .replace("库", "")
                .replace("pdf", "");
    }

    private String normalizeDocumentKeyword(String value) {
        return normalizeKeyword(value)
                .replace("pdf", "")
                .replace("docx", "")
                .replace("doc", "")
                .replace("txt", "");
    }

    private int normalizeLimit(Integer limit, int defaultValue, int maxValue) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, maxValue);
    }

    private String safe(String value) {
        return StrUtil.blankToDefault(value, "");
    }

    private void appendGlobalDocumentCandidates(StringBuilder builder,
                                                LoginUser loginUser,
                                                String knowledgeBaseKeyword,
                                                String documentKeyword,
                                                int size,
                                                List<KnowledgeBaseVO> candidateBases) {
        String fallbackKeyword = StrUtil.isNotBlank(documentKeyword) ? documentKeyword : knowledgeBaseKeyword;
        String normalizedFallbackKeyword = normalizeKeyword(fallbackKeyword);
        List<FallbackDocumentCandidate> documents = new ArrayList<>();

        for (KnowledgeBaseVO base : candidateBases) {
            List<ScoredDocument> rankedDocuments = rankDocuments(listDocuments(loginUser, base.getId()), normalizedFallbackKeyword, size);
            for (ScoredDocument scoredDocument : rankedDocuments) {
                KnowledgeDocumentVO document = scoredDocument.document();
                documents.add(new FallbackDocumentCandidate(
                        base.getKnowledgeBaseName(),
                        StrUtil.blankToDefault(document.getDocumentName(), ""),
                        StrUtil.blankToDefault(document.getFileName(), "")
                ));
                if (documents.size() >= size) {
                    break;
                }
            }
            if (documents.size() >= size) {
                break;
            }
        }

        builder.append("GLOBAL_FALLBACK_DOCUMENT_COUNT: ").append(documents.size()).append("\n");
        int index = 1;
        for (FallbackDocumentCandidate document : documents) {
            builder.append("fallback_document_").append(index).append("_knowledge_base: ").append(document.knowledgeBaseName()).append("\n")
                    .append("fallback_document_").append(index).append("_name: ").append(document.documentName()).append("\n")
                    .append("fallback_document_").append(index).append("_file_name: ").append(document.fileName()).append("\n");
            index++;
        }
    }

    private void appendChunks(StringBuilder builder, List<KnowledgeRetrieverService.RetrievedChunk> chunks) {
        int index = 1;
        for (KnowledgeRetrieverService.RetrievedChunk chunk : chunks) {
            builder.append("\nITEM_").append(index).append(":\n")
                    .append("source_file: ").append(StrUtil.blankToDefault(chunk.fileName(), "")).append("\n")
                    .append("snippet: ").append(StrUtil.blankToDefault(chunk.content(), "")).append("\n");
            index++;
        }
    }

    private List<KnowledgeBaseVO> accessibleKnowledgeBases(LoginUser loginUser) {
        return knowledgeBaseService.list(loginUser.getUserId(), loginUser.getRoles());
    }

    private Long resolveKnowledgeBaseId(LoginUser loginUser, String knowledgeBaseKeyword) {
        if (StrUtil.isBlank(knowledgeBaseKeyword)) {
            return null;
        }
        List<ScoredKnowledgeBase> matches = rankKnowledgeBases(accessibleKnowledgeBases(loginUser), normalizeKeyword(knowledgeBaseKeyword), 1);
        return matches.isEmpty() ? null : matches.get(0).knowledgeBase().getId();
    }

    private List<KnowledgeDocumentVO> listDocuments(LoginUser loginUser, Long knowledgeBaseId) {
        return knowledgeDocumentService.listDocuments(loginUser.getUserId(), loginUser.getRoles(), knowledgeBaseId);
    }

    private record ScoredKnowledgeBase(KnowledgeBaseVO knowledgeBase, int score) {
    }

    private record ScoredDocument(KnowledgeDocumentVO document, int score) {
    }

    private record ScoredChunk(KnowledgeRetrieverService.RetrievedChunk chunk, int score) {
    }

    private record FallbackDocumentCandidate(String knowledgeBaseName, String documentName, String fileName) {
    }

    private record DocumentTarget(KnowledgeBaseVO knowledgeBase, KnowledgeDocumentVO document) {
    }

    private enum FactType {
        PHONE,
        EMAIL,
        ADDRESS
    }

    private record FactHit(DocumentTarget target,
                           FactType factType,
                           String extractedValue,
                           KnowledgeRetrieverService.RetrievedChunk chunk,
                           int score) {
    }
}
