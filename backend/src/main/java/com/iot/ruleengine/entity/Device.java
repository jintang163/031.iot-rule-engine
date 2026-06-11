package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("iot_device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String deviceName;

    private String deviceType;

    private String productKey;

    private Integer onlineStatus;

    private LocalDateTime lastReportTime;

    private String firmwareVersion;

    private String ipAddress;

    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
