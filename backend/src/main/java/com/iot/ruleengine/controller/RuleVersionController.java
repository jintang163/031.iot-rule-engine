package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.dto.RuleDetailVO;
import com.iot.ruleengine.dto.VersionDiffResult;
import com.iot.ruleengine.dto.VersionRollbackDTO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.entity.RuleVersion;
import com.iot.ruleengine.security.RequirePermission;
import com.iot.ruleengine.service.RuleVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/rule/version")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequirePermission("rule:view")
public class RuleVersionController {

    private final RuleVersionService ruleVersionService;

    @Autowired
    public RuleVersionController(RuleVersionService ruleVersionService) {
        this.ruleVersionService = ruleVersionService;
    }

    @GetMapping("/list/{ruleId}")
    public Result<PageResult<RuleVersion>> listVersions(
            @PathVariable Long ruleId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<RuleVersion> page = new Page<>(pageNum, pageSize);
        Page<RuleVersion> result = ruleVersionService.listVersions(ruleId, page);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/{id}")
    public Result<RuleVersion> getVersionById(@PathVariable Long id) {
        RuleVersion version = ruleVersionService.getVersionById(id);
        return Result.success(version);
    }

    @GetMapping("/rule/{ruleId}/v/{version}")
    public Result<RuleVersion> getVersion(
            @PathVariable Long ruleId,
            @PathVariable Integer version) {
        RuleVersion ruleVersion = ruleVersionService.getVersionByRuleIdAndVersion(ruleId, version);
        return Result.success(ruleVersion);
    }

    @GetMapping("/compare/{ruleId}")
    public Result<VersionDiffResult> compareVersions(
            @PathVariable Long ruleId,
            @RequestParam Integer fromVersion,
            @RequestParam Integer toVersion) {
        VersionDiffResult diff = ruleVersionService.compareVersions(ruleId, fromVersion, toVersion);
        return Result.success(diff);
    }

    @GetMapping("/compare-with-current/{ruleId}")
    public Result<VersionDiffResult> compareWithCurrent(
            @PathVariable Long ruleId,
            @RequestParam Integer version) {
        VersionDiffResult diff = ruleVersionService.compareWithCurrent(ruleId, version);
        return Result.success(diff);
    }

    @PostMapping("/rollback")
    @RequirePermission("rule:edit")
    public Result<RuleDetailVO> rollback(@RequestBody VersionRollbackDTO dto) {
        Rule rule = ruleVersionService.rollback(dto);
        return Result.success(convertToDetailVO(rule));
    }

    @PutMapping("/{versionId}/comment")
    @RequirePermission("rule:edit")
    public Result<RuleVersion> updateComment(
            @PathVariable Long versionId,
            @RequestBody Map<String, String> body) {
        String comment = body.get("comment");
        RuleVersion version = ruleVersionService.updateComment(versionId, comment);
        return Result.success(version);
    }

    @GetMapping("/latest/{ruleId}")
    public Result<List<RuleVersion>> getLatestVersions(
            @PathVariable Long ruleId,
            @RequestParam(defaultValue = "5") int limit) {
        List<RuleVersion> versions = ruleVersionService.getLatestVersions(ruleId, limit);
        return Result.success(versions);
    }

    private RuleDetailVO convertToDetailVO(Rule rule) {
        RuleDetailVO vo = new RuleDetailVO();
        vo.setId(rule.getId());
        vo.setName(rule.getName());
        vo.setDescription(rule.getDescription());
        vo.setStatus(rule.getStatus());
        vo.setPriority(rule.getPriority());
        vo.setMutexGroup(rule.getMutexGroup());
        vo.setRuleJson(rule.getRuleJson());
        vo.setDrlContent(rule.getDrlContent());
        vo.setAviatorExpression(rule.getAviatorExpression() != null ? rule.getAviatorExpression() : "");
        vo.setAviatorActions(rule.getAviatorActions() != null ? rule.getAviatorActions() : "");
        vo.setWindowEnabled(rule.getWindowEnabled());
        vo.setWindowType(rule.getWindowType());
        vo.setWindowDuration(rule.getWindowDuration());
        vo.setWindowAggregation(rule.getWindowAggregation());
        vo.setWindowField(rule.getWindowField());
        vo.setWindowOperator(rule.getWindowOperator());
        vo.setWindowThreshold(rule.getWindowThreshold());
        vo.setCooldownSeconds(rule.getCooldownSeconds());
        vo.setChainTriggerEnabled(rule.getChainTriggerEnabled());
        vo.setChainNextRuleIds(rule.getChainNextRuleIds());
        vo.setChainDisableSelf(rule.getChainDisableSelf());
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());

        return vo;
    }
}
