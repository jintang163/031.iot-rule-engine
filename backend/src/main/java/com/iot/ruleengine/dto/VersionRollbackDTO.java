package com.iot.ruleengine.dto;

import lombok.Data;

@Data
public class VersionRollbackDTO {

    private Long ruleId;

    private Integer version;

    private String comment;
}
