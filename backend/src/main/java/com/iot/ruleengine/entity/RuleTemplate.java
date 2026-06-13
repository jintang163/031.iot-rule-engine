package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_template")
public class RuleTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String icon;

    private String category;

    @TableField("rule_json")
    private String ruleJson;

    @TableField("rule_config")
    private String ruleConfig;

    private String scope;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_rule_id")
    private Long sourceRuleId;

    @TableField("author_id")
    private String authorId;

    @TableField("author_name")
    private String authorName;

    private String version;

    @TableField("review_status")
    private Integer reviewStatus;

    @TableField("reviewer_id")
    private String reviewerId;

    @TableField("review_time")
    private LocalDateTime reviewTime;

    @TableField("review_remark")
    private String reviewRemark;

    @TableField("apply_count")
    private Integer applyCount;

    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
