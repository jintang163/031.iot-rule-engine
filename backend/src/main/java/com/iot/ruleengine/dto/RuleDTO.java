package com.iot.ruleengine.dto;

import lombok.Data;

@Data
public class RuleDTO {

    private Long id;

    private String name;

    private String description;

    private String ruleJson;

    private Integer status;

    private Integer priority;

    private String mutexGroup;
}
