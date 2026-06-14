package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_notify_config")
public class AlertNotifyConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String channel;

    private String config;

    @TableField("enabled_levels")
    private String enabledLevels;

    private Integer enabled;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
