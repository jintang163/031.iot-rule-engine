package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("rule_execution_stats")
public class RuleExecutionStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("rule_id")
    private Long ruleId;

    @TableField("rule_name")
    private String ruleName;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("trigger_count")
    private Long triggerCount;

    @TableField("action_count")
    private Long actionCount;

    @TableField("total_execution_ms")
    private Long totalExecutionMs;

    @TableField("max_execution_ms")
    private Long maxExecutionMs;

    @TableField("avg_execution_ms")
    private BigDecimal avgExecutionMs;

    @TableField("estimated_cost_yuan")
    private BigDecimal estimatedCostYuan;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
