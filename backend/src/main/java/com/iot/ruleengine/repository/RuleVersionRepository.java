package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.RuleVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleVersionRepository extends BaseMapper<RuleVersion> {
}
