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

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
