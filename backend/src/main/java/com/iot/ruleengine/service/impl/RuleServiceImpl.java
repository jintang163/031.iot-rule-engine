package com.iot.ruleengine.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.drools.DeviceData;
import com.iot.ruleengine.drools.DroolsRuleEngine;
import com.iot.ruleengine.drools.RuleParser;
import com.iot.ruleengine.dto.RuleDTO;
import com.iot.ruleengine.dto.RuleTestDTO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.exception.BusinessException;
import com.iot.ruleengine.repository.RuleRepository;
import com.iot.ruleengine.service.RuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RuleServiceImpl implements RuleService {

    private final RuleRepository ruleRepository;
    private final RuleParser ruleParser;
    private final DroolsRuleEngine droolsRuleEngine;

    @Autowired
    public RuleServiceImpl(RuleRepository ruleRepository, RuleParser ruleParser, DroolsRuleEngine droolsRuleEngine) {
        this.ruleRepository = ruleRepository;
        this.ruleParser = ruleParser;
        this.droolsRuleEngine = droolsRuleEngine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Rule saveRule(RuleDTO ruleDTO) {
        Rule rule = new Rule();
        BeanUtils.copyProperties(ruleDTO, rule);
        rule.setStatus(0);

        if (StringUtils.hasText(rule.getRuleJson())) {
            try {
                String drlContent = ruleParser.parseToDrl(
                        rule.getId() != null ? String.valueOf(rule.getId()) : "temp",
                        rule.getName(),
                        rule.getRuleJson()
                );
                rule.setDrlContent(drlContent);
            } catch (Exception e) {
                log.error("规则解析失败: {}", e.getMessage(), e);
                throw new BusinessException("规则JSON解析失败: " + e.getMessage());
            }
        }

        ruleRepository.insert(rule);

        if (StringUtils.hasText(rule.getRuleJson())) {
            try {
                String drlContent = ruleParser.parseToDrl(
                        String.valueOf(rule.getId()),
                        rule.getName(),
                        rule.getRuleJson()
                );
                rule.setDrlContent(drlContent);
                ruleRepository.updateById(rule);
            } catch (Exception e) {
                log.error("规则解析失败: {}", e.getMessage(), e);
            }
        }

        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Rule updateRule(RuleDTO ruleDTO) {
        Rule existRule = ruleRepository.selectById(ruleDTO.getId());
        if (existRule == null) {
            throw new BusinessException("规则不存在");
        }

        boolean wasEnabled = existRule.getStatus() != null && existRule.getStatus() == 1;

        BeanUtils.copyProperties(ruleDTO, existRule);

        if (StringUtils.hasText(existRule.getRuleJson())) {
            try {
                String drlContent = ruleParser.parseToDrl(
                        String.valueOf(existRule.getId()),
                        existRule.getName(),
                        existRule.getRuleJson()
                );
                existRule.setDrlContent(drlContent);
            } catch (Exception e) {
                log.error("规则解析失败: {}", e.getMessage(), e);
                throw new BusinessException("规则JSON解析失败: " + e.getMessage());
            }
        }

        existRule.setStatus(0);
        ruleRepository.updateById(existRule);

        if (wasEnabled) {
            droolsRuleEngine.removeRule(String.valueOf(existRule.getId()));
        }

        return existRule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        Rule existRule = ruleRepository.selectById(id);
        if (existRule == null) {
            throw new BusinessException("规则不存在");
        }

        if (existRule.getStatus() != null && existRule.getStatus() == 1) {
            droolsRuleEngine.removeRule(String.valueOf(id));
        }

        ruleRepository.deleteById(id);
    }

    @Override
    public Rule getRuleById(Long id) {
        Rule rule = ruleRepository.selectById(id);
        if (rule == null) {
            throw new BusinessException("规则不存在");
        }
        return rule;
    }

    @Override
    public Page<Rule> listRules(Page<Rule> page, Map<String, Object> params) {
        QueryWrapper<Rule> queryWrapper = new QueryWrapper<>();

        if (params != null) {
            if (params.containsKey("name") && StringUtils.hasText((String) params.get("name"))) {
                queryWrapper.like("name", params.get("name"));
            }
            if (params.containsKey("status") && params.get("status") != null) {
                queryWrapper.eq("status", params.get("status"));
            }
            if (params.containsKey("mutexGroup") && StringUtils.hasText((String) params.get("mutexGroup"))) {
                queryWrapper.eq("mutex_group", params.get("mutexGroup"));
            }
        }

        queryWrapper.orderByDesc("create_time");
        return ruleRepository.selectPage(page, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableRule(Long id) {
        Rule rule = ruleRepository.selectById(id);
        if (rule == null) {
            throw new BusinessException("规则不存在");
        }

        if (!StringUtils.hasText(rule.getDrlContent())) {
            if (StringUtils.hasText(rule.getRuleJson())) {
                try {
                    String drlContent = ruleParser.parseToDrl(
                            String.valueOf(rule.getId()),
                            rule.getName(),
                            rule.getRuleJson()
                    );
                    rule.setDrlContent(drlContent);
                } catch (Exception e) {
                    log.error("规则解析失败: {}", e.getMessage(), e);
                    throw new BusinessException("规则JSON解析失败: " + e.getMessage());
                }
            } else {
                throw new BusinessException("规则内容为空，无法启用");
            }
        }

        if (StringUtils.hasText(rule.getMutexGroup())) {
            QueryWrapper<Rule> mutexQuery = new QueryWrapper<>();
            mutexQuery.eq("mutex_group", rule.getMutexGroup())
                    .eq("status", 1)
                    .ne("id", id);
            List<Rule> conflictRules = ruleRepository.selectList(mutexQuery);
            if (!conflictRules.isEmpty()) {
                throw new BusinessException("互斥组[" + rule.getMutexGroup() + "]中已有启用的规则");
            }
        }

        boolean compileResult = droolsRuleEngine.compileRule(String.valueOf(id), rule.getDrlContent());
        if (!compileResult) {
            throw new BusinessException("规则编译失败，请检查规则内容");
        }

        rule.setStatus(1);
        ruleRepository.updateById(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableRule(Long id) {
        Rule rule = ruleRepository.selectById(id);
        if (rule == null) {
            throw new BusinessException("规则不存在");
        }

        droolsRuleEngine.removeRule(String.valueOf(id));

        rule.setStatus(0);
        ruleRepository.updateById(rule);
    }

    @Override
    public List<Map<String, Object>> testRule(RuleTestDTO ruleTestDTO) {
        DeviceData deviceData;
        try {
            JSONObject jsonObject = JSON.parseObject(ruleTestDTO.getDeviceData());
            deviceData = new DeviceData();
            if (jsonObject.containsKey("deviceId")) {
                deviceData.setDeviceId(jsonObject.getString("deviceId"));
            }
            if (jsonObject.containsKey("temperature")) {
                deviceData.setTemperature(jsonObject.getDouble("temperature"));
            }
            if (jsonObject.containsKey("humidity")) {
                deviceData.setHumidity(jsonObject.getDouble("humidity"));
            }
            if (jsonObject.containsKey("presence")) {
                deviceData.setPresence(jsonObject.getBoolean("presence"));
            }
            if (jsonObject.containsKey("time")) {
                deviceData.setTime(jsonObject.getString("time"));
            }
            for (String key : jsonObject.keySet()) {
                if (!"deviceId".equals(key) && !"temperature".equals(key)
                        && !"humidity".equals(key) && !"presence".equals(key) && !"time".equals(key)) {
                    deviceData.addAttribute(key, jsonObject.get(key));
                }
            }
        } catch (Exception e) {
            throw new BusinessException("设备数据解析失败: " + e.getMessage());
        }

        String testRuleId = ruleTestDTO.getRuleId() != null ? String.valueOf(ruleTestDTO.getRuleId()) : null;
        Rule rule = null;
        if (testRuleId != null) {
            rule = ruleRepository.selectById(ruleTestDTO.getRuleId());
        }

        if (rule != null && StringUtils.hasText(rule.getDrlContent())) {
            boolean compileResult = droolsRuleEngine.compileRule(testRuleId, rule.getDrlContent());
            if (!compileResult) {
                throw new BusinessException("规则编译失败");
            }
        } else if (rule != null && StringUtils.hasText(rule.getRuleJson())) {
            try {
                String drlContent = ruleParser.parseToDrl(testRuleId, rule.getName(), rule.getRuleJson());
                boolean compileResult = droolsRuleEngine.compileRule(testRuleId, drlContent);
                if (!compileResult) {
                    throw new BusinessException("规则编译失败");
                }
            } catch (Exception e) {
                throw new BusinessException("规则解析失败: " + e.getMessage());
            }
        }

        List<DroolsRuleEngine.RuleExecutionResult> results = droolsRuleEngine.executeRules(deviceData);

        List<Map<String, Object>> triggeredActions = new ArrayList<>();
        Map<String, Object> attributes = deviceData.getAttributes();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_status") || key.endsWith("_temperature") || key.endsWith("_mode")
                    || key.endsWith("_brightness") || key.endsWith("_color")
                    || key.startsWith("alert_")) {
                Map<String, Object> action = new HashMap<>();
                action.put("key", key);
                action.put("value", entry.getValue());
                triggeredActions.add(action);
            }
        }

        Map<String, Object> executionInfo = new HashMap<>();
        executionInfo.put("triggeredRules", results);
        executionInfo.put("triggeredActions", triggeredActions);

        List<Map<String, Object>> response = new ArrayList<>();
        response.add(executionInfo);
        return response;
    }

    @Override
    public boolean checkMutex(String mutexGroup) {
        if (!StringUtils.hasText(mutexGroup)) {
            return true;
        }
        QueryWrapper<Rule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mutex_group", mutexGroup).eq("status", 1);
        List<Rule> rules = ruleRepository.selectList(queryWrapper);
        return rules.isEmpty();
    }
}
