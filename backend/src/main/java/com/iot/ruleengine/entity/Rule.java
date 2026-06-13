package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule")
public class Rule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

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
    private java.math.BigDecimal windowThreshold;

    @TableField("cooldown_seconds")
    private Integer cooldownSeconds;

    @TableField("chain_trigger_enabled")
    private Integer chainTriggerEnabled;

    @TableField("chain_next_rule_ids")
    private String chainNextRuleIds;

    @TableField("chain_disable_self")
    private Integer chainDisableSelf;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
