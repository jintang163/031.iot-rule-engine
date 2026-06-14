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
import com.iot.ruleengine.dto.SandboxTestRequest;
import com.iot.ruleengine.engine.RuleEngine;
import com.iot.ruleengine.engine.RuleEngine.RuleMatchResult;
import com.iot.ruleengine.engine.aviator.AviatorRuleEngine;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.exception.BusinessException;
import com.iot.ruleengine.repository.RuleRepository;
import com.iot.ruleengine.service.RuleService;
import com.iot.ruleengine.service.RuleVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    private final RuleEngine ruleEngine;
    private final RuleVersionService ruleVersionService;

    @Value("${rule.engine:aviator}")
    private String engineType;

    @Autowired(required = false)
    @Qualifier("aviatorRuleEngine")
    private AviatorRuleEngine aviatorRuleEngine;

    @Autowired(required = false)
    @Qualifier("droolsRuleEngine")
    private DroolsRuleEngine droolsRuleEngine;

    @Autowired
    public RuleServiceImpl(RuleRepository ruleRepository, RuleParser ruleParser, RuleEngine ruleEngine,
                            RuleVersionService ruleVersionService) {
        this.ruleRepository = ruleRepository;
        this.ruleParser = ruleParser;
        this.ruleEngine = ruleEngine;
        this.ruleVersionService = ruleVersionService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Rule saveRule(RuleDTO ruleDTO) {
        Rule rule = new Rule();
        BeanUtils.copyProperties(ruleDTO, rule);
        rule.setStatus(0);

        parseAndSetRuleContents(rule, rule.getId() != null ? String.valueOf(rule.getId()) : "temp");

        ruleRepository.insert(rule);

        if (StringUtils.hasText(rule.getRuleJson())) {
            try {
                parseAndSetRuleContents(rule, String.valueOf(rule.getId()));
                ruleRepository.updateById(rule);
            } catch (Exception e) {
                log.error("二次规则解析失败: {}", e.getMessage(), e);
            }
        }

        try {
            String comment = ruleDTO.getVersionComment();
            String changeSummary = ruleDTO.getChangeSummary();
            if (!StringUtils.hasText(changeSummary)) {
                changeSummary = "创建规则";
            }
            ruleVersionService.createVersion(rule, comment, changeSummary);
        } catch (Exception e) {
            log.warn("创建初始版本快照失败: {}", e.getMessage());
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

        parseAndSetRuleContents(existRule, String.valueOf(existRule.getId()));

        existRule.setStatus(0);
        ruleRepository.updateById(existRule);

        if (wasEnabled) {
            ruleEngine.unregisterRule(existRule.getId());
        }

        try {
            String comment = ruleDTO.getVersionComment();
            String changeSummary = ruleDTO.getChangeSummary();
            if (!StringUtils.hasText(changeSummary)) {
                changeSummary = "更新规则";
            }
            ruleVersionService.createVersion(existRule, comment, changeSummary);
        } catch (Exception e) {
            log.warn("创建版本快照失败: {}", e.getMessage());
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
            ruleEngine.unregisterRule(id);
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

        ensureRuleContentsParsed(rule);

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

        boolean registerResult = registerRuleToEngine(rule);
        if (!registerResult) {
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

        ruleEngine.unregisterRule(id);

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

        Long testRuleId = ruleTestDTO.getRuleId();
        Rule rule = null;
        if (testRuleId != null) {
            rule = ruleRepository.selectById(ruleTestDTO.getRuleId());
        }

        if (rule != null) {
            ensureRuleContentsParsed(rule);
            registerRuleToEngine(rule);
        }

        List<RuleMatchResult> results = ruleEngine.evaluate(deviceData, true);

        List<Map<String, Object>> triggeredActions = new ArrayList<>();
        List<DeviceData.ActionRequest> pendingActions = deviceData.getPendingActions();
        if (pendingActions != null && !pendingActions.isEmpty()) {
            for (DeviceData.ActionRequest action : pendingActions) {
                Map<String, Object> actionMap = new HashMap<>();
                actionMap.put("actionType", action.getActionType());
                actionMap.put("params", action.getParams());
                actionMap.put("targetDeviceId", action.getTargetDeviceId() != null
                        ? action.getTargetDeviceId() : deviceData.getDeviceId());
                actionMap.put("ruleId", action.getRuleId());
                actionMap.put("ruleName", action.getRuleName());
                triggeredActions.add(actionMap);
            }
        }

        List<Map<String, Object>> triggeredRules = new ArrayList<>();
        for (RuleMatchResult result : results) {
            Map<String, Object> ruleMap = new HashMap<>();
            ruleMap.put("ruleId", result.getRuleId());
            ruleMap.put("ruleName", result.getRuleName());
            ruleMap.put("triggerTime", result.getTriggerTime() != null ? result.getTriggerTime().toString() : null);
            ruleMap.put("deviceId", result.getDeviceId());
            ruleMap.put("matchedExpression", result.getMatchedExpression());
            ruleMap.put("triggeredActions", result.getTriggeredActions());
            triggeredRules.add(ruleMap);
        }

        Map<String, Object> executionInfo = new HashMap<>();
        executionInfo.put("triggeredRules", triggeredRules);
        executionInfo.put("triggeredActions", triggeredActions);
        executionInfo.put("matchedRuleCount", triggeredRules.size());
        executionInfo.put("actionCount", triggeredActions.size());
        executionInfo.put("deviceData", JSON.toJSON(deviceData));

        List<Map<String, Object>> response = new ArrayList<>();
        response.add(executionInfo);
        return response;
    }

    @Override
    public Map<String, Object> sandboxTest(SandboxTestRequest request) {
        DeviceData deviceData = new DeviceData();
        deviceData.setDeviceId("sandbox_device");
        deviceData.setAttributes(new HashMap<>());

        if (request.getSensorData() != null) {
            for (Map.Entry<String, Object> entry : request.getSensorData().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                switch (key) {
                    case "temperature":
                        if (value instanceof Number) {
                            deviceData.setTemperature(((Number) value).doubleValue());
                        }
                        break;
                    case "humidity":
                        if (value instanceof Number) {
                            deviceData.setHumidity(((Number) value).doubleValue());
                        }
                        break;
                    case "presence":
                        if (value instanceof Boolean) {
                            deviceData.setPresence((Boolean) value);
                        }
                        break;
                    case "time":
                        deviceData.setTime(String.valueOf(value));
                        break;
                    default:
                        deviceData.addAttribute(key, value);
                        break;
                }
            }
        }

        Long sandboxTempRuleId = null;
        boolean registeredViaRuleJson = false;

        if (StringUtils.hasText(request.getRuleJson())) {
            try {
                RuleParser.AviatorParseResult parseResult = ruleParser.parseToAviator(
                        "sandbox_temp",
                        "未保存规则",
                        request.getRuleJson()
                );
                if (parseResult != null && StringUtils.hasText(parseResult.getExpression())) {
                    sandboxTempRuleId = System.currentTimeMillis();
                    Object actionsMeta = parseResult.getActions();
                    ruleEngine.registerRule(sandboxTempRuleId, "未保存规则", parseResult.getExpression(), actionsMeta);
                    registeredViaRuleJson = true;
                }
            } catch (Exception e) {
                log.warn("解析画布ruleJson失败，将尝试使用已保存规则: {}", e.getMessage());
            }
        } else {
            Long ruleId = request.getRuleId();
            if (ruleId != null) {
                Rule rule = ruleRepository.selectById(ruleId);
                if (rule != null) {
                    ensureRuleContentsParsed(rule);
                    registerRuleToEngine(rule);
                }
            }
        }

        List<RuleMatchResult> results = ruleEngine.evaluate(deviceData, true);

        if (registeredViaRuleJson && sandboxTempRuleId != null) {
            ruleEngine.unregisterRule(sandboxTempRuleId);
        }

        List<Map<String, Object>> conditionEvaluations = new ArrayList<>();
        List<Map<String, Object>> simulatedActions = new ArrayList<>();

        for (RuleMatchResult matchResult : results) {
            Map<String, Object> evalMap = new HashMap<>();
            evalMap.put("ruleId", matchResult.getRuleId());
            evalMap.put("ruleName", matchResult.getRuleName());
            evalMap.put("matchedExpression", matchResult.getMatchedExpression());
            evalMap.put("triggered", true);
            evalMap.put("triggerTime", matchResult.getTriggerTime() != null
                    ? matchResult.getTriggerTime().toString() : null);
            conditionEvaluations.add(evalMap);

            if (matchResult.getTriggeredActions() != null) {
                for (DeviceData.ActionRequest action : matchResult.getTriggeredActions()) {
                    Map<String, Object> actionMap = new HashMap<>();
                    actionMap.put("actionType", action.getActionType());
                    actionMap.put("params", action.getParams());
                    actionMap.put("targetDeviceId", action.getTargetDeviceId());
                    actionMap.put("ruleId", action.getRuleId());
                    actionMap.put("ruleName", action.getRuleName());
                    actionMap.put("simulated", true);
                    simulatedActions.add(actionMap);
                }
            }
        }

        Map<String, Object> sandboxResult = new HashMap<>();
        sandboxResult.put("mode", "sandbox");
        sandboxResult.put("inputData", request.getSensorData());
        sandboxResult.put("conditionEvaluations", conditionEvaluations);
        sandboxResult.put("simulatedActions", simulatedActions);
        sandboxResult.put("matchedRuleCount", results.size());
        sandboxResult.put("actionCount", simulatedActions.size());
        sandboxResult.put("usedRuleJson", registeredViaRuleJson);
        sandboxResult.put("summary", results.isEmpty()
                ? "无规则被触发" : results.size() + " 条规则被触发，" + simulatedActions.size() + " 个动作模拟执行");

        return sandboxResult;
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

    private void parseAndSetRuleContents(Rule rule, String ruleId) {
        if (!StringUtils.hasText(rule.getRuleJson())) {
            return;
        }
        try {
            String drlContent = ruleParser.parseToDrl(ruleId, rule.getName(), rule.getRuleJson());
            rule.setDrlContent(drlContent);
        } catch (Exception e) {
            log.error("DRL解析失败: {}", e.getMessage(), e);
            throw new BusinessException("规则JSON解析为DRL失败: " + e.getMessage());
        }

        try {
            RuleParser.AviatorParseResult aviatorResult = ruleParser.parseToAviator(ruleId, rule.getName(), rule.getRuleJson());
            if (aviatorResult != null) {
                rule.setAviatorExpression(aviatorResult.getExpression());
                if (aviatorResult.getActions() != null) {
                    rule.setAviatorActions(JSON.toJSONString(aviatorResult.getActions()));
                }
            }
        } catch (Exception e) {
            log.error("Aviator解析失败: {}", e.getMessage(), e);
            throw new BusinessException("规则JSON解析为Aviator失败: " + e.getMessage());
        }
    }

    private void ensureRuleContentsParsed(Rule rule) {
        boolean needParse = false;

        if (!StringUtils.hasText(rule.getDrlContent())
                || !StringUtils.hasText(rule.getAviatorExpression())) {
            needParse = true;
        }

        if (needParse && StringUtils.hasText(rule.getRuleJson())) {
            parseAndSetRuleContents(rule, String.valueOf(rule.getId()));
            ruleRepository.updateById(rule);
        } else if (!StringUtils.hasText(rule.getRuleJson())) {
            throw new BusinessException("规则内容为空，无法启用");
        }
    }

    private boolean registerRuleToEngine(Rule rule) {
        Long ruleId = rule.getId();
        String ruleName = rule.getName();

        if (isAviatorEngine()) {
            String expression = rule.getAviatorExpression();
            if (!StringUtils.hasText(expression)) {
                log.error("Aviator表达式为空，无法注册规则: ruleId={}", ruleId);
                return false;
            }
            Object actionsMeta = parseAviatorActionsMeta(rule);
            return ruleEngine.registerRule(ruleId, ruleName, expression, actionsMeta);
        } else {
            String drlContent = rule.getDrlContent();
            if (!StringUtils.hasText(drlContent)) {
                log.error("DRL内容为空，无法注册规则: ruleId={}", ruleId);
                return false;
            }
            return ruleEngine.registerRule(ruleId, ruleName, drlContent, null);
        }
    }

    private boolean isAviatorEngine() {
        return ruleEngine instanceof AviatorRuleEngine
                || "aviator".equalsIgnoreCase(engineType);
    }

    @SuppressWarnings("unchecked")
    private Object parseAviatorActionsMeta(Rule rule) {
        if (!StringUtils.hasText(rule.getAviatorActions())) {
            JSONObject defaultMeta = new JSONObject();
            if (rule.getPriority() != null) {
                defaultMeta.put("priority", rule.getPriority());
            }
            return defaultMeta;
        }
        try {
            List<Map<String, Object>> actionList = JSON.parseObject(rule.getAviatorActions(), List.class);
            if (actionList == null || actionList.isEmpty()) {
                JSONObject defaultMeta = new JSONObject();
                if (rule.getPriority() != null) {
                    defaultMeta.put("priority", rule.getPriority());
                }
                return defaultMeta;
            }

            if (actionList.size() == 1) {
                Map<String, Object> single = actionList.get(0);
                JSONObject jsonMeta = new JSONObject();
                jsonMeta.put("actionType", single.get("actionType"));
                jsonMeta.put("actionParams", single.get("params"));
                jsonMeta.put("targetDeviceId", single.get("targetDeviceId"));
                if (rule.getPriority() != null) {
                    jsonMeta.put("priority", rule.getPriority());
                }
                return jsonMeta;
            }

            return actionList;
        } catch (Exception e) {
            log.error("解析Aviator动作定义失败, ruleId: {}", rule.getId(), e);
            JSONObject defaultMeta = new JSONObject();
            if (rule.getPriority() != null) {
                defaultMeta.put("priority", rule.getPriority());
            }
            return defaultMeta;
        }
    }
}
