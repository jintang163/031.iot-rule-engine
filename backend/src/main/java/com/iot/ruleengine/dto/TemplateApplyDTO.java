package com.iot.ruleengine.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class TemplateApplyDTO {

    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    private String ruleName;

    private String ruleDescription;
}
