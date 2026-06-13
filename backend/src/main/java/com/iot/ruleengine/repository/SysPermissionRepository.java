package com.iot.ruleengine.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysPermissionRepository extends BaseMapper<SysPermission> {}
