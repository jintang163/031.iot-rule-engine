package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class Tenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_code")
    private String tenantCode;
    @TableField("tenant_name")
    private String tenantName;
    @TableField("table_prefix")
    private String tablePrefix;
    @TableField("contact_person")
    private String contactPerson;
    @TableField("contact_phone")
    private String contactPhone;
    @TableField("contact_email")
    private String contactEmail;
    @TableField("max_users")
    private Integer maxUsers;
    @TableField("max_devices")
    private Integer maxDevices;
    @TableField("max_rules")
    private Integer maxRules;
    @TableField("expire_time")
    private LocalDateTime expireTime;
    private Integer status;
    private String remark;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
