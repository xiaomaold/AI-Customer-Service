package com.airag.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class KnowledgeDocument {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long knowledgeBaseId;

    private String documentName;

    private String fileName;

    private String fileExt;

    private String contentType;

    private Long fileSize;

    private String fileHash;

    private String storagePath;

    private String parseStatus;

    private Integer chunkCount;

    private String embeddingModel;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
