package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertRecordRepository extends BaseMapper<AlertRecord> {
}
