package com.airag.modules.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.airag.common.exception.BusinessException;
import com.airag.modules.knowledge.entity.KnowledgeDocument;
import com.airag.modules.knowledge.enums.DocumentStatusEnum;
import com.airag.modules.knowledge.mapper.KnowledgeDocumentMapper;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.service.KnowledgeDocumentProcessingService;
import com.airag.modules.knowledge.store.PgVectorKnowledgeEmbeddingStore;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentProcessingServiceImpl implements KnowledgeDocumentProcessingService {

    private static final int SAFE_REMARK_LENGTH = 240;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentParser apacheTikaDocumentParser;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> knowledgeEmbeddingStore;
    private final PgVectorKnowledgeEmbeddingStore pgVectorKnowledgeEmbeddingStore;

    @Value("${langchain4j.community.dashscope.embedding-model.model-name:text-embedding-v4}")
    private String embeddingModelName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processUploadedDocument(Long documentId) {
        KnowledgeDocument document = assertDocumentExists(documentId);
        ingestDocument(document, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildIndex(Long documentId) {
        KnowledgeDocument document = assertDocumentExists(documentId);
        ingestDocument(document, true);
    }

    private void ingestDocument(KnowledgeDocument document, boolean rebuildOnly) {
        List<String> embeddingIds = new ArrayList<>();
        try {
            pgVectorKnowledgeEmbeddingStore.removeByDocumentId(document.getId());

            Document parsedDocument = FileSystemDocumentLoader.loadDocument(
                    Path.of(document.getStoragePath()),
                    apacheTikaDocumentParser
            );
            List<TextSegment> segments = buildSegments(
                    parsedDocument,
                    document.getKnowledgeBaseId(),
                    document.getId(),
                    document.getFileName()
            );
            if (segments.isEmpty()) {
                throw new BusinessException("文档解析后内容为空");
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingIds = buildEmbeddingIds(segments.size());
            knowledgeEmbeddingStore.addAll(embeddingIds, embeddings, segments);

            knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getId, document.getId())
                    .set(KnowledgeDocument::getParseStatus, DocumentStatusEnum.SUCCESS.getCode())
                    .set(KnowledgeDocument::getChunkCount, segments.size())
                    .set(KnowledgeDocument::getFileHash, calculateFileHash(document.getStoragePath()))
                    .set(KnowledgeDocument::getEmbeddingModel, embeddingModelName)
                    .set(KnowledgeDocument::getRemark, rebuildOnly ? "文档重新建立索引成功" : "文档解析并入库成功")
                    .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now()));

            knowledgeBaseService.refreshDocumentCount(document.getKnowledgeBaseId());
            log.info("Knowledge document processed successfully documentId={}, chunkCount={}, rebuildOnly={}",
                    document.getId(), segments.size(), rebuildOnly);
        } catch (Exception exception) {
            if (!embeddingIds.isEmpty()) {
                knowledgeEmbeddingStore.removeAll(embeddingIds);
            } else {
                pgVectorKnowledgeEmbeddingStore.removeByDocumentId(document.getId());
            }
            knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getId, document.getId())
                    .set(KnowledgeDocument::getParseStatus, DocumentStatusEnum.FAILED.getCode())
                    .set(KnowledgeDocument::getChunkCount, 0)
                    .set(KnowledgeDocument::getRemark, buildSafeRemark(exception))
                    .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now()));
            log.error("Knowledge document processing failed documentId={}", document.getId(), exception);
            throw new BusinessException("知识文档处理失败: " + exception.getMessage());
        }
    }

    private List<TextSegment> buildSegments(Document parsedDocument, Long knowledgeBaseId, Long documentId, String originalFilename) {
        parsedDocument.metadata()
                .put(PgVectorKnowledgeEmbeddingStore.METADATA_KNOWLEDGE_BASE_ID, knowledgeBaseId)
                .put(PgVectorKnowledgeEmbeddingStore.METADATA_DOCUMENT_ID, documentId)
                .put(PgVectorKnowledgeEmbeddingStore.METADATA_FILE_NAME, originalFilename);

        List<TextSegment> rawSegments = documentSplitter.split(parsedDocument);
        List<TextSegment> segments = new ArrayList<>(rawSegments.size());
        for (int index = 0; index < rawSegments.size(); index++) {
            TextSegment rawSegment = rawSegments.get(index);
            Metadata metadata = new Metadata();
            metadata.put(PgVectorKnowledgeEmbeddingStore.METADATA_KNOWLEDGE_BASE_ID, knowledgeBaseId);
            metadata.put(PgVectorKnowledgeEmbeddingStore.METADATA_DOCUMENT_ID, documentId);
            metadata.put(PgVectorKnowledgeEmbeddingStore.METADATA_CHUNK_INDEX, index);
            metadata.put(PgVectorKnowledgeEmbeddingStore.METADATA_FILE_NAME, originalFilename);
            segments.add(TextSegment.from(rawSegment.text(), metadata));
        }
        return segments;
    }

    private KnowledgeDocument assertDocumentExists(Long documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);
        if (document == null || (document.getDeleted() != null && document.getDeleted() == 1)) {
            throw new BusinessException("知识文档不存在");
        }
        return document;
    }

    private List<String> buildEmbeddingIds(int size) {
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        return ids;
    }

    private String calculateFileHash(String storagePath) {
        if (StrUtil.isBlank(storagePath)) {
            return null;
        }
        return DigestUtil.sha256Hex(Path.of(storagePath).toFile());
    }

    private String buildSafeRemark(Exception exception) {
        String message = exception.getMessage();
        if (StrUtil.isBlank(message)) {
            message = exception.getClass().getSimpleName();
        }
        return StrUtil.maxLength(message, SAFE_REMARK_LENGTH);
    }
}
