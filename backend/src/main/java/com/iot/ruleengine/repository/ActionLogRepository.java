package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.ActionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActionLogRepository extends BaseMapper<ActionLog> {
}
