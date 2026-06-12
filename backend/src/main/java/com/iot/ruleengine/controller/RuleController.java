package com.iot.ruleengine.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.dto.RuleDTO;
import com.iot.ruleengine.dto.RuleDetailVO;
import com.iot.ruleengine.dto.RuleTestDTO;
import com.iot.ruleengine.engine.RuleEngine;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.repository.RuleRepository;
import com.iot.ruleengine.service.RuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/rule")
@CrossOrigin(origins = "*", maxAge = 3600)
public class RuleController {

    private final RuleService ruleService;

    private final RuleEngine ruleEngine;

    private final RuleRepository ruleRepository;

    @Autowired
    public RuleController(RuleService ruleService, RuleEngine ruleEngine, RuleRepository ruleRepository) {
        this.ruleService = ruleService;
        this.ruleEngine = ruleEngine;
        this.ruleRepository = ruleRepository;
    }

    @PostMapping
    public Result<RuleDetailVO> saveRule(@Valid @RequestBody RuleDTO ruleDTO) {
        Rule rule = ruleService.saveRule(ruleDTO);
        return Result.success(convertToDetailVO(rule));
    }

    @PutMapping
    public Result<RuleDetailVO> updateRule(@Valid @RequestBody RuleDTO ruleDTO) {
        Rule rule = ruleService.updateRule(ruleDTO);
        return Result.success(convertToDetailVO(rule));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        ruleService.deleteRule(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<RuleDetailVO> getRuleById(@PathVariable Long id) {
        Rule rule = ruleService.getRuleById(id);
        return Result.success(convertToDetailVO(rule));
    }

    @GetMapping("/list")
    public Result<PageResult<Rule>> listRules(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String mutexGroup) {
        Page<Rule> page = new Page<>(pageNum, pageSize);
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("status", status);
        params.put("mutexGroup", mutexGroup);
        Page<Rule> result = ruleService.listRules(page, params);
        return Result.success(PageResult.of(result));
    }

    @PutMapping("/{id}/enable")
    public Result<Void> enableRule(@PathVariable Long id) {
        ruleService.enableRule(id);
        return Result.success();
    }

    @PutMapping("/{id}/disable")
    public Result<Void> disableRule(@PathVariable Long id) {
        ruleService.disableRule(id);
        return Result.success();
    }

    @PostMapping("/test")
    public Result<List<Map<String, Object>>> testRule(@Valid @RequestBody RuleTestDTO ruleTestDTO) {
        List<Map<String, Object>> result = ruleService.testRule(ruleTestDTO);
        return Result.success(result);
    }

    @GetMapping("/check-mutex")
    public Result<Boolean> checkMutex(@RequestParam String mutexGroup) {
        boolean available = ruleService.checkMutex(mutexGroup);
        return Result.success(available);
    }

    @GetMapping("/internal/enabled-list")
    public Result<List<Map<String, Object>>> getEnabledRulesList() {
        log.info("内部接口：拉取所有启用的规则列表");
        try {
            QueryWrapper<Rule> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", 1);
            List<Rule> enabledRules = ruleRepository.selectList(queryWrapper);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Rule rule : enabledRules) {
                Map<String, Object> ruleMap = new HashMap<>();
                ruleMap.put("id", rule.getId());
                ruleMap.put("name", rule.getName());
                ruleMap.put("expression", rule.getAviatorExpression() != null ? rule.getAviatorExpression() : "");
                ruleMap.put("drlContent", rule.getDrlContent() != null ? rule.getDrlContent() : "");
                ruleMap.put("ruleJson", rule.getRuleJson() != null ? rule.getRuleJson() : "");
                ruleMap.put("priority", rule.getPriority() != null ? rule.getPriority() : 5);
                ruleMap.put("aviatorActions", rule.getAviatorActions() != null ? rule.getAviatorActions() : "");
                result.add(ruleMap);
            }

            log.info("拉取启用规则列表完成，数量: {}", result.size());
            return Result.success(result);
        } catch (Exception e) {
            log.error("拉取启用规则列表失败", e);
            return Result.fail("拉取规则列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/internal/reload")
    public Result<Map<String, Object>> reloadAllRules() {
        log.info("内部接口：触发规则引擎重新加载所有规则");
        try {
            ruleEngine.reloadAll();
            int loadedCount = ruleEngine.getLoadedRuleCount();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("loadedCount", loadedCount);
            result.put("loadedRuleIds", ruleEngine.getLoadedRuleIds());
            result.put("message", "规则重载完成，共加载 " + loadedCount + " 条规则");

            log.info("规则重载完成，数量: {}", loadedCount);
            return Result.success(result);
        } catch (Exception e) {
            log.error("规则重载失败", e);
            return Result.fail("规则重载失败: " + e.getMessage());
        }
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

        Map<String, Object> ruleInfo = new HashMap<>();
        ruleInfo.put("id", rule.getId());
        ruleInfo.put("name", rule.getName());
        ruleInfo.put("description", rule.getDescription());
        ruleInfo.put("status", rule.getStatus());
        ruleInfo.put("priority", rule.getPriority());
        ruleInfo.put("mutexGroup", rule.getMutexGroup());
        vo.setRuleInfo(ruleInfo);

        List<Object> nodes = new ArrayList<>();
        List<Object> edges = new ArrayList<>();

        String ruleJsonStr = rule.getRuleJson();
        if (ruleJsonStr != null && !ruleJsonStr.trim().isEmpty()) {
            try {
                JSONObject jsonObj = JSON.parseObject(ruleJsonStr);
                if (jsonObj.containsKey("nodes")) {
                    JSONArray nodesArray = jsonObj.getJSONArray("nodes");
                    if (nodesArray != null) {
                        nodes.addAll(nodesArray);
                    }
                }
                if (jsonObj.containsKey("edges")) {
                    JSONArray edgesArray = jsonObj.getJSONArray("edges");
                    if (edgesArray != null) {
                        edges.addAll(edgesArray);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        vo.setNodes(nodes);
        vo.setEdges(edges);

        return vo;
    }
}
