package com.airag.modules.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminMissedQuestionVO {

    private Long missedQuestionId;
    private Long userId;
    private Long sessionId;
    private Long knowledgeBaseId;
    private String routeMode;
    private String question;
    private String answer;
    private String missReason;
    private String status;
    private LocalDateTime createTime;
}
