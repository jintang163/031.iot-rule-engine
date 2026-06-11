package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("iot_action_log")
public class ActionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String actionType;

    private String actionContent;

    private String params;

    private Integer executeStatus;

    private Integer retryCount;

    private LocalDateTime executeTime;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
