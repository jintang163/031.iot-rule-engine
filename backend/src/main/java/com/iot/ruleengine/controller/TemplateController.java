package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.dto.TemplateApplyDTO;
import com.iot.ruleengine.dto.TemplateDTO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.entity.RuleTemplate;
import com.iot.ruleengine.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/template")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TemplateController {

    private final TemplateService templateService;

    @Autowired
    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public Result<RuleTemplate> saveTemplate(@Valid @RequestBody TemplateDTO templateDTO) {
        RuleTemplate template = templateService.saveTemplate(templateDTO);
        return Result.success(template);
    }

    @PutMapping
    public Result<RuleTemplate> updateTemplate(@Valid @RequestBody TemplateDTO templateDTO) {
        RuleTemplate template = templateService.updateTemplate(templateDTO);
        return Result.success(template);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<RuleTemplate> getTemplateById(@PathVariable Long id) {
        RuleTemplate template = templateService.getTemplateById(id);
        return Result.success(template);
    }

    @GetMapping("/list")
    public Result<PageResult<RuleTemplate>> listTemplates(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Integer reviewStatus,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String authorId) {
        Page<RuleTemplate> page = new Page<>(pageNum, pageSize);
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("category", category);
        params.put("scope", scope);
        params.put("sourceType", sourceType);
        params.put("reviewStatus", reviewStatus);
        params.put("status", status);
        params.put("teamId", teamId);
        params.put("authorId", authorId);
        Page<RuleTemplate> result = templateService.listTemplates(page, params);
        return Result.success(PageResult.of(result));
    }

    @PostMapping("/apply")
    public Result<Map<String, Object>> applyTemplate(@Valid @RequestBody TemplateApplyDTO applyDTO) {
        Rule rule = templateService.applyTemplate(applyDTO);
        Map<String, Object> data = new HashMap<>();
        data.put("ruleId", rule.getId());
        data.put("ruleName", rule.getName());
        data.put("status", rule.getStatus());
        data.put("message", rule.getStatus() != null && rule.getStatus() == 1
                ? "模板应用成功，规则已创建并自动启用"
                : "模板应用成功，规则已创建（自动启用失败，请手动启用）");
        return Result.success(data);
    }

    @PutMapping("/{id}/enable")
    public Result<Void> enableTemplate(@PathVariable Long id) {
        templateService.enableTemplate(id);
        return Result.success();
    }

    @PutMapping("/{id}/disable")
    public Result<Void> disableTemplate(@PathVariable Long id) {
        templateService.disableTemplate(id);
        return Result.success();
    }

    @PutMapping("/{id}/review")
    public Result<Void> reviewTemplate(
            @PathVariable Long id,
            @RequestParam Integer reviewStatus,
            @RequestParam(required = false) String reviewerId,
            @RequestParam(required = false) String remark) {
        templateService.reviewTemplate(id, reviewStatus, reviewerId, remark);
        return Result.success();
    }

    @GetMapping("/category/{category}")
    public Result<List<RuleTemplate>> getByCategory(@PathVariable String category) {
        List<RuleTemplate> templates = templateService.getTemplatesByCategory(category);
        return Result.success(templates);
    }

    @PostMapping("/save-from-rule")
    public Result<RuleTemplate> saveRuleAsTemplate(
            @RequestParam Long ruleId,
            @RequestParam String templateName,
            @RequestParam(required = false) String templateDescription,
            @RequestParam(required = false) String authorName,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String authorId) {
        RuleTemplate template = templateService.saveRuleAsTemplate(ruleId, templateName, templateDescription, authorName, teamId, authorId);
        return Result.success(template);
    }
}
