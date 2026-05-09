package com.airag.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_document_analysis")
public class AiDocumentAnalysisRecord {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long suggestedKnowledgeBaseId;

    private Long uploadedKnowledgeBaseId;

    private Long uploadedDocumentId;

    private String originalFileName;

    private String contentType;

    private Long fileSize;

    private String tempFilePath;

    private String suggestedKnowledgeBaseName;

    private String suggestedDocumentName;

    private String summary;

    private String tagsJson;

    private String reason;

    private String recommendedAction;

    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
