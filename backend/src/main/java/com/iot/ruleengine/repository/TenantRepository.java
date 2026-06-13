package com.iot.ruleengine.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantRepository extends BaseMapper<Tenant> {}
