package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.dto.RuleDTO;
import com.iot.ruleengine.dto.RuleTestDTO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rule")
@CrossOrigin(origins = "*", maxAge = 3600)
public class RuleController {

    private final RuleService ruleService;

    @Autowired
    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public Result<Rule> saveRule(@Valid @RequestBody RuleDTO ruleDTO) {
        Rule rule = ruleService.saveRule(ruleDTO);
        return Result.success(rule);
    }

    @PutMapping
    public Result<Rule> updateRule(@Valid @RequestBody RuleDTO ruleDTO) {
        Rule rule = ruleService.updateRule(ruleDTO);
        return Result.success(rule);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        ruleService.deleteRule(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Rule> getRuleById(@PathVariable Long id) {
        Rule rule = ruleService.getRuleById(id);
        return Result.success(rule);
    }

    @GetMapping("/list")
    public Result<Page<Rule>> listRules(
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
        return Result.success(result);
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
}
