package com.iot.ruleengine.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActionExecuteDTO {

    private Long ruleId;

    private String ruleName;

    private String actionType;

    private String actionParams;

    private String deviceId;

    private Integer result;

    private Integer retryCount;

    private String errorMsg;

    private LocalDateTime executeTime;
}
