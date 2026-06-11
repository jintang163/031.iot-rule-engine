package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("iot_device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private String deviceId;

    @TableField("name")
    private String name;

    @TableField("type")
    private String type;

    @TableField("actions")
    private String actions;

    @TableField("status")
    private String status;

    @TableField("online")
    private Integer online;

    @TableField("location")
    private String location;

    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    @TableField(exist = false)
    private String productKey;

    @TableField(exist = false)
    private String firmwareVersion;

    @TableField(exist = false)
    private String ipAddress;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
