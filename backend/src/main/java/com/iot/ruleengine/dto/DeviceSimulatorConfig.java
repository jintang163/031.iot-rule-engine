package com.iot.ruleengine.dto;

import lombok.Data;

@Data
public class DeviceSimulatorConfig {

    private String deviceId;

    private String deviceType;

    private Integer intervalSeconds = 5;

    private Double minTemperature = 15.0;

    private Double maxTemperature = 40.0;

    private Double minHumidity = 30.0;

    private Double maxHumidity = 90.0;

    private Boolean enabled = false;
}
