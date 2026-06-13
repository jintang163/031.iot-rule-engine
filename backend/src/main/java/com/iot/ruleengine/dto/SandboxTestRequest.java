package com.iot.ruleengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxTestRequest {

    private Long ruleId;

    private Map<String, Object> sensorData;

    private String ruleJson;
}
