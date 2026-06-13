package com.iot.ruleengine.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.TemplateApplyDTO;
import com.iot.ruleengine.dto.TemplateDTO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.entity.RuleTemplate;

import java.util.List;
import java.util.Map;

public interface TemplateService {

    RuleTemplate saveTemplate(TemplateDTO templateDTO);

    RuleTemplate updateTemplate(TemplateDTO templateDTO);

    void deleteTemplate(Long id);

    RuleTemplate getTemplateById(Long id);

    Page<RuleTemplate> listTemplates(Page<RuleTemplate> page, Map<String, Object> params);

    Rule applyTemplate(TemplateApplyDTO applyDTO);

    void enableTemplate(Long id);

    void disableTemplate(Long id);

    void reviewTemplate(Long id, Integer reviewStatus, String reviewerId, String remark);

    List<RuleTemplate> getTemplatesByCategory(String category);

    RuleTemplate saveRuleAsTemplate(Long ruleId, String templateName, String templateDescription, String authorName);
}
