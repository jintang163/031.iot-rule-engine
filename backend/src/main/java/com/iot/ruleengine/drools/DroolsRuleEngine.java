package com.iot.ruleengine.drools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.ruleengine.cep.CooldownService;
import com.iot.ruleengine.cep.RuleChainService;
import com.iot.ruleengine.cep.TimeWindowService;
import com.iot.ruleengine.engine.RuleEngine;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.history.RuleHistoryService;
import com.iot.ruleengine.history.RuleTriggerHistory;
import com.iot.ruleengine.mqtt.DeviceCommandService;
import com.iot.ruleengine.repository.RuleRepository;
import com.iot.ruleengine.stats.RuleStatsService;
import com.iot.ruleengine.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.definition.KiePackage;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class DroolsRuleEngine implements RuleEngine {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KieServices kieServices;

    private final KieContainer kieContainer;

    private final RuleExecutionListener ruleExecutionListener;

    private final RuleParser ruleParser;

    private final TimeWindowService timeWindowService;

    private final CooldownService cooldownService;

    private final RuleChainService ruleChainService;

    private RuleRepository ruleRepository;

    @Lazy
    private DeviceCommandService deviceCommandService;

    @Autowired(required = false)
    private RuleStatsService ruleStatsService;

    @Autowired(required = false)
    private RuleHistoryService ruleHistoryService;

    private volatile KieSession kieSession;

    private final Map<String, String> dynamicRules = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    public DroolsRuleEngine(KieServices kieServices, KieContainer kieContainer,
                            RuleExecutionListener ruleExecutionListener, RuleParser ruleParser,
                            TimeWindowService timeWindowService, CooldownService cooldownService,
                            RuleChainService ruleChainService) {
        this.kieServices = kieServices;
        this.kieContainer = kieContainer;
        this.ruleExecutionListener = ruleExecutionListener;
        this.ruleParser = ruleParser;
        this.timeWindowService = timeWindowService;
        this.cooldownService = cooldownService;
        this.ruleChainService = ruleChainService;
    }

    @Autowired
    public void setRuleRepository(@Lazy RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Autowired
    public void setDeviceCommandService(@Lazy DeviceCommandService deviceCommandService) {
        this.deviceCommandService = deviceCommandService;
    }

    @PostConstruct
    public void init() {
        log.info("初始化Drools规则引擎...");
        createNewKieSession();
        log.info("Drools规则引擎初始化完成");
    }

    private void createNewKieSession() {
        synchronized (lock) {
            if (kieSession != null) {
                try {
                    kieSession.dispose();
                } catch (Exception e) {
                    log.warn("销毁旧KieSession异常: {}", e.getMessage());
                }
            }
            kieSession = kieContainer.newKieSession();
            registerListeners(kieSession);
        }
    }

    private void registerListeners(KieSession session) {
        session.addEventListener((AgendaEventListener) ruleExecutionListener);
    }

    @Override
    public boolean registerRule(Long ruleId, String ruleName, String expression, Object actionsMeta) {
        return compileRule(String.valueOf(ruleId), expression);
    }

    @Override
    public boolean unregisterRule(Long ruleId) {
        return removeRule(String.valueOf(ruleId));
    }

    public boolean compileRule(String ruleId, String drlContent) {
        log.info("开始编译规则: ruleId={}", ruleId);

        if (ruleId == null || drlContent == null || drlContent.trim().isEmpty()) {
            log.error("规则编译失败: ruleId或drlContent为空");
            return false;
        }

        KieHelper kieHelper = new KieHelper();
        try {
            kieHelper.addContent(drlContent, ResourceType.DRL);
            Results results = kieHelper.verify();

            if (results.hasMessages(Message.Level.ERROR)) {
                log.error("规则编译错误:");
                for (Message message : results.getMessages(Message.Level.ERROR)) {
                    log.error("  行{}: {}", message.getLine(), message.getText());
                }
                return false;
            }

            if (results.hasMessages(Message.Level.WARNING)) {
                log.warn("规则编译警告:");
                for (Message message : results.getMessages(Message.Level.WARNING)) {
                    log.warn("  行{}: {}", message.getLine(), message.getText());
                }
            }

            dynamicRules.put(ruleId, drlContent);
            rebuildKieSession();

            log.info("规则编译成功: ruleId={}", ruleId);
            return true;
        } catch (Exception e) {
            log.error("规则编译异常: ruleId={}, error={}", ruleId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<RuleMatchResult> evaluate(DeviceData data) {
        return evaluate(data, false);
    }

    @Override
    public List<RuleMatchResult> evaluate(DeviceData data, boolean dryRun) {
        List<RuleExecutionResult> executionResults = executeRules(data, dryRun);
        return convertToRuleMatchResults(executionResults, data);
    }

    private List<RuleMatchResult> convertToRuleMatchResults(List<RuleExecutionResult> executionResults, DeviceData data) {
        List<RuleMatchResult> results = new ArrayList<>();
        List<DeviceData.ActionRequest> pendingActions = data != null ? data.getPendingActions() : null;

        for (RuleExecutionResult execResult : executionResults) {
            RuleMatchResult.RuleMatchResultBuilder builder = RuleMatchResult.builder()
                    .ruleName(execResult.getRuleName())
                    .deviceId(execResult.getDeviceId())
                    .matchedExpression(execResult.getPackageName());

            try {
                if (execResult.getRuleId() != null) {
                    builder.ruleId(Long.parseLong(execResult.getRuleId()));
                }
            } catch (NumberFormatException e) {
                log.debug("ruleId格式转换失败: {}", execResult.getRuleId());
            }

            try {
                if (execResult.getTriggerTime() != null) {
                    builder.triggerTime(LocalDateTime.parse(execResult.getTriggerTime(), DATE_TIME_FORMATTER));
                }
            } catch (Exception e) {
                log.debug("triggerTime格式转换失败: {}", execResult.getTriggerTime());
            }

            List<DeviceData.ActionRequest> matchedActions = new ArrayList<>();
            if (pendingActions != null) {
                for (DeviceData.ActionRequest action : pendingActions) {
                    boolean ruleIdMatch = (execResult.getRuleId() == null && action.getRuleId() == null)
                            || (execResult.getRuleId() != null && execResult.getRuleId().equals(action.getRuleId()));
                    boolean ruleNameMatch = (execResult.getRuleName() == null && action.getRuleName() == null)
                            || (execResult.getRuleName() != null && execResult.getRuleName().equals(action.getRuleName()));
                    if (ruleIdMatch || ruleNameMatch) {
                        matchedActions.add(action);
                    }
                }
            }
            builder.triggeredActions(matchedActions);
            results.add(builder.build());
        }

        return results;
    }

    public List<RuleExecutionResult> executeRules(DeviceData data) {
        return executeRules(data, false);
    }

    public List<RuleExecutionResult> executeRules(DeviceData data, boolean dryRun) {
        if (data == null) {
            log.warn("执行规则时DeviceData为null");
            return Collections.emptyList();
        }

        log.debug("开始执行规则匹配: deviceId={}, dryRun={}", data.getDeviceId(), dryRun);

        synchronized (lock) {
            if (kieSession == null) {
                createNewKieSession();
            }
        }

        ruleExecutionListener.clearExecutionLogs();

        if (data.getPendingActions() != null) {
            data.getPendingActions().clear();
        }

        List<RuleExecutionResult> results = new ArrayList<>();

        if (!dryRun) {
            recordDataPointsForTimeWindow(data);
        }

        try {
            long droolsEvalStartNanos = System.nanoTime();

            int factCount = kieSession.insert(data);
            log.debug("插入Fact数量: {}", factCount);

            int firedCount = kieSession.fireAllRules();
            log.debug("触发规则数量: firedCount={}", firedCount);

            List<DeviceData.ActionRequest> pendingActions = data.getPendingActions();
            List<RuleExecutionListener.ExecutionLog> logs = ruleExecutionListener.getExecutionLogs();

            long droolsTotalMs = (System.nanoTime() - droolsEvalStartNanos) / 1_000_000L;
            long perRuleMs = logs.size() > 0 ? droolsTotalMs / logs.size() : 1L;

            for (RuleExecutionListener.ExecutionLog logItem : logs) {
                Long ruleId = parseRuleId(logItem.getRuleId());
                Rule rule = ruleId != null ? getRuleById(ruleId) : null;

                if (ruleId != null && rule != null) {
                    boolean timeWindowPassed = evaluateTimeWindow(rule, data);
                    if (!timeWindowPassed) {
                        log.info("时间窗口条件未满足，跳过规则: ruleId={}, ruleName={}",
                                ruleId, rule.getName());
                        removeActionsByRule(pendingActions, logItem.getRuleId(), logItem.getRuleName());
                        continue;
                    }

                    if (!dryRun) {
                        int cooldownSeconds = rule.getCooldownSeconds() != null ? rule.getCooldownSeconds() : 0;
                        if (!cooldownService.canTrigger(ruleId, cooldownSeconds)) {
                            long remaining = cooldownService.getRemainingCooldown(ruleId, cooldownSeconds);
                            log.info("规则处于冷却期，跳过: ruleId={}, ruleName={}, 剩余{}秒",
                                    ruleId, rule.getName(), remaining);
                            removeActionsByRule(pendingActions, logItem.getRuleId(), logItem.getRuleName());
                            continue;
                        }
                    }
                }

                List<DeviceData.ActionRequest> matchedActions = extractActionsByRule(
                        pendingActions, logItem.getRuleId(), logItem.getRuleName());
                List<DeviceCommandService.ActionExecutionResult> actionResults = new ArrayList<>();

                if (!dryRun && !matchedActions.isEmpty()) {
                    for (DeviceData.ActionRequest actionRequest : matchedActions) {
                        try {
                            String targetDeviceId = actionRequest.getTargetDeviceId() != null
                                    ? actionRequest.getTargetDeviceId() : data.getDeviceId();
                            DeviceCommandService.ActionExecutionResult execResult =
                                    deviceCommandService.sendCommandWithResult(
                                            targetDeviceId,
                                            actionRequest.getActionType(),
                                            actionRequest.getParams(),
                                            ruleId,
                                            actionRequest.getRuleName()
                                    );
                            actionResults.add(execResult);
                        } catch (Exception e) {
                            log.error("动作落地失败: actionType={}, targetDeviceId={}, error={}",
                                    actionRequest.getActionType(), actionRequest.getTargetDeviceId(), e.getMessage(), e);
                            actionResults.add(DeviceCommandService.ActionExecutionResult.builder()
                                    .actionType(actionRequest.getActionType())
                                    .targetDeviceId(actionRequest.getTargetDeviceId() != null
                                            ? actionRequest.getTargetDeviceId() : data.getDeviceId())
                                    .params(actionRequest.getParams() != null
                                            ? new HashMap<>(actionRequest.getParams()) : null)
                                    .success(false)
                                    .resultCode(0)
                                    .errorMsg(e.getMessage())
                                    .build());
                        }
                    }
                }

                if (!dryRun && ruleId != null) {
                    cooldownService.recordTrigger(ruleId);
                    if (rule != null) {
                        ruleChainService.processRuleChain(rule);
                    }
                }

                RuleExecutionResult result = new RuleExecutionResult();
                result.setRuleId(logItem.getRuleId());
                result.setRuleName(logItem.getRuleName());
                result.setPackageName(logItem.getPackageName());
                result.setTriggerTime(logItem.getTriggerTime());
                result.setDeviceId(data.getDeviceId());
                result.setTriggeredActions(matchedActions);
                results.add(result);

                if (!dryRun && ruleStatsService != null && ruleId != null) {
                    try {
                        List<String> actionTypes = new ArrayList<>();
                        for (DeviceData.ActionRequest req : matchedActions) {
                            actionTypes.add(req.getActionType());
                        }
                        String statsRuleName = rule != null ? rule.getName() : logItem.getRuleName();
                        ruleStatsService.recordExecution(ruleId, statsRuleName, perRuleMs,
                                matchedActions.size(), actionTypes);
                    } catch (Exception e) {
                        log.warn("记录规则执行统计失败(Drools), ruleId={}", ruleId, e);
                    }
                }

                if (!dryRun && ruleHistoryService != null && ruleId != null) {
                    try {
                        String hRuleName = rule != null ? rule.getName() : logItem.getRuleName();
                        recordHistory(ruleId, hRuleName, logItem.getPackageName(),
                                data, actionResults, perRuleMs);
                    } catch (Exception e) {
                        log.warn("记录规则历史轨迹失败(Drools), ruleId={}", ruleId, e);
                    }
                }
            }

            log.info("规则执行完成: deviceId={}, 触发规则数={}, 动作数={}",
                    data.getDeviceId(), results.size(), pendingActions != null ? pendingActions.size() : 0);
        } catch (Exception e) {
            log.error("规则执行异常: deviceId={}, error={}", data.getDeviceId(), e.getMessage(), e);
        } finally {
            try {
                kieSession.delete(kieSession.getFactHandle(data));
            } catch (Exception e) {
                log.debug("删除Fact异常: {}", e.getMessage());
            }
        }

        return results;
    }

    private void removeActionsByRule(List<DeviceData.ActionRequest> pendingActions,
                                     String ruleId, String ruleName) {
        if (pendingActions == null) {
            return;
        }
        pendingActions.removeIf(action -> {
            boolean ruleIdMatch = (ruleId == null && action.getRuleId() == null)
                    || (ruleId != null && ruleId.equals(action.getRuleId()));
            boolean ruleNameMatch = (ruleName == null && action.getRuleName() == null)
                    || (ruleName != null && ruleName.equals(action.getRuleName()));
            return ruleIdMatch || ruleNameMatch;
        });
    }

    private List<DeviceData.ActionRequest> extractActionsByRule(List<DeviceData.ActionRequest> pendingActions,
                                                                String ruleId, String ruleName) {
        List<DeviceData.ActionRequest> matched = new ArrayList<>();
        if (pendingActions == null) {
            return matched;
        }
        for (DeviceData.ActionRequest action : pendingActions) {
            boolean ruleIdMatch = (ruleId == null && action.getRuleId() == null)
                    || (ruleId != null && ruleId.equals(action.getRuleId()));
            boolean ruleNameMatch = (ruleName == null && action.getRuleName() == null)
                    || (ruleName != null && ruleName.equals(action.getRuleName()));
            if (ruleIdMatch || ruleNameMatch) {
                matched.add(action);
            }
        }
        return matched;
    }

    public boolean removeRule(String ruleId) {
        log.info("移除规则: ruleId={}", ruleId);

        if (!dynamicRules.containsKey(ruleId)) {
            log.warn("要移除的规则不存在: ruleId={}", ruleId);
            return false;
        }

        dynamicRules.remove(ruleId);
        rebuildKieSession();

        log.info("规则移除成功: ruleId={}", ruleId);
        return true;
    }

    @Override
    public void reloadAll() {
        reloadAllRules();
    }

    public void reloadAllRules() {
        log.info("重新加载所有规则(从数据库)...");

        dynamicRules.clear();

        if (ruleRepository != null) {
            try {
                QueryWrapper<Rule> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("status", 1);
                List<Rule> enabledRules = ruleRepository.selectList(queryWrapper);

                log.info("从数据库查询到启用规则数量: {}", enabledRules.size());

                for (Rule rule : enabledRules) {
                    String ruleId = String.valueOf(rule.getId());
                    String drlContent = rule.getDrlContent();

                    if (drlContent == null || drlContent.trim().isEmpty()) {
                        String ruleJson = rule.getRuleJson();
                        if (ruleJson != null && !ruleJson.trim().isEmpty()) {
                            try {
                                drlContent = ruleParser.parseToDrl(ruleId, rule.getName(), ruleJson);
                                log.info("规则JSON解析为DRL成功: ruleId={}, name={}", ruleId, rule.getName());
                            } catch (Exception e) {
                                log.error("规则JSON解析失败: ruleId={}, name={}, error={}",
                                        ruleId, rule.getName(), e.getMessage(), e);
                                continue;
                            }
                        } else {
                            log.warn("规则无有效内容，跳过: ruleId={}, name={}", ruleId, rule.getName());
                            continue;
                        }
                    }

                    if (drlContent != null && !drlContent.trim().isEmpty()) {
                        dynamicRules.put(ruleId, drlContent);
                        log.debug("加载规则: ruleId={}, name={}", ruleId, rule.getName());
                    }
                }
            } catch (Exception e) {
                log.error("从数据库加载规则异常: {}", e.getMessage(), e);
            }
        } else {
            log.warn("RuleRepository未初始化，跳过数据库加载");
        }

        rebuildKieSession();

        log.info("所有规则重新加载完成, 加载成功数量: {}", dynamicRules.size());
    }

    public void reloadAllRules(Map<String, String> rulesFromDb) {
        log.info("从数据库加载所有规则, 数量: {}", rulesFromDb == null ? 0 : rulesFromDb.size());

        dynamicRules.clear();

        if (rulesFromDb != null && !rulesFromDb.isEmpty()) {
            for (Map.Entry<String, String> entry : rulesFromDb.entrySet()) {
                String ruleId = entry.getKey();
                String drlContent = entry.getValue();
                if (drlContent != null && !drlContent.trim().isEmpty()) {
                    dynamicRules.put(ruleId, drlContent);
                }
            }
        }

        rebuildKieSession();

        log.info("从数据库加载规则完成, 成功加载数量: {}", dynamicRules.size());
    }

    private void rebuildKieSession() {
        log.debug("开始重建KieSession, 动态规则数量: {}", dynamicRules.size());

        synchronized (lock) {
            try {
                KieHelper kieHelper = new KieHelper();

                Collection<KiePackage> originalPackages = kieContainer.getKieBase().getKiePackages();
                for (KiePackage kiePackage : originalPackages) {
                    kieHelper.addKiePackage(kiePackage);
                }

                for (Map.Entry<String, String> entry : dynamicRules.entrySet()) {
                    String drlContent = entry.getValue();
                    kieHelper.addContent(drlContent, ResourceType.DRL);
                }

                Results results = kieHelper.verify();
                if (results.hasMessages(Message.Level.ERROR)) {
                    log.error("重建KieSession时编译错误:");
                    for (Message message : results.getMessages(Message.Level.ERROR)) {
                        log.error("  行{}: {}", message.getLine(), message.getText());
                    }
                    return;
                }

                if (kieSession != null) {
                    try {
                        kieSession.dispose();
                    } catch (Exception e) {
                        log.warn("销毁旧KieSession异常: {}", e.getMessage());
                    }
                }

                kieSession = kieHelper.build().newKieSession();
                registerListeners(kieSession);

                log.debug("KieSession重建成功, 动态规则数量: {}", dynamicRules.size());
            } catch (Exception e) {
                log.error("重建KieSession异常: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public Set<Long> getLoadedRuleIds() {
        return dynamicRules.keySet().stream()
                .map(id -> {
                    try {
                        return Long.parseLong(id);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public int getLoadedRuleCount() {
        return dynamicRules.size();
    }

    public Set<String> getLoadedRuleIdStrings() {
        return new HashSet<>(dynamicRules.keySet());
    }

    public String getRuleContent(String ruleId) {
        return dynamicRules.get(ruleId);
    }

    public List<RuleExecutionListener.ExecutionLog> getRecentExecutionLogs() {
        return ruleExecutionListener.getExecutionLogs();
    }

    public void clearExecutionLogs() {
        ruleExecutionListener.clearExecutionLogs();
    }

    @PreDestroy
    public void destroy() {
        log.info("销毁Drools规则引擎...");
        synchronized (lock) {
            if (kieSession != null) {
                try {
                    kieSession.dispose();
                    kieSession = null;
                } catch (Exception e) {
                    log.warn("销毁KieSession异常: {}", e.getMessage());
                }
            }
        }
        dynamicRules.clear();
        log.info("Drools规则引擎销毁完成");
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

    private boolean evaluateTimeWindow(Rule rule, DeviceData data) {
        if (rule == null || data == null || timeWindowService == null) {
            return true;
        }

        if (rule.getWindowEnabled() == null || rule.getWindowEnabled() != 1) {
            return true;
        }

        try {
            TimeWindowService.WindowResult result = timeWindowService.evaluateWindow(rule, data);
            return result.isConditionMet();
        } catch (Exception e) {
            log.error("时间窗口评估异常: ruleId={}, ruleName={}, error={}",
                    rule.getId(), rule.getName(), e.getMessage(), e);
            return false;
        }
    }

    private Long parseRuleId(String ruleIdStr) {
        if (ruleIdStr == null || "null".equalsIgnoreCase(ruleIdStr) || ruleIdStr.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(ruleIdStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
                    .engineType("drools")
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
            log.warn("构建规则历史记录失败(Drools), ruleId={}", ruleId, e);
        }
    }

    public static class RuleExecutionResult {
        private String ruleId;
        private String ruleName;
        private String packageName;
        private String triggerTime;
        private String deviceId;
        private List<DeviceData.ActionRequest> triggeredActions;

        public String getRuleId() {
            return ruleId;
        }

        public void setRuleId(String ruleId) {
            this.ruleId = ruleId;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getTriggerTime() {
            return triggerTime;
        }

        public void setTriggerTime(String triggerTime) {
            this.triggerTime = triggerTime;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public List<DeviceData.ActionRequest> getTriggeredActions() {
            return triggeredActions;
        }

        public void setTriggeredActions(List<DeviceData.ActionRequest> triggeredActions) {
            this.triggeredActions = triggeredActions;
        }
    }
}
