package com.airag.modules.knowledge.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.airag.common.exception.BusinessException;
import com.airag.config.KnowledgeProperties;
import com.airag.modules.chat.ai.AiDocumentAnalysisAiService;
import com.airag.modules.chat.prompt.PromptTemplateService;
import com.airag.modules.knowledge.dto.AiDocumentAnalyzeResponse;
import com.airag.modules.knowledge.dto.AiDocumentUploadConfirmRequest;
import com.airag.modules.knowledge.dto.KnowledgeDocumentUploadResponse;
import com.airag.modules.knowledge.entity.AiDocumentAnalysisRecord;
import com.airag.modules.knowledge.mapper.AiDocumentAnalysisMapper;
import com.airag.modules.knowledge.service.AiDocumentIngestionService;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.service.KnowledgeDocumentService;
import com.airag.modules.knowledge.vo.UserKnowledgePermissionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentIngestionServiceImpl implements AiDocumentIngestionService {

    private static final int ANALYSIS_PREVIEW_LENGTH = 4000;
    private static final int SUMMARY_LENGTH = 80;
    private static final long ANALYSIS_TIMEOUT_SECONDS = 45L;

    private final AiDocumentAnalysisMapper aiDocumentAnalysisMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeProperties knowledgeProperties;
    private final DocumentParser apacheTikaDocumentParser;
    private final AiDocumentAnalysisAiService aiDocumentAnalysisAiService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDocumentAnalyzeResponse analyze(Long userId, List<String> roles, Long knowledgeBaseId, MultipartFile file) {
        return execute(userId, roles, knowledgeBaseId, "", file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDocumentAnalyzeResponse execute(Long userId,
                                             List<String> roles,
                                             Long knowledgeBaseId,
                                             String instruction,
                                             MultipartFile file) {
        validateAnalyzeFile(file);
        if (knowledgeBaseId != null) {
            knowledgeBaseService.assertReadable(userId, roles, knowledgeBaseId);
        }

        Long analysisId = IdUtil.getSnowflakeNextId();
        String originalFilename = Optional.ofNullable(file.getOriginalFilename())
                .filter(StrUtil::isNotBlank)
                .orElse("document");
        Path tempFilePath = buildTempFilePath(analysisId, originalFilename);
        saveTempFile(file, tempFilePath);

        String preview = extractPreview(tempFilePath);
        List<UserKnowledgePermissionVO> accessibleKnowledgeBases = loadAccessibleKnowledgeBases(userId, roles, knowledgeBaseId);
        DocumentTaskType taskType = classifyTaskType(instruction);
        AnalysisSuggestion suggestion = analyzeDocumentSuggestion(
                originalFilename,
                preview,
                knowledgeBaseId,
                accessibleKnowledgeBases,
                taskType,
                instruction
        );

        UserKnowledgePermissionVO suggestedKnowledgeBase = resolveSuggestedKnowledgeBase(
                suggestion.suggestedKnowledgeBaseName(),
                knowledgeBaseId,
                accessibleKnowledgeBases
        );
        Long suggestedKnowledgeBaseId = suggestedKnowledgeBase == null ? knowledgeBaseId : suggestedKnowledgeBase.getKnowledgeBaseId();
        String suggestedKnowledgeBaseName = suggestedKnowledgeBase == null ? null : suggestedKnowledgeBase.getKnowledgeBaseName();
        String suggestedDocumentName = StrUtil.blankToDefault(
                suggestion.suggestedDocumentName(),
                FileUtil.mainName(originalFilename)
        );
        List<String> tags = normalizeTags(suggestion.tags(), preview, originalFilename, instruction);
        String recommendedAction = StrUtil.blankToDefault(suggestion.recommendedAction(), defaultRecommendedAction(taskType));
        String reason = StrUtil.blankToDefault(suggestion.reason(), "已根据文档内容和你的指令生成处理结果。");
        String answer = buildTaskAnswer(taskType, suggestion.answer(), preview, suggestedKnowledgeBaseName, suggestedDocumentName, instruction);
        boolean canUpload = suggestedKnowledgeBaseId != null && canManageKnowledgeBase(userId, roles, suggestedKnowledgeBaseId);
        String uploadDeniedReason = canUpload ? null : buildUploadDeniedReason(knowledgeBaseId, suggestedKnowledgeBaseName, taskType);

        LocalDateTime now = LocalDateTime.now();
        AiDocumentAnalysisRecord record = new AiDocumentAnalysisRecord();
        record.setId(analysisId);
        record.setUserId(userId);
        record.setSuggestedKnowledgeBaseId(suggestedKnowledgeBaseId);
        record.setOriginalFileName(originalFilename);
        record.setContentType(file.getContentType());
        record.setFileSize(file.getSize());
        record.setTempFilePath(tempFilePath.toAbsolutePath().toString());
        record.setSuggestedKnowledgeBaseName(suggestedKnowledgeBaseName);
        record.setSuggestedDocumentName(suggestedDocumentName);
        record.setSummary(buildSummary(suggestion.summary(), preview, originalFilename));
        record.setTagsJson(writeTags(tags));
        record.setReason(reason);
        record.setRecommendedAction(recommendedAction);
        record.setStatus("ANALYZED");
        record.setExpiresAt(now.plusHours(24));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(0);
        aiDocumentAnalysisMapper.insert(record);

        return AiDocumentAnalyzeResponse.builder()
                .analysisId(analysisId)
                .taskType(taskType.name())
                .answer(answer)
                .suggestedKnowledgeBaseId(suggestedKnowledgeBaseId)
                .suggestedKnowledgeBaseName(suggestedKnowledgeBaseName)
                .suggestedDocumentName(suggestedDocumentName)
                .summary(record.getSummary())
                .tags(tags)
                .recommendedAction(recommendedAction)
                .reason(reason)
                .canUpload(canUpload)
                .uploadDeniedReason(uploadDeniedReason)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentUploadResponse confirmUpload(Long userId, List<String> roles, AiDocumentUploadConfirmRequest request) {
        AiDocumentAnalysisRecord record = aiDocumentAnalysisMapper.selectOne(new LambdaQueryWrapper<AiDocumentAnalysisRecord>()
                .eq(AiDocumentAnalysisRecord::getId, request.getAnalysisId())
                .eq(AiDocumentAnalysisRecord::getDeleted, 0)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException("AI 文档分析记录不存在");
        }
        if (!Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException("当前用户无权使用该分析记录");
        }
        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("该分析记录已过期，请重新上传文档分析");
        }
        if (StrUtil.equalsIgnoreCase(record.getStatus(), "UPLOADED")) {
            throw new BusinessException("该分析记录已完成上传");
        }

        Long targetKnowledgeBaseId = request.getKnowledgeBaseId() != null
                ? request.getKnowledgeBaseId()
                : record.getSuggestedKnowledgeBaseId();
        if (targetKnowledgeBaseId == null) {
            throw new BusinessException("当前分析结果尚未识别出可上传的知识库");
        }
        knowledgeBaseService.assertManageable(userId, roles, targetKnowledgeBaseId);

        Path tempFilePath = Path.of(record.getTempFilePath());
        if (!Files.exists(tempFilePath)) {
            throw new BusinessException("待上传文档不存在，请重新分析文档");
        }

        String documentName = StrUtil.blankToDefault(
                request.getDocumentName(),
                StrUtil.blankToDefault(record.getSuggestedDocumentName(), FileUtil.mainName(record.getOriginalFileName()))
        );
        KnowledgeDocumentUploadResponse response = knowledgeDocumentService.uploadStoredFile(
                targetKnowledgeBaseId,
                record.getOriginalFileName(),
                record.getContentType(),
                record.getFileSize(),
                tempFilePath,
                documentName
        );

        record.setUploadedKnowledgeBaseId(targetKnowledgeBaseId);
        record.setUploadedDocumentId(response.getDocumentId());
        record.setStatus("UPLOADED");
        record.setUpdateTime(LocalDateTime.now());
        aiDocumentAnalysisMapper.updateById(record);
        deleteTempFileQuietly(tempFilePath);
        return response;
    }

    private void validateAnalyzeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String extension = FileUtil.extName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(extension) || !knowledgeProperties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException("仅支持 PDF、Word、TXT 和 Markdown 文档");
        }
    }

    private Path buildTempFilePath(Long analysisId, String originalFilename) {
        String extension = FileUtil.extName(originalFilename).toLowerCase(Locale.ROOT);
        Path dir = Path.of(knowledgeProperties.getUploadDir()).resolve("ai-analysis");
        try {
            Files.createDirectories(dir);
            return dir.resolve(analysisId + "." + extension);
        } catch (IOException exception) {
            throw new BusinessException("创建 AI 文档分析目录失败");
        }
    }

    private void saveTempFile(MultipartFile file, Path tempFilePath) {
        try {
            file.transferTo(tempFilePath);
        } catch (IOException exception) {
            throw new BusinessException("保存待分析文档失败");
        }
    }

    private String extractPreview(Path tempFilePath) {
        try {
            Document document = FileSystemDocumentLoader.loadDocument(tempFilePath, apacheTikaDocumentParser);
            return StrUtil.maxLength(StrUtil.blankToDefault(document.text(), ""), ANALYSIS_PREVIEW_LENGTH);
        } catch (Exception exception) {
            log.warn("AI document preview extraction failed path={}", tempFilePath, exception);
            return "";
        }
    }

    private List<UserKnowledgePermissionVO> loadAccessibleKnowledgeBases(Long userId, List<String> roles, Long knowledgeBaseId) {
        List<UserKnowledgePermissionVO> permissions = knowledgeBaseService.listAccessibleByUserId(userId, roles);
        if (knowledgeBaseId == null) {
            return permissions;
        }
        return permissions.stream()
                .filter(permission -> Objects.equals(permission.getKnowledgeBaseId(), knowledgeBaseId))
                .toList();
    }

    private AnalysisSuggestion analyzeDocumentSuggestion(String originalFilename,
                                                        String preview,
                                                        Long knowledgeBaseId,
                                                        List<UserKnowledgePermissionVO> accessibleKnowledgeBases,
                                                        DocumentTaskType taskType,
                                                        String instruction) {
        if (StrUtil.isBlank(preview) && accessibleKnowledgeBases.isEmpty()) {
            return fallbackSuggestion(originalFilename, preview, null, taskType, instruction);
        }
        String targetKnowledgeBase = knowledgeBaseId == null
                ? ""
                : accessibleKnowledgeBases.stream().findFirst().map(UserKnowledgePermissionVO::getKnowledgeBaseName).orElse("");
        try {
            String response = collectAiResponse(
                    taskType.name(),
                    StrUtil.blankToDefault(instruction, "请根据文档内容完成当前任务。"),
                    originalFilename,
                    targetKnowledgeBase,
                    formatKnowledgeBases(accessibleKnowledgeBases),
                    StrUtil.blankToDefault(preview, "无可用内容预览")
            );
            AnalysisSuggestion parsed = parseSuggestion(response);
            if (parsed.isEmpty()) {
                return fallbackSuggestion(originalFilename, preview, targetKnowledgeBase, taskType, instruction);
            }
            return parsed;
        } catch (Exception exception) {
            log.warn("AI document task fallback triggered fileName={}, taskType={}", originalFilename, taskType, exception);
            return fallbackSuggestion(originalFilename, preview, targetKnowledgeBase, taskType, instruction);
        }
    }

    private String collectAiResponse(String taskType,
                                     String instruction,
                                     String fileName,
                                     String targetKnowledgeBase,
                                     String knowledgeBases,
                                     String contentPreview) throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder builder = new StringBuilder();
        TokenStream tokenStream = aiDocumentAnalysisAiService.analyze(
                promptTemplateService.aiDocumentAnalysisSystemPrompt(),
                taskType,
                instruction,
                fileName,
                targetKnowledgeBase,
                knowledgeBases,
                contentPreview
        );
        tokenStream.onPartialResponse(builder::append)
                .onCompleteResponse(response -> future.complete(builder.toString().trim()))
                .onError(future::completeExceptionally)
                .start();
        return future.get(ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private AnalysisSuggestion parseSuggestion(String response) {
        String taskType = extractLineValue(response, "TASK_TYPE:");
        String answer = extractLineValue(response, "ANSWER:");
        String suggestedKnowledgeBase = extractLineValue(response, "SUGGESTED_KNOWLEDGE_BASE:");
        String suggestedDocumentName = extractLineValue(response, "SUGGESTED_DOCUMENT_NAME:");
        String summary = extractLineValue(response, "SUMMARY:");
        List<String> tags = splitTags(extractLineValue(response, "TAGS:"));
        String recommendedAction = extractLineValue(response, "RECOMMENDED_ACTION:");
        String reason = extractLineValue(response, "REASON:");
        return new AnalysisSuggestion(taskType, answer, suggestedKnowledgeBase, suggestedDocumentName, summary, tags, recommendedAction, reason);
    }

    private String extractLineValue(String response, String prefix) {
        return response.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElse("");
    }

    private AnalysisSuggestion fallbackSuggestion(String originalFilename,
                                                  String preview,
                                                  String targetKnowledgeBase,
                                                  DocumentTaskType taskType,
                                                  String instruction) {
        List<String> tags = buildFallbackTags(originalFilename, preview, instruction);
        return new AnalysisSuggestion(
                taskType.name(),
                "",
                targetKnowledgeBase,
                FileUtil.mainName(originalFilename),
                buildSummary("", preview, originalFilename),
                tags,
                defaultRecommendedAction(taskType),
                "已根据文件名、文档预览和你的指令完成基础识别。"
        );
    }

    private List<String> buildFallbackTags(String originalFilename, String preview, String instruction) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String combined = StrUtil.blankToDefault(originalFilename, "") + " " + StrUtil.blankToDefault(preview, "") + " " + StrUtil.blankToDefault(instruction, "");
        if (StrUtil.containsAny(combined, "退款", "售后")) {
            tags.add("退款");
            tags.add("售后");
        }
        if (StrUtil.containsAny(combined, "请假", "病假", "事假")) {
            tags.add("请假");
        }
        if (StrUtil.containsAny(combined, "邮箱", "电话", "联系人")) {
            tags.add("联系方式");
        }
        return tags.stream().limit(5).toList();
    }

    private UserKnowledgePermissionVO resolveSuggestedKnowledgeBase(String suggestedName,
                                                                    Long requestedKnowledgeBaseId,
                                                                    List<UserKnowledgePermissionVO> accessibleKnowledgeBases) {
        if (requestedKnowledgeBaseId != null) {
            return accessibleKnowledgeBases.stream().findFirst().orElse(null);
        }
        if (accessibleKnowledgeBases.isEmpty()) {
            return null;
        }
        if (accessibleKnowledgeBases.size() == 1) {
            return accessibleKnowledgeBases.get(0);
        }
        String normalizedSuggestedName = normalizeText(suggestedName);
        if (StrUtil.isBlank(normalizedSuggestedName)) {
            return null;
        }
        return accessibleKnowledgeBases.stream()
                .sorted(Comparator.comparing(UserKnowledgePermissionVO::getKnowledgeBaseName))
                .filter(permission -> normalizeText(permission.getKnowledgeBaseName()).equals(normalizedSuggestedName)
                        || normalizeText(permission.getKnowledgeBaseName()).contains(normalizedSuggestedName)
                        || normalizedSuggestedName.contains(normalizeText(permission.getKnowledgeBaseName())))
                .findFirst()
                .orElse(null);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "")
                .replace("`", "")
                .replace("“", "")
                .replace("”", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeTags(List<String> tags, String preview, String originalFilename, String instruction) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        tags.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .map(tag -> StrUtil.maxLength(tag, 20))
                .forEach(normalized::add);
        buildFallbackTags(originalFilename, preview, instruction).forEach(normalized::add);
        return normalized.stream().limit(5).toList();
    }

    private List<String> splitTags(String tagsLine) {
        if (StrUtil.isBlank(tagsLine)) {
            return List.of();
        }
        return StrUtil.split(tagsLine.replace("，", ",").replace("；", ",").replace("|", ","), ',')
                .stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    private String buildSummary(String summary, String preview, String originalFilename) {
        if (StrUtil.isNotBlank(summary)) {
            return StrUtil.maxLength(summary.trim(), SUMMARY_LENGTH);
        }
        if (StrUtil.isNotBlank(preview)) {
            String normalized = preview.replaceAll("\\s+", " ").trim();
            return StrUtil.maxLength(normalized, SUMMARY_LENGTH);
        }
        return "已上传文档《" + FileUtil.mainName(originalFilename) + "》，等待进一步处理。";
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("保存 AI 文档标签失败");
        }
    }

    private String formatKnowledgeBases(List<UserKnowledgePermissionVO> accessibleKnowledgeBases) {
        if (accessibleKnowledgeBases.isEmpty()) {
            return "No accessible knowledge bases.";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < accessibleKnowledgeBases.size(); index++) {
            UserKnowledgePermissionVO permission = accessibleKnowledgeBases.get(index);
            builder.append(index + 1)
                    .append(". ")
                    .append(permission.getKnowledgeBaseName())
                    .append(" | permission=")
                    .append(permission.getPermissionType());
            if (StrUtil.isNotBlank(permission.getDescription())) {
                builder.append(" | description=").append(permission.getDescription());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private boolean canManageKnowledgeBase(Long userId, List<String> roles, Long knowledgeBaseId) {
        try {
            knowledgeBaseService.assertManageable(userId, roles, knowledgeBaseId);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private String buildUploadDeniedReason(Long requestedKnowledgeBaseId, String suggestedKnowledgeBaseName, DocumentTaskType taskType) {
        if (!taskType.isUploadRelated()) {
            return null;
        }
        if (requestedKnowledgeBaseId != null) {
            return "你可以查看 AI 处理结果，但无权上传到当前指定的知识库。";
        }
        if (StrUtil.isNotBlank(suggestedKnowledgeBaseName)) {
            return "你可以查看 AI 处理结果，但无权上传到知识库「" + suggestedKnowledgeBaseName + "」。";
        }
        return "当前仅完成文档处理，尚未确认可上传的目标知识库。";
    }

    private void deleteTempFileQuietly(Path tempFilePath) {
        try {
            Files.deleteIfExists(tempFilePath);
        } catch (IOException exception) {
            log.warn("Delete ai analysis temp file failed path={}", tempFilePath, exception);
        }
    }

    private DocumentTaskType classifyTaskType(String instruction) {
        String normalized = StrUtil.blankToDefault(instruction, "").toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(normalized)) {
            return DocumentTaskType.ANALYZE;
        }
        if (containsAny(normalized, "上传", "入库", "放到", "存到")) {
            return DocumentTaskType.PREPARE_UPLOAD;
        }
        if (containsAny(normalized, "总结", "概括", "摘要")) {
            return DocumentTaskType.SUMMARIZE;
        }
        if (containsAny(normalized, "提取", "抽取", "邮箱", "电话", "联系人", "关键信息", "要点")) {
            return DocumentTaskType.EXTRACT_FACTS;
        }
        if (containsAny(normalized, "改写", "整理", "润色", "表格", "列表")) {
            return DocumentTaskType.REWRITE;
        }
        if (containsAny(normalized, "命名", "标题", "取名", "名字")) {
            return DocumentTaskType.RENAME;
        }
        if (containsAny(normalized, "知识库", "识别", "推荐")) {
            return DocumentTaskType.ANALYZE;
        }
        return DocumentTaskType.ANALYZE;
    }

    private boolean containsAny(String source, String... candidates) {
        for (String candidate : candidates) {
            if (source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String defaultRecommendedAction(DocumentTaskType taskType) {
        return taskType.isUploadRelated() ? "READY_TO_UPLOAD" : "ANALYZE_ONLY";
    }

    private String buildTaskAnswer(DocumentTaskType taskType,
                                   String aiAnswer,
                                   String preview,
                                   String suggestedKnowledgeBaseName,
                                   String suggestedDocumentName,
                                   String instruction) {
        if (StrUtil.isNotBlank(aiAnswer)) {
            return aiAnswer;
        }

        return switch (taskType) {
            case ANALYZE -> "已完成文档解析识别。推荐知识库：" + StrUtil.blankToDefault(suggestedKnowledgeBaseName, "暂未识别")
                    + "；推荐文档名：" + StrUtil.blankToDefault(suggestedDocumentName, "未生成");
            case SUMMARIZE -> buildSummary("", preview, suggestedDocumentName);
            case EXTRACT_FACTS -> "已完成文档关键信息提取，请查看下方摘要和标签。";
            case REWRITE -> "已根据文档内容和你的要求完成整理，请查看当前结果。";
            case RENAME -> "建议将文档命名为《" + StrUtil.blankToDefault(suggestedDocumentName, "未生成标题") + "》。";
            case PREPARE_UPLOAD -> "已完成上传前分析。"
                    + (StrUtil.isNotBlank(suggestedKnowledgeBaseName) ? " 推荐知识库：" + suggestedKnowledgeBaseName + "。" : " 暂未识别出明确知识库。")
                    + " 你可以继续确认上传。";
        };
    }

    private enum DocumentTaskType {
        ANALYZE,
        SUMMARIZE,
        EXTRACT_FACTS,
        REWRITE,
        RENAME,
        PREPARE_UPLOAD;

        boolean isUploadRelated() {
            return this == PREPARE_UPLOAD;
        }
    }

    private record AnalysisSuggestion(String taskType,
                                      String answer,
                                      String suggestedKnowledgeBaseName,
                                      String suggestedDocumentName,
                                      String summary,
                                      List<String> tags,
                                      String recommendedAction,
                                      String reason) {

        boolean isEmpty() {
            return StrUtil.isAllBlank(
                    taskType,
                    answer,
                    suggestedKnowledgeBaseName,
                    suggestedDocumentName,
                    summary,
                    recommendedAction,
                    reason
            ) && (tags == null || tags.isEmpty());
        }
    }
}
