package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.AlertNotifyConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertNotifyConfigRepository extends BaseMapper<AlertNotifyConfig> {
}
