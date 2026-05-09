package com.airag.modules.knowledge.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.airag.common.exception.BusinessException;
import com.airag.config.KnowledgeProperties;
import com.airag.modules.knowledge.dto.KnowledgeDocumentUploadResponse;
import com.airag.modules.knowledge.entity.KnowledgeDocument;
import com.airag.modules.knowledge.enums.DocumentStatusEnum;
import com.airag.modules.knowledge.mapper.KnowledgeDocumentMapper;
import com.airag.modules.knowledge.mq.KnowledgeDocumentTaskMessage;
import com.airag.modules.knowledge.mq.KnowledgeDocumentTaskProducer;
import com.airag.modules.knowledge.mq.KnowledgeDocumentTaskType;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.service.KnowledgeDocumentService;
import com.airag.modules.knowledge.store.PgVectorKnowledgeEmbeddingStore;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import com.airag.modules.knowledge.vo.KnowledgeChunkVO;
import com.airag.modules.knowledge.vo.KnowledgeDocumentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeProperties knowledgeProperties;
    private final PgVectorKnowledgeEmbeddingStore pgVectorKnowledgeEmbeddingStore;
    private final KnowledgeDocumentTaskProducer knowledgeDocumentTaskProducer;

    @Value("${langchain4j.community.dashscope.embedding-model.model-name:text-embedding-v4}")
    private String embeddingModelName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentUploadResponse upload(Long knowledgeBaseId, MultipartFile file) {
        knowledgeBaseService.assertExists(knowledgeBaseId);
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = FileUtil.extName(originalFilename).toLowerCase();
        KnowledgeDocument existingDocument = findByKnowledgeBaseIdAndFileName(knowledgeBaseId, originalFilename);
        boolean reusedDocument = existingDocument != null;
        Long documentId = reusedDocument ? existingDocument.getId() : IdUtil.getSnowflakeNextId();
        Path uploadPath = buildUploadPath(documentId, extension);
        saveMultipartFile(file, uploadPath);

        KnowledgeDocument document = reusedDocument
                ? rebuildProcessingDocument(existingDocument, file, originalFilename, extension, uploadPath)
                : buildProcessingDocument(documentId, knowledgeBaseId, file, originalFilename, extension, uploadPath);

        persistDocumentBeforeIngest(document, reusedDocument);
        dispatchAsyncTask(documentId, knowledgeBaseId, KnowledgeDocumentTaskType.UPLOAD);

        return KnowledgeDocumentUploadResponse.builder()
                .documentId(documentId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentName(document.getDocumentName())
                .chunkCount(0)
                .parseStatus(DocumentStatusEnum.PROCESSING.getCode())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentUploadResponse uploadStoredFile(Long knowledgeBaseId,
                                                            String originalFilename,
                                                            String contentType,
                                                            Long fileSize,
                                                            Path sourcePath,
                                                            String documentName) {
        knowledgeBaseService.assertExists(knowledgeBaseId);
        validateStoredFile(originalFilename, sourcePath);

        String extension = FileUtil.extName(originalFilename).toLowerCase();
        KnowledgeDocument existingDocument = findByKnowledgeBaseIdAndFileName(knowledgeBaseId, originalFilename);
        boolean reusedDocument = existingDocument != null;
        Long documentId = reusedDocument ? existingDocument.getId() : IdUtil.getSnowflakeNextId();
        Path uploadPath = buildUploadPath(documentId, extension);
        copyStoredFile(sourcePath, uploadPath);

        String effectiveDocumentName = StrUtil.blankToDefault(documentName, FileUtil.mainName(originalFilename));
        KnowledgeDocument document = reusedDocument
                ? rebuildProcessingDocument(existingDocument, effectiveDocumentName, originalFilename, extension, contentType, fileSize, uploadPath)
                : buildProcessingDocument(documentId, knowledgeBaseId, effectiveDocumentName, originalFilename, extension, contentType, fileSize, uploadPath);

        persistDocumentBeforeIngest(document, reusedDocument);
        dispatchAsyncTask(documentId, knowledgeBaseId, KnowledgeDocumentTaskType.UPLOAD);

        return KnowledgeDocumentUploadResponse.builder()
                .documentId(documentId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentName(document.getDocumentName())
                .chunkCount(0)
                .parseStatus(DocumentStatusEnum.PROCESSING.getCode())
                .build();
    }

    @Override
    public List<KnowledgeDocumentVO> listDocuments(Long userId, List<String> roles, Long knowledgeBaseId) {
        if (knowledgeBaseId != null) {
            knowledgeBaseService.assertReadable(userId, roles, knowledgeBaseId);
        }

        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDeleted, 0)
                .orderByDesc(KnowledgeDocument::getCreateTime);
        if (knowledgeBaseId != null) {
            queryWrapper.eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId);
        } else {
            List<Long> accessibleKnowledgeBaseIds = knowledgeBaseService.list(userId, roles).stream()
                    .map(KnowledgeBaseVO::getId)
                    .toList();
            if (accessibleKnowledgeBaseIds.isEmpty()) {
                return List.of();
            }
            queryWrapper.in(KnowledgeDocument::getKnowledgeBaseId, accessibleKnowledgeBaseIds);
        }
        return knowledgeDocumentMapper.selectList(queryWrapper)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<KnowledgeChunkVO> listChunks(Long userId, List<String> roles, Long documentId) {
        KnowledgeDocument document = assertDocumentExists(documentId);
        knowledgeBaseService.assertReadable(userId, roles, document.getKnowledgeBaseId());
        return pgVectorKnowledgeEmbeddingStore.listByDocumentId(documentId).stream()
                .map(segment -> KnowledgeChunkVO.builder()
                        .id(segment.metadata().getString(PgVectorKnowledgeEmbeddingStore.METADATA_CHUNK_ID))
                        .knowledgeBaseId(segment.metadata().getLong(PgVectorKnowledgeEmbeddingStore.METADATA_KNOWLEDGE_BASE_ID))
                        .documentId(segment.metadata().getLong(PgVectorKnowledgeEmbeddingStore.METADATA_DOCUMENT_ID))
                        .chunkIndex(segment.metadata().getInteger(PgVectorKnowledgeEmbeddingStore.METADATA_CHUNK_INDEX))
                        .fileName(segment.metadata().getString(PgVectorKnowledgeEmbeddingStore.METADATA_FILE_NAME))
                        .content(segment.text())
                        .build())
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long documentId) {
        KnowledgeDocument document = assertDocumentExists(documentId);
        pgVectorKnowledgeEmbeddingStore.removeByDocumentId(documentId);
        knowledgeDocumentMapper.deleteById(documentId);
        if (StrUtil.isNotBlank(document.getStoragePath())) {
            FileUtil.del(document.getStoragePath());
        }
        knowledgeBaseService.refreshDocumentCount(document.getKnowledgeBaseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentUploadResponse rebuildIndex(Long documentId) {
        KnowledgeDocument document = assertDocumentExists(documentId);
        knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, documentId)
                .set(KnowledgeDocument::getParseStatus, DocumentStatusEnum.PROCESSING.getCode())
                .set(KnowledgeDocument::getChunkCount, 0)
                .set(KnowledgeDocument::getRemark, "文档正在重新建立索引")
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now()));

        dispatchAsyncTask(documentId, document.getKnowledgeBaseId(), KnowledgeDocumentTaskType.REBUILD_INDEX);

        return KnowledgeDocumentUploadResponse.builder()
                .documentId(document.getId())
                .knowledgeBaseId(document.getKnowledgeBaseId())
                .documentName(document.getDocumentName())
                .chunkCount(0)
                .parseStatus(DocumentStatusEnum.PROCESSING.getCode())
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String extension = FileUtil.extName(file.getOriginalFilename()).toLowerCase();
        if (StrUtil.isBlank(extension) || !knowledgeProperties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException("仅支持 PDF、Word、TXT 和 Markdown 文件");
        }
    }

    private Path buildUploadPath(Long documentId, String extension) {
        try {
            Path dir = Path.of(knowledgeProperties.getUploadDir());
            Files.createDirectories(dir);
            return dir.resolve(documentId + "." + extension);
        } catch (IOException exception) {
            throw new BusinessException("创建上传目录失败");
        }
    }

    private void saveMultipartFile(MultipartFile file, Path uploadPath) {
        try {
            file.transferTo(uploadPath);
        } catch (IOException exception) {
            throw new BusinessException("保存上传文件失败");
        }
    }

    private void validateStoredFile(String originalFilename, Path sourcePath) {
        if (StrUtil.isBlank(originalFilename) || sourcePath == null || !Files.exists(sourcePath)) {
            throw new BusinessException("待上传文档不存在");
        }
        String extension = FileUtil.extName(originalFilename).toLowerCase();
        if (StrUtil.isBlank(extension) || !knowledgeProperties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException("仅支持 PDF、Word、TXT 和 Markdown 文件");
        }
    }

    private void copyStoredFile(Path sourcePath, Path uploadPath) {
        try {
            Files.copy(sourcePath, uploadPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException("保存待上传文档失败");
        }
    }

    private KnowledgeDocument buildProcessingDocument(Long documentId,
                                                      Long knowledgeBaseId,
                                                      MultipartFile file,
                                                      String originalFilename,
                                                      String extension,
                                                      Path uploadPath) {
        return buildProcessingDocument(
                documentId,
                knowledgeBaseId,
                FileUtil.mainName(originalFilename),
                originalFilename,
                extension,
                file.getContentType(),
                file.getSize(),
                uploadPath
        );
    }

    private KnowledgeDocument buildProcessingDocument(Long documentId,
                                                      Long knowledgeBaseId,
                                                      String documentName,
                                                      String originalFilename,
                                                      String extension,
                                                      String contentType,
                                                      Long fileSize,
                                                      Path uploadPath) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(documentId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setDocumentName(documentName);
        document.setFileName(originalFilename);
        document.setFileExt(extension);
        document.setContentType(contentType);
        document.setFileSize(fileSize);
        document.setFileHash(calculateFileHash(uploadPath));
        document.setStoragePath(uploadPath.toAbsolutePath().toString());
        document.setParseStatus(DocumentStatusEnum.PROCESSING.getCode());
        document.setChunkCount(0);
        document.setEmbeddingModel(embeddingModelName);
        document.setRemark("文档已接收，正在后台处理中");
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setDeleted(0);
        return document;
    }

    private KnowledgeDocument rebuildProcessingDocument(KnowledgeDocument existingDocument,
                                                        MultipartFile file,
                                                        String originalFilename,
                                                        String extension,
                                                        Path uploadPath) {
        return rebuildProcessingDocument(
                existingDocument,
                FileUtil.mainName(originalFilename),
                originalFilename,
                extension,
                file.getContentType(),
                file.getSize(),
                uploadPath
        );
    }

    private KnowledgeDocument rebuildProcessingDocument(KnowledgeDocument existingDocument,
                                                        String documentName,
                                                        String originalFilename,
                                                        String extension,
                                                        String contentType,
                                                        Long fileSize,
                                                        Path uploadPath) {
        String previousStoragePath = existingDocument.getStoragePath();
        existingDocument.setDocumentName(documentName);
        existingDocument.setFileName(originalFilename);
        existingDocument.setFileExt(extension);
        existingDocument.setContentType(contentType);
        existingDocument.setFileSize(fileSize);
        existingDocument.setFileHash(calculateFileHash(uploadPath));
        existingDocument.setStoragePath(uploadPath.toAbsolutePath().toString());
        existingDocument.setParseStatus(DocumentStatusEnum.PROCESSING.getCode());
        existingDocument.setChunkCount(0);
        existingDocument.setEmbeddingModel(embeddingModelName);
        existingDocument.setRemark("检测到同名文档，系统将覆盖原文档并在后台重新建立索引");
        existingDocument.setUpdateTime(LocalDateTime.now());
        deleteOldStorageFileIfNeeded(previousStoragePath, existingDocument.getStoragePath());
        return existingDocument;
    }

    private void persistDocumentBeforeIngest(KnowledgeDocument document, boolean reusedDocument) {
        if (reusedDocument) {
            knowledgeDocumentMapper.updateById(document);
            return;
        }
        knowledgeDocumentMapper.insert(document);
        knowledgeBaseService.refreshDocumentCount(document.getKnowledgeBaseId());
    }

    private void dispatchAsyncTask(Long documentId, Long knowledgeBaseId, KnowledgeDocumentTaskType taskType) {
        try {
            knowledgeDocumentTaskProducer.send(KnowledgeDocumentTaskMessage.builder()
                    .documentId(documentId)
                    .knowledgeBaseId(knowledgeBaseId)
                    .taskType(taskType)
                    .build());
        } catch (Exception exception) {
            log.error("Dispatch knowledge document async task failed documentId={}, taskType={}", documentId, taskType, exception);
            throw new BusinessException("文档已保存，但提交后台处理任务失败，请检查 RabbitMQ 是否可用");
        }
    }

    private KnowledgeDocument findByKnowledgeBaseIdAndFileName(Long knowledgeBaseId, String fileName) {
        return knowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeDocument::getFileName, fileName)
                .eq(KnowledgeDocument::getDeleted, 0)
                .last("limit 1"));
    }

    private void deleteOldStorageFileIfNeeded(String previousStoragePath, String currentStoragePath) {
        if (StrUtil.isBlank(previousStoragePath) || StrUtil.equals(previousStoragePath, currentStoragePath)) {
            return;
        }
        FileUtil.del(previousStoragePath);
    }

    private String calculateFileHash(Path uploadPath) {
        if (uploadPath == null || !Files.exists(uploadPath)) {
            throw new BusinessException("上传文件不存在，无法计算文件指纹");
        }
        return DigestUtil.sha256Hex(uploadPath.toFile());
    }

    private KnowledgeDocument assertDocumentExists(Long documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);
        if (document == null || (document.getDeleted() != null && document.getDeleted() == 1)) {
            throw new BusinessException("知识文档不存在");
        }
        return document;
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocument document) {
        return KnowledgeDocumentVO.builder()
                .id(document.getId())
                .knowledgeBaseId(document.getKnowledgeBaseId())
                .documentName(document.getDocumentName())
                .fileName(document.getFileName())
                .fileExt(document.getFileExt())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .parseStatus(document.getParseStatus())
                .chunkCount(document.getChunkCount())
                .embeddingModel(document.getEmbeddingModel())
                .remark(document.getRemark())
                .createTime(document.getCreateTime())
                .build();
    }
}
