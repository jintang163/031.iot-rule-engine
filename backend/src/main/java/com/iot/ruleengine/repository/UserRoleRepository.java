package com.iot.ruleengine.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleRepository extends BaseMapper<UserRole> {}
