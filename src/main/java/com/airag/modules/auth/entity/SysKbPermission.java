package com.airag.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_kb_permission")
public class SysKbPermission {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long knowledgeBaseId;
    private String permissionType;
    private LocalDateTime createTime;
}
