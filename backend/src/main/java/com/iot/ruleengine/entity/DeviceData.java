package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("iot_device_data")
public class DeviceData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String telemetryData;

    private Integer status;

    @TableField(exist = false)
    private Map<String, Object> telemetry;

    @TableField(exist = false)
    private Double temperature;

    @TableField(exist = false)
    private Double humidity;

    @TableField(exist = false)
    private Double pressure;

    @TableField(exist = false)
    private Integer batteryLevel;

    @TableField(exist = false)
    private String deviceStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
