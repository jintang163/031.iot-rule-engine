package com.iot.ruleengine.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class TemplateDTO {

    private Long id;

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String description;

    private String icon;

    @NotBlank(message = "模板分类不能为空")
    private String category;

    private String ruleJson;

    private String ruleConfig;

    private String scope;

    private String sourceType;

    private Long sourceRuleId;

    private String teamId;

    private String authorId;

    private String authorName;

    private String version;
}
