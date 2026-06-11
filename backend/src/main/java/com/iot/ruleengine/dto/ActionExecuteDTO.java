package com.iot.ruleengine.dto;

import lombok.Data;

@Data
public class ActionExecuteDTO {

    private Long ruleId;

    private String actionType;

    private String actionParams;

    private String deviceId;
}
