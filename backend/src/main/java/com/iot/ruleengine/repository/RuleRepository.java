package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.Rule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleRepository extends BaseMapper<Rule> {
}
