package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    private String username;
    private String password;
    private String salt;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer status;
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private java.util.List<SysRole> roles;
    @TableField(exist = false)
    private java.util.List<String> permissions;
    @TableField(exist = false)
    private String tenantName;
    @TableField(exist = false)
    private Boolean isAdmin;
}
