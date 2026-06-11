package com.iot.ruleengine.drools;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RuleExecutionListener implements AgendaEventListener, Serializable {

    private static final long serialVersionUID = 1L;

    private final List<ExecutionLog> executionLogs = new ArrayList<>();

    @Override
    public void matchCreated(MatchCreatedEvent event) {
        String ruleName = event.getMatch().getRule().getName();
        log.debug("规则匹配创建: {}", ruleName);
    }

    @Override
    public void matchCancelled(MatchCancelledEvent event) {
        String ruleName = event.getMatch().getRule().getName();
        log.debug("规则匹配取消: {}", ruleName);
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        String ruleName = event.getMatch().getRule().getName();
        log.debug("规则即将触发: {}", ruleName);
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        String ruleName = event.getMatch().getRule().getName();
        String packageName = event.getMatch().getRule().getPackageName();
        List<Object> facts = new ArrayList<>();
        event.getMatch().getObjects().forEach(facts::add);

        ExecutionLog executionLog = new ExecutionLog();
        executionLog.setRuleName(ruleName);
        executionLog.setPackageName(packageName);
        executionLog.setFacts(facts);
        executionLog.setTriggerTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        executionLogs.add(executionLog);

        log.info("规则触发执行 - 规则: {}, 包: {}, 触发时间: {}", ruleName, packageName, executionLog.getTriggerTime());

        for (Object fact : facts) {
            if (fact instanceof DeviceData) {
                DeviceData deviceData = (DeviceData) fact;
                log.info("触发动作的设备: deviceId={}, temperature={}, humidity={}, presence={}, time={}",
                        deviceData.getDeviceId(),
                        deviceData.getTemperature(),
                        deviceData.getHumidity(),
                        deviceData.getPresence(),
                        deviceData.getTime());
            }
        }
    }

    @Override
    public void agendaGroupPopped(AgendaGroupPoppedEvent event) {
        log.debug("AgendaGroup弹出: {}", event.getAgendaGroup().getName());
    }

    @Override
    public void agendaGroupPushed(AgendaGroupPushedEvent event) {
        log.debug("AgendaGroup压入: {}", event.getAgendaGroup().getName());
    }

    @Override
    public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
        log.debug("RuleFlowGroup即将激活: {}", event.getRuleFlowGroup().getName());
    }

    @Override
    public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
        log.debug("RuleFlowGroup已激活: {}", event.getRuleFlowGroup().getName());
    }

    @Override
    public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
        log.debug("RuleFlowGroup即将停用: {}", event.getRuleFlowGroup().getName());
    }

    @Override
    public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
        log.debug("RuleFlowGroup已停用: {}", event.getRuleFlowGroup().getName());
    }

    public List<ExecutionLog> getExecutionLogs() {
        return new ArrayList<>(executionLogs);
    }

    public void clearExecutionLogs() {
        executionLogs.clear();
    }

    @Data
    public static class ExecutionLog implements Serializable {
        private static final long serialVersionUID = 1L;
        private String ruleName;
        private String packageName;
        private List<Object> facts;
        private String triggerTime;
    }
}
