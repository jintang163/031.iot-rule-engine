package com.iot.ruleengine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class RuleDetailVO {

    private Long id;

    private String name;

    private String description;

    private Integer status;

    private Integer priority;

    private String mutexGroup;

    private String ruleJson;

    private String drlContent;

    private String aviatorExpression;

    private String aviatorActions;

    private List<Object> nodes;

    private List<Object> edges;

    private Map<String, Object> ruleInfo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
