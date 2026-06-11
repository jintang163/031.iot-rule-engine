package com.iot.ruleengine.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeviceDTO {

    private Long id;

    private String deviceId;

    private String name;

    private String type;

    private String actions;

    private String status;

    private Integer online;

    private String location;

    private LocalDateTime lastOnlineTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
