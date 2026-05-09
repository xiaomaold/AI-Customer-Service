package com.airag.modules.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminConfigVO {

    private Long configId;
    private String configKey;
    private String configName;
    private String configValue;
    private String valueType;
    private String remark;
    private String status;
    private LocalDateTime updateTime;
}
