package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_version")
public class RuleVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rule_id")
    private Long ruleId;

    private Integer version;

    private String name;

    private String description;

    @TableField("rule_json")
    private String ruleJson;

    @TableField("drl_content")
    private String drlContent;

    @TableField("aviator_expression")
    private String aviatorExpression;

    @TableField("aviator_actions")
    private String aviatorActions;

    private Integer status;

    private Integer priority;

    @TableField("mutex_group")
    private String mutexGroup;

    @TableField("window_enabled")
    private Integer windowEnabled;

    @TableField("window_type")
    private String windowType;

    @TableField("window_duration")
    private Integer windowDuration;

    @TableField("window_aggregation")
    private String windowAggregation;

    @TableField("window_field")
    private String windowField;

    @TableField("window_operator")
    private String windowOperator;

    @TableField("window_threshold")
    private BigDecimal windowThreshold;

    @TableField("cooldown_seconds")
    private Integer cooldownSeconds;

    @TableField("chain_trigger_enabled")
    private Integer chainTriggerEnabled;

    @TableField("chain_next_rule_ids")
    private String chainNextRuleIds;

    @TableField("chain_disable_self")
    private Integer chainDisableSelf;

    @TableField("change_summary")
    private String changeSummary;

    @TableField("comment")
    private String comment;

    @TableField("create_by")
    private String createBy;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
