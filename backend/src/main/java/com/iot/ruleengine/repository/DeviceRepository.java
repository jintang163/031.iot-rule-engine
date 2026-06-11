package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.Device;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceRepository extends BaseMapper<Device> {
}
