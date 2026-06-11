package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.DeviceData;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceDataRepository extends BaseMapper<DeviceData> {
}
