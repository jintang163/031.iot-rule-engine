package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.RuleTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TemplateRepository extends BaseMapper<RuleTemplate> {
}
