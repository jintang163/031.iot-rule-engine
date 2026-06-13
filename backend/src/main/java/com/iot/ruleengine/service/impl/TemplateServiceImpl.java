package com.iot.ruleengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.RuleDTO;
import com.iot.ruleengine.dto.TemplateApplyDTO;
import com.iot.ruleengine.dto.TemplateDTO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.entity.RuleTemplate;
import com.iot.ruleengine.exception.BusinessException;
import com.iot.ruleengine.repository.RuleRepository;
import com.iot.ruleengine.repository.TemplateRepository;
import com.iot.ruleengine.service.RuleService;
import com.iot.ruleengine.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final RuleRepository ruleRepository;
    private final RuleService ruleService;

    @Autowired
    public TemplateServiceImpl(TemplateRepository templateRepository,
                               RuleRepository ruleRepository,
                               RuleService ruleService) {
        this.templateRepository = templateRepository;
        this.ruleRepository = ruleRepository;
        this.ruleService = ruleService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleTemplate saveTemplate(TemplateDTO templateDTO) {
        RuleTemplate template = new RuleTemplate();
        BeanUtils.copyProperties(templateDTO, template);
        if (!StringUtils.hasText(template.getScope())) {
            template.setScope("team");
        }
        if (!StringUtils.hasText(template.getSourceType())) {
            template.setSourceType("user");
        }
        if (!StringUtils.hasText(template.getVersion())) {
            template.setVersion("1.0.0");
        }
        template.setReviewStatus("public".equals(template.getScope()) ? 0 : 1);
        template.setApplyCount(0);
        template.setStatus(1);
        templateRepository.insert(template);
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleTemplate updateTemplate(TemplateDTO templateDTO) {
        RuleTemplate existing = templateRepository.selectById(templateDTO.getId());
        if (existing == null) {
            throw new BusinessException("模板不存在");
        }
        if ("system".equals(existing.getSourceType())) {
            throw new BusinessException("系统内置模板不可修改");
        }
        BeanUtils.copyProperties(templateDTO, existing);
        if ("public".equals(existing.getScope()) && existing.getReviewStatus() != null && existing.getReviewStatus() == 1) {
            existing.setReviewStatus(0);
        }
        templateRepository.updateById(existing);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        RuleTemplate existing = templateRepository.selectById(id);
        if (existing == null) {
            throw new BusinessException("模板不存在");
        }
        if ("system".equals(existing.getSourceType())) {
            throw new BusinessException("系统内置模板不可删除");
        }
        templateRepository.deleteById(id);
    }

    @Override
    public RuleTemplate getTemplateById(Long id) {
        RuleTemplate template = templateRepository.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        return template;
    }

    @Override
    public Page<RuleTemplate> listTemplates(Page<RuleTemplate> page, Map<String, Object> params) {
        QueryWrapper<RuleTemplate> queryWrapper = new QueryWrapper<>();
        if (params != null) {
            if (params.containsKey("category") && StringUtils.hasText((String) params.get("category"))) {
                queryWrapper.eq("category", params.get("category"));
            }
            if (params.containsKey("scope") && StringUtils.hasText((String) params.get("scope"))) {
                queryWrapper.eq("scope", params.get("scope"));
            }
            if (params.containsKey("sourceType") && StringUtils.hasText((String) params.get("sourceType"))) {
                queryWrapper.eq("source_type", params.get("sourceType"));
            }
            if (params.containsKey("reviewStatus") && params.get("reviewStatus") != null) {
                queryWrapper.eq("review_status", params.get("reviewStatus"));
            }
            if (params.containsKey("name") && StringUtils.hasText((String) params.get("name"))) {
                queryWrapper.like("name", params.get("name"));
            }
            if (params.containsKey("status") && params.get("status") != null) {
                queryWrapper.eq("status", params.get("status"));
            }
        }
        queryWrapper.orderByDesc("apply_count");
        queryWrapper.orderByDesc("create_time");
        return templateRepository.selectPage(page, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Rule applyTemplate(TemplateApplyDTO applyDTO) {
        RuleTemplate template = templateRepository.selectById(applyDTO.getTemplateId());
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        if (template.getStatus() != null && template.getStatus() == 0) {
            throw new BusinessException("模板已停用，无法应用");
        }
        if ("public".equals(template.getScope()) && template.getReviewStatus() != null && template.getReviewStatus() != 1) {
            throw new BusinessException("模板未通过审核，无法应用");
        }

        RuleDTO ruleDTO = new RuleDTO();
        ruleDTO.setName(StringUtils.hasText(applyDTO.getRuleName()) ? applyDTO.getRuleName() : template.getName());
        ruleDTO.setDescription(StringUtils.hasText(applyDTO.getRuleDescription()) ? applyDTO.getRuleDescription() : template.getDescription());
        ruleDTO.setRuleJson(template.getRuleJson());
        ruleDTO.setStatus(0);
        ruleDTO.setPriority(5);

        Rule rule = ruleService.saveRule(ruleDTO);

        template.setApplyCount(template.getApplyCount() != null ? template.getApplyCount() + 1 : 1);
        templateRepository.updateById(template);

        log.info("模板[{}]已应用，生成规则[{}]", template.getName(), rule.getId());
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableTemplate(Long id) {
        RuleTemplate template = templateRepository.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        template.setStatus(1);
        templateRepository.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableTemplate(Long id) {
        RuleTemplate template = templateRepository.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        template.setStatus(0);
        templateRepository.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewTemplate(Long id, Integer reviewStatus, String reviewerId, String remark) {
        RuleTemplate template = templateRepository.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        if (reviewStatus == null || (reviewStatus != 1 && reviewStatus != 2)) {
            throw new BusinessException("审核状态值无效");
        }
        template.setReviewStatus(reviewStatus);
        template.setReviewerId(reviewerId);
        template.setReviewTime(LocalDateTime.now());
        template.setReviewRemark(remark);
        templateRepository.updateById(template);
        log.info("模板[{}]审核完成，审核结果: {}", id, reviewStatus == 1 ? "通过" : "拒绝");
    }

    @Override
    public List<RuleTemplate> getTemplatesByCategory(String category) {
        QueryWrapper<RuleTemplate> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category", category);
        queryWrapper.eq("status", 1);
        queryWrapper.eq("review_status", 1);
        queryWrapper.orderByDesc("apply_count");
        return templateRepository.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleTemplate saveRuleAsTemplate(Long ruleId, String templateName, String templateDescription, String authorName) {
        Rule rule = ruleRepository.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("规则不存在");
        }

        RuleTemplate template = new RuleTemplate();
        template.setName(templateName);
        template.setDescription(templateDescription);
        template.setCategory("custom");
        template.setRuleJson(rule.getRuleJson());
        template.setScope("team");
        template.setSourceType("user");
        template.setSourceRuleId(ruleId);
        template.setAuthorName(authorName);
        template.setVersion("1.0.0");
        template.setReviewStatus(1);
        template.setApplyCount(0);
        template.setStatus(1);
        templateRepository.insert(template);

        log.info("规则[{}]已保存为模板[{}]", ruleId, template.getId());
        return template;
    }
}
