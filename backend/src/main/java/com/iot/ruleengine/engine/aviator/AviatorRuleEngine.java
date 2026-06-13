package com.iot.ruleengine.engine.aviator;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.iot.ruleengine.cep.CooldownService;
import com.iot.ruleengine.cep.RuleChainService;
import com.iot.ruleengine.cep.TimeWindowService;
import com.iot.ruleengine.drools.DeviceData;
import com.iot.ruleengine.engine.ExpressionContext;
import com.iot.ruleengine.engine.RuleEngine;
import com.iot.ruleengine.engine.cache.RuleCompilerCache;
import com.iot.ruleengine.engine.cache.RuleJsonParseCache;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.history.RuleHistoryService;
import com.iot.ruleengine.history.RuleTriggerHistory;
import com.iot.ruleengine.mqtt.DeviceCommandService;
import com.iot.ruleengine.repository.RuleRepository;
import com.iot.ruleengine.stats.RuleStatsService;
import com.iot.ruleengine.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("unchecked")
public class AviatorRuleEngine implements RuleEngine {

    private final ConcurrentHashMap<Long, CompiledRule> ruleMap = new ConcurrentHashMap<>();

    private final AviatorEvaluatorInstance aviatorEvaluator;

    private final RuleCompilerCache ruleCompilerCache;

    private final RuleJsonParseCache ruleJsonParseCache;

    private final RuleRepository ruleRepository;

    private final MeterRegistry meterRegistry;

    private final TimeWindowService timeWindowService;

    private final CooldownService cooldownService;

    private final RuleChainService ruleChainService;

    @Lazy
    @Autowired
    private DeviceCommandService deviceCommandService;

    @Autowired(required = false)
    private RuleStatsService ruleStatsService;

    @Autowired(required = false)
    private RuleHistoryService ruleHistoryService;

    private Counter rulesRegisteredCounter;

    private Timer rulesEvaluationTimer;

    private Counter actionsTriggeredCounter;

    public AviatorRuleEngine(AviatorEvaluatorInstance aviatorEvaluator,
                             RuleCompilerCache ruleCompilerCache,
                             RuleJsonParseCache ruleJsonParseCache,
                             RuleRepository ruleRepository,
                             MeterRegistry meterRegistry,
                             TimeWindowService timeWindowService,
                             CooldownService cooldownService,
                             RuleChainService ruleChainService) {
        this.aviatorEvaluator = aviatorEvaluator;
        this.ruleCompilerCache = ruleCompilerCache;
        this.ruleJsonParseCache = ruleJsonParseCache;
        this.ruleRepository = ruleRepository;
        this.meterRegistry = meterRegistry;
        this.timeWindowService = timeWindowService;
        this.cooldownService = cooldownService;
        this.ruleChainService = ruleChainService;
    }

    @PostConstruct
    public void initMetrics() {
        this.rulesRegisteredCounter = Counter.builder("rules_registered_total")
                .description("已注册规则总数")
                .register(meterRegistry);

        this.rulesEvaluationTimer = Timer.builder("rules_evaluation_seconds")
                .description("规则评估耗时")
                .register(meterRegistry);

        this.actionsTriggeredCounter = Counter.builder("actions_triggered_total")
                .description("已触发动作总数")
                .register(meterRegistry);

        log.info("Aviator规则引擎初始化完成, 使用独立AviatorEvaluatorInstance(线程安全), 已配置Micrometer指标, CEP已集成");
    }

    @Override
    public boolean registerRule(Long ruleId, String ruleName, String expression, Object actionsMeta) {
        try {
            log.info("注册规则, ruleId: {}, ruleName: {}, expression: {}", ruleId, ruleName, expression);

            Expression compiledExpression = ruleCompilerCache.getCompiled(ruleId, expression);
            if (compiledExpression == null) {
                log.error("规则编译失败, ruleId: {}, ruleName: {}", ruleId, ruleName);
                return false;
            }

            List<ActionDefinition> actionDefinitions = parseActionsMeta(actionsMeta);

            CompiledRule compiledRule = CompiledRule.builder()
                    .ruleId(ruleId)
                    .ruleName(ruleName)
                    .compiledExpression(compiledExpression)
                    .actionsMeta(actionDefinitions)
                    .priority(extractPriority(actionsMeta))
                    .rawExpression(expression)
                    .build();

            ruleMap.put(ruleId, compiledRule);
            rulesRegisteredCounter.increment();
            log.info("规则注册成功, ruleId: {}, ruleName: {}, 当前规则数: {}", ruleId, ruleName, ruleMap.size());
            return true;
        } catch (Exception e) {
            log.error("注册规则异常, ruleId: {}, ruleName: {}", ruleId, ruleName, e);
            return false;
        }
    }

    @Override
    public boolean unregisterRule(Long ruleId) {
        log.info("注销规则, ruleId: {}", ruleId);
        CompiledRule removed = ruleMap.remove(ruleId);
        if (removed != null) {
            ruleCompilerCache.invalidate(ruleId);
            ruleJsonParseCache.invalidate(ruleId);
            log.info("规则注销成功, ruleId: {}, ruleName: {}", ruleId, removed.getRuleName());
            return true;
        }
        log.warn("规则不存在, 无需注销, ruleId: {}", ruleId);
        return false;
    }

    @Override
    public List<RuleMatchResult> evaluate(DeviceData data) {
        return evaluate(data, false);
    }

    @Override
    public List<RuleMatchResult> evaluate(DeviceData data, boolean dryRun) {
        List<RuleMatchResult> results = new ArrayList<>();

        if (data == null) {
            return results;
        }

        if (!dryRun) {
            recordDataPointsForTimeWindow(data);
        }

        Map<String, Object> envMap = buildEnvMap(data);

        List<CompiledRule> sortedRules = ruleMap.values().stream()
                .sorted(Comparator.comparingInt(CompiledRule::getPriority).reversed())
                .collect(Collectors.toList());

        for (CompiledRule rule : sortedRules) {
            RuleMatchResult result = evaluateSingleRule(rule, data, envMap, dryRun);
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    private RuleMatchResult evaluateSingleRule(CompiledRule rule, DeviceData data,
                                               Map<String, Object> envMap, boolean dryRun) {
        final Long ruleId = rule.getRuleId();
        final String ruleName = rule.getRuleName();

        long startNanos = System.nanoTime();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Object evalResult = rulesEvaluationTimer.recordCallable(() ->
                    rule.getCompiledExpression().execute(envMap)
            );

            if (Boolean.TRUE.equals(evalResult)) {
                log.info("规则匹配成功, ruleId: {}, ruleName: {}, deviceId: {}", ruleId, ruleName, data.getDeviceId());

                Rule ruleEntity = getRuleById(ruleId);
                if (ruleEntity != null) {
                    if (!evaluateTimeWindow(ruleEntity, data)) {
                        log.info("时间窗口条件未满足，跳过规则: ruleId={}, ruleName={}",
                                ruleId, ruleName);
                        return null;
                    }

                    if (!dryRun) {
                        int cooldownSeconds = ruleEntity.getCooldownSeconds() != null ? ruleEntity.getCooldownSeconds() : 0;
                        if (!cooldownService.canTrigger(ruleId, cooldownSeconds)) {
                            long remaining = cooldownService.getRemainingCooldown(ruleId, cooldownSeconds);
                            log.info("规则处于冷却期，跳过动作执行: ruleId={}, ruleName={}, 剩余{}秒",
                                    ruleId, ruleName, remaining);
                            return null;
                        }
                    }
                }

                List<DeviceData.ActionRequest> triggeredActions = buildActionRequests(rule, data);
                List<DeviceCommandService.ActionExecutionResult> actionResults = Collections.emptyList();

                if (!dryRun && !triggeredActions.isEmpty()) {
                    actionResults = executeActions(triggeredActions, ruleId, ruleName);
                    actionsTriggeredCounter.increment(triggeredActions.size());
                }

                if (!dryRun) {
                    cooldownService.recordTrigger(ruleId);

                    if (ruleEntity != null) {
                        ruleChainService.processRuleChain(ruleEntity);
                    }
                }

                long executionMs = (System.nanoTime() - startNanos) / 1_000_000L;
                if (!dryRun && ruleStatsService != null && ruleId != null) {
                    try {
                        List<String> actionTypes = new ArrayList<>();
                        for (DeviceData.ActionRequest req : triggeredActions) {
                            actionTypes.add(req.getActionType());
                        }
                        ruleStatsService.recordExecution(ruleId, ruleName, executionMs,
                                triggeredActions.size(), actionTypes);
                    } catch (Exception e) {
                        log.warn("记录规则执行统计失败, ruleId={}", ruleId, e);
                    }
                }

                if (!dryRun && ruleHistoryService != null && ruleId != null) {
                    try {
                        recordHistory(ruleId, ruleName, rule.getRawExpression(),
                                data, actionResults, executionMs);
                    } catch (Exception e) {
                        log.warn("记录规则历史轨迹失败, ruleId={}", ruleId, e);
                    }
                }

                return RuleMatchResult.builder()
                        .ruleId(ruleId)
                        .ruleName(ruleName)
                        .triggerTime(LocalDateTime.now())
                        .deviceId(data.getDeviceId())
                        .matchedExpression(rule.getRawExpression())
                        .triggeredActions(triggeredActions)
                        .build();
            }
        } catch (Exception e) {
            log.error("规则评估异常, ruleId: {}, ruleName: {}, deviceId: {}", ruleId, ruleName, data.getDeviceId(), e);
        } finally {
            sample.stop(rulesEvaluationTimer);
        }

        return null;
    }

    private void recordDataPointsForTimeWindow(DeviceData data) {
        if (data == null || timeWindowService == null) {
            return;
        }

        String deviceId = data.getDeviceId();
        if (deviceId == null) {
            return;
        }

        if (data.getTemperature() != null) {
            timeWindowService.recordDataPoint(deviceId, "temperature", data.getTemperature());
        }
        if (data.getHumidity() != null) {
            timeWindowService.recordDataPoint(deviceId, "humidity", data.getHumidity());
        }

        if (data.getAttributes() != null) {
            for (Map.Entry<String, Object> entry : data.getAttributes().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Number) {
                    timeWindowService.recordDataPoint(deviceId, entry.getKey(), ((Number) value).doubleValue());
                }
            }
        }
    }

    private boolean evaluateTimeWindow(Rule ruleEntity, DeviceData data) {
        if (ruleEntity == null || data == null || timeWindowService == null) {
            return true;
        }

        if (ruleEntity.getWindowEnabled() == null || ruleEntity.getWindowEnabled() != 1) {
            return true;
        }

        try {
            TimeWindowService.WindowResult result = timeWindowService.evaluateWindow(ruleEntity, data);
            return result.isConditionMet();
        } catch (Exception e) {
            log.error("时间窗口评估异常: ruleId={}, ruleName={}, error={}",
                    ruleEntity.getId(), ruleEntity.getName(), e.getMessage(), e);
            return false;
        }
    }

    private Map<String, Object> buildEnvMap(DeviceData data) {
        Map<String, Object> envMap = ExpressionContext.toEnvMap(data);

        if (data.getAttributes() != null) {
            for (Map.Entry<String, Object> entry : data.getAttributes().entrySet()) {
                envMap.put(entry.getKey(), entry.getValue());
            }
        }

        return envMap;
    }

    private List<DeviceData.ActionRequest> buildActionRequests(CompiledRule rule, DeviceData data) {
        List<DeviceData.ActionRequest> requests = new ArrayList<>();

        if (rule.getActionsMeta() == null || rule.getActionsMeta().isEmpty()) {
            return requests;
        }

        for (ActionDefinition actionDef : rule.getActionsMeta()) {
            DeviceData.ActionRequest request = new DeviceData.ActionRequest(
                    actionDef.getActionType(),
                    actionDef.getActionParams() != null ? new HashMap<>(actionDef.getActionParams()) : new HashMap<>(),
                    actionDef.getTargetDeviceId() != null ? actionDef.getTargetDeviceId() : data.getDeviceId()
            );
            request.setRuleId(String.valueOf(rule.getRuleId()));
            request.setRuleName(rule.getRuleName());
            requests.add(request);
        }

        return requests;
    }

    private List<DeviceCommandService.ActionExecutionResult> executeActions(
            List<DeviceData.ActionRequest> requests, Long ruleId, String ruleName) {
        List<DeviceCommandService.ActionExecutionResult> results = new ArrayList<>();
        if (deviceCommandService == null) {
            log.warn("DeviceCommandService未初始化，跳过动作执行, ruleId: {}", ruleId);
            for (DeviceData.ActionRequest req : requests) {
                results.add(DeviceCommandService.ActionExecutionResult.builder()
                        .actionType(req.getActionType())
                        .targetDeviceId(req.getTargetDeviceId())
                        .params(req.getParams() != null ? new HashMap<>(req.getParams()) : null)
                        .success(false)
                        .resultCode(0)
                        .errorMsg("DeviceCommandService未初始化")
                        .build());
            }
            return results;
        }

        for (DeviceData.ActionRequest request : requests) {
            try {
                DeviceCommandService.ActionExecutionResult result =
                        deviceCommandService.sendCommandWithResult(
                                request.getTargetDeviceId(),
                                request.getActionType(),
                                request.getParams(),
                                ruleId,
                                ruleName
                        );
                results.add(result);
            } catch (Exception e) {
                log.error("执行动作失败, ruleId: {}, actionType: {}, deviceId: {}",
                        ruleId, request.getActionType(), request.getTargetDeviceId(), e);
                results.add(DeviceCommandService.ActionExecutionResult.builder()
                        .actionType(request.getActionType())
                        .targetDeviceId(request.getTargetDeviceId())
                        .params(request.getParams() != null ? new HashMap<>(request.getParams()) : null)
                        .success(false)
                        .resultCode(0)
                        .errorMsg(e.getMessage())
                        .build());
            }
        }
        return results;
    }

    @Override
    public void reloadAll() {
        log.info("开始重新加载所有规则");

        ruleMap.clear();
        ruleCompilerCache.invalidateAll();
        ruleJsonParseCache.invalidateAll();

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Rule> queryWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("status", 1);

        List<Rule> rules = ruleRepository.selectList(queryWrapper);
        if (rules == null || rules.isEmpty()) {
            log.info("没有找到启用的规则");
            return;
        }

        log.info("找到{}条启用的规则，开始批量注册", rules.size());

        int successCount = 0;
        for (Rule rule : rules) {
            try {
                RuleJsonParseCache.ParsedRule parsedRule = ruleJsonParseCache.parseAndCache(rule);
                if (parsedRule != null) {
                    JSONObject actionsMeta = parsedRule.getActionsMeta();
                    if (actionsMeta == null) {
                        actionsMeta = new JSONObject();
                    }
                    if (rule.getPriority() != null) {
                        actionsMeta.put("priority", rule.getPriority());
                    }
                    boolean registered = registerRule(
                            rule.getId(),
                            rule.getName(),
                            parsedRule.getExpression(),
                            actionsMeta
                    );
                    if (registered) {
                        successCount++;
                    }
                }
            } catch (Exception e) {
                log.error("加载规则失败, ruleId: {}, ruleName: {}", rule.getId(), rule.getName(), e);
            }
        }

        log.info("规则重新加载完成，成功注册{}/{}条规则", successCount, rules.size());
    }

    @Override
    public int getLoadedRuleCount() {
        return ruleMap.size();
    }

    @Override
    public Set<Long> getLoadedRuleIds() {
        return new HashSet<>(ruleMap.keySet());
    }

    private Rule getRuleById(Long ruleId) {
        if (ruleId == null || ruleRepository == null) {
            return null;
        }
        try {
            return ruleRepository.selectById(ruleId);
        } catch (Exception e) {
            log.error("查询规则异常: ruleId={}, error={}", ruleId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<ActionDefinition> parseActionsMeta(Object actionsMeta) {
        List<ActionDefinition> result = new ArrayList<>();

        if (actionsMeta == null) {
            return result;
        }

        if (actionsMeta instanceof JSONObject) {
            JSONObject json = (JSONObject) actionsMeta;
            ActionDefinition actionDef = new ActionDefinition();
            actionDef.setActionType(json.getString("actionType"));
            if (json.containsKey("actionParams")) {
                Object params = json.get("actionParams");
                if (params instanceof Map) {
                    actionDef.setActionParams((Map<String, Object>) params);
                } else if (params != null) {
                    actionDef.setActionParams(JSON.parseObject(params.toString(), Map.class));
                }
            }
            actionDef.setTargetDeviceId(json.getString("targetDeviceId"));
            if (actionDef.getActionType() != null) {
                result.add(actionDef);
            }
        } else if (actionsMeta instanceof List) {
            List<?> list = (List<?>) actionsMeta;
            for (Object item : list) {
                if (item instanceof JSONObject) {
                    JSONObject json = (JSONObject) item;
                    ActionDefinition actionDef = new ActionDefinition();
                    actionDef.setActionType(json.getString("actionType"));
                    if (json.containsKey("actionParams")) {
                        Object params = json.get("actionParams");
                        if (params instanceof Map) {
                            actionDef.setActionParams((Map<String, Object>) params);
                        } else if (params != null) {
                            actionDef.setActionParams(JSON.parseObject(params.toString(), Map.class));
                        }
                    }
                    actionDef.setTargetDeviceId(json.getString("targetDeviceId"));
                    if (actionDef.getActionType() != null) {
                        result.add(actionDef);
                    }
                } else if (item instanceof ActionDefinition) {
                    result.add((ActionDefinition) item);
                }
            }
        } else if (actionsMeta instanceof ActionDefinition) {
            result.add((ActionDefinition) actionsMeta);
        }

        return result;
    }

    private int extractPriority(Object actionsMeta) {
        if (actionsMeta instanceof JSONObject) {
            JSONObject json = (JSONObject) actionsMeta;
            if (json.containsKey("priority")) {
                return json.getIntValue("priority");
            }
        }
        return 0;
    }

    private void recordHistory(Long ruleId, String ruleName, String matchedExpression,
                               DeviceData data, List<DeviceCommandService.ActionExecutionResult> actionResults,
                               long executionMs) {
        if (ruleHistoryService == null) {
            return;
        }
        try {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                tenantId = 1L;
            }

            Map<String, Object> deviceSnapshot = new HashMap<>();
            deviceSnapshot.put("deviceId", data.getDeviceId());
            deviceSnapshot.put("temperature", data.getTemperature());
            deviceSnapshot.put("humidity", data.getHumidity());
            deviceSnapshot.put("timestamp", data.getTimestamp());
            if (data.getAttributes() != null) {
                deviceSnapshot.put("attributes", new HashMap<>(data.getAttributes()));
            }

            List<RuleTriggerHistory.ActionHistoryItem> actionItems = new ArrayList<>();
            if (actionResults != null) {
                for (DeviceCommandService.ActionExecutionResult r : actionResults) {
                    actionItems.add(RuleTriggerHistory.ActionHistoryItem.builder()
                            .actionType(r.getActionType())
                            .targetDeviceId(r.getTargetDeviceId())
                            .params(r.getParams() != null ? new HashMap<>(r.getParams()) : null)
                            .success(r.isSuccess())
                            .resultCode(r.getResultCode())
                            .errorMsg(r.getErrorMsg())
                            .build());
                }
            }

            java.time.format.DateTimeFormatter dtf =
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            String nowStr = LocalDateTime.now().format(dtf);

            RuleTriggerHistory history = RuleTriggerHistory.builder()
                    .tenantId(tenantId)
                    .ruleId(ruleId)
                    .ruleName(ruleName)
                    .engineType("aviator")
                    .triggerTime(nowStr)
                    .deviceId(data.getDeviceId())
                    .temperature(data.getTemperature())
                    .humidity(data.getHumidity())
                    .deviceSnapshot(deviceSnapshot)
                    .matchedExpression(matchedExpression)
                    .actions(actionItems)
                    .executionMs(executionMs)
                    .createTime(nowStr)
                    .build();

            ruleHistoryService.recordHistory(history);
        } catch (Exception e) {
            log.warn("构建规则历史记录失败, ruleId={}", ruleId, e);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompiledRule {
        private Long ruleId;
        private String ruleName;
        private Expression compiledExpression;
        private List<ActionDefinition> actionsMeta;
        private int priority;
        private String rawExpression;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionDefinition {
        private String actionType;
        private Map<String, Object> actionParams;
        private String targetDeviceId;
    }
}
