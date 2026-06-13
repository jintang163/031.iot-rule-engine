package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("iot_action_log")
public class ActionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("rule_id")
    private Long ruleId;

    @TableField("rule_name")
    private String ruleName;

    @TableField("device_id")
    private String deviceId;

    @TableField("action_type")
    private String actionType;

    @TableField("action_params")
    private String actionParams;

    @TableField("result")
    private Integer result;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("execute_time")
    private LocalDateTime executeTime;

    @TableField(exist = false)
    private String actionContent;

    @TableField(exist = false)
    private Integer executeStatus;

    @TableField(exist = false)
    private String errorMessage;

    @TableField(exist = false)
    private String params;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
