package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_role_permission")
public class RolePermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("role_id")
    private Long roleId;
    @TableField("permission_id")
    private Long permissionId;
}
