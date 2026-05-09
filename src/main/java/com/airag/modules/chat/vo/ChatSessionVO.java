package com.airag.modules.chat.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatSessionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long userId;
    private String sessionName;
    private String sessionStatus;
    private Integer pinned;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;
}
