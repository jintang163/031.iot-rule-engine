package com.iot.ruleengine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("parent_id")
    private Long parentId;
    @TableField("perm_code")
    private String permCode;
    @TableField("perm_name")
    private String permName;
    @TableField("perm_type")
    private Integer permType;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField(exist = false)
    private List<SysPermission> children;
}
