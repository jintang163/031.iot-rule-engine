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

    private Integer windowEnabled;

    private String windowType;

    private Integer windowDuration;

    private String windowAggregation;

    private String windowField;

    private String windowOperator;

    private java.math.BigDecimal windowThreshold;

    private Integer cooldownSeconds;

    private Integer chainTriggerEnabled;

    private String chainNextRuleIds;

    private Integer chainDisableSelf;

    private String versionComment;

    private String changeSummary;
}
