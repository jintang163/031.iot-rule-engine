package com.iot.ruleengine.drools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.mqtt.DeviceCommandService;
import com.iot.ruleengine.repository.RuleRepository;
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
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DroolsRuleEngine {

    private final KieServices kieServices;

    private final KieContainer kieContainer;

    private final RuleExecutionListener ruleExecutionListener;

    private final RuleParser ruleParser;

    private RuleRepository ruleRepository;

    private RuleRepository ruleRepository2;

    @Lazy
    private DeviceCommandService deviceCommandService;

    private volatile KieSession kieSession;

    private final Map<String, String> dynamicRules = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    @Autowired
    public DroolsRuleEngine(KieServices kieServices, KieContainer kieContainer,
                            RuleExecutionListener ruleExecutionListener, RuleParser ruleParser) {
        this.kieServices = kieServices;
        this.kieContainer = kieContainer;
        this.ruleExecutionListener = ruleExecutionListener;
        this.ruleParser = ruleParser;
    }

    @Autowired
    public void setRuleRepository(@Lazy RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Autowired
    public void setRuleRepository2(@Lazy RuleRepository ruleRepository2) {
        this.ruleRepository2 = ruleRepository2;
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

        try {
            int factCount = kieSession.insert(data);
            log.debug("插入Fact数量: {}", factCount);

            int firedCount = kieSession.fireAllRules();
            log.debug("触发规则数量: firedCount={}", firedCount);

            List<DeviceData.ActionRequest> pendingActions = data.getPendingActions();
            if (pendingActions != null && !pendingActions.isEmpty()) {
                if (dryRun) {
                    log.info("DryRun模式: 跳过{}个MQTT指令下发，仅返回动作列表", pendingActions.size());
                } else if (deviceCommandService != null) {
                    log.info("规则触发完成，开始执行{}个待落地动作", pendingActions.size());
                for (DeviceData.ActionRequest actionRequest : pendingActions) {
                    try {
                        String ruleIdStr = actionRequest.getRuleId();
                        String ruleName = actionRequest.getRuleName();
                        Long ruleId = null;
                        if (ruleIdStr != null && !"null".equalsIgnoreCase(ruleIdStr)) {
                            try {
                                ruleId = Long.parseLong(ruleIdStr);
                            } catch (NumberFormatException e) {
                                log.debug("ruleId格式转换失败: {}", ruleIdStr);
                            }
                        }
                        String targetDeviceId = actionRequest.getTargetDeviceId() != null
                                ? actionRequest.getTargetDeviceId() : data.getDeviceId();
                        deviceCommandService.sendCommand(
                                targetDeviceId,
                                actionRequest.getActionType(),
                                actionRequest.getParams(),
                                ruleId,
                                ruleName
                        );
                    } catch (Exception e) {
                        log.error("动作落地失败: actionType={}, targetDeviceId={}, error={}",
                                actionRequest.getActionType(), actionRequest.getTargetDeviceId(), e.getMessage(), e);
                    }
                }
                log.info("待落地动作执行完成");
                }
            }

            List<RuleExecutionListener.ExecutionLog> logs = ruleExecutionListener.getExecutionLogs();
            for (RuleExecutionListener.ExecutionLog logItem : logs) {
                RuleExecutionResult result = new RuleExecutionResult();
                result.setRuleId(logItem.getRuleId());
                result.setRuleName(logItem.getRuleName());
                result.setPackageName(logItem.getPackageName());
                result.setTriggerTime(logItem.getTriggerTime());
                result.setDeviceId(data.getDeviceId());
                List<DeviceData.ActionRequest> matchedActions = new ArrayList<>();
                if (pendingActions != null) {
                    for (DeviceData.ActionRequest action : pendingActions) {
                        boolean ruleIdMatch = (logItem.getRuleId() == null && action.getRuleId() == null)
                                || (logItem.getRuleId() != null && logItem.getRuleId().equals(action.getRuleId()));
                        boolean ruleNameMatch = (logItem.getRuleName() == null && action.getRuleName() == null)
                                || (logItem.getRuleName() != null && logItem.getRuleName().equals(action.getRuleName()));
                        if (ruleIdMatch || ruleNameMatch) {
                            matchedActions.add(action);
                        }
                    }
                }
                result.setTriggeredActions(matchedActions);
                results.add(result);
            }

            log.info("规则执行完成: deviceId={}, 触发规则数={}, 动作数={}",
                    data.getDeviceId(), results.size(), pendingActions != null ? pendingActions.size() : 0);
        } catch (Exception e) {
            log.error("规则执行异常: deviceId={}, error={}", data.getDeviceId(), e.getMessage(), e);
        } finally {
            kieSession.delete(kieSession.getFactHandle(data));
        }

        return results;
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

    public Set<String> getLoadedRuleIds() {
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
