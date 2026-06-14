package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_record")
public class AlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rule_id")
    private Long ruleId;

    @TableField("rule_name")
    private String ruleName;

    @TableField("device_id")
    private String deviceId;

    @TableField("level")
    private String level;

    @TableField("message")
    private String message;

    @TableField("detail")
    private String detail;

    @TableField("status")
    private String status;

    @TableField("acknowledged_by")
    private String acknowledgedBy;

    @TableField("acknowledged_time")
    private LocalDateTime acknowledgedTime;

    @TableField("cleared_by")
    private String clearedBy;

    @TableField("cleared_time")
    private LocalDateTime clearedTime;

    @TableField("notify_channels")
    private String notifyChannels;

    @TableField("notify_status")
    private Integer notifyStatus;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
