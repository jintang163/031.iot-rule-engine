package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("role_code")
    private String roleCode;
    @TableField("role_name")
    private String roleName;
    private Integer sort;
    @TableField("data_scope")
    private Integer dataScope;
    @TableField("role_type")
    private Integer roleType;
    private Integer status;
    private String remark;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
