package com.iot.ruleengine.flink.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleUpdateEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_ADD = "ADD";
    public static final String TYPE_UPDATE = "UPDATE";
    public static final String TYPE_DELETE = "DELETE";

    private String ruleId;

    private String ruleName;

    private String type;

    private String drlContent;

    private String ruleJson;

    private Integer priority;

    private String mutexGroup;

    private long timestamp;
}
