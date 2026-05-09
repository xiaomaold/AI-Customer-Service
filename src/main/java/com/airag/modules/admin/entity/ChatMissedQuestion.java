package com.airag.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_missed_question")
public class ChatMissedQuestion {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long knowledgeBaseId;
    private String routeMode;
    private String question;
    private String answer;
    private String missReason;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
