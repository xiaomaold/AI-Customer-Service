package com.airag.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameSessionRequest {

    @NotBlank(message = "会话名称不能为空")
    @Size(max = 255, message = "会话名称长度不能超过255个字符")
    private String sessionName;
}
