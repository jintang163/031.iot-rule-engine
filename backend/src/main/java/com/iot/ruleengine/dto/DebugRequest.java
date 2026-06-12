package com.iot.ruleengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebugRequest {

    private Long ruleId;

    private Set<String> breakpointNodeIds;

    private boolean singleStepMode;

    private String deviceId;

    private Double temperature;

    private Double humidity;

    private String status;

    private Boolean online;
}
