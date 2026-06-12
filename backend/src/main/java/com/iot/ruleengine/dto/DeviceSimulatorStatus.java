package com.iot.ruleengine.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeviceSimulatorStatus {

    private String deviceId;

    private String deviceType;

    private Boolean running;

    private Integer intervalSeconds;

    private Double lastTemperature;

    private Double lastHumidity;

    private LocalDateTime lastReportTime;

    private Long reportCount;
}
