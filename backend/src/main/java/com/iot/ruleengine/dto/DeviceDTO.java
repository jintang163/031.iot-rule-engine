package com.iot.ruleengine.dto;

import lombok.Data;

@Data
public class DeviceDTO {

    private Long id;

    private String deviceId;

    private String name;

    private String type;

    private String actions;

    private Integer online;
}
