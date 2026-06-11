package com.iot.ruleengine.engine;

import com.iot.ruleengine.drools.DeviceData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface RuleEngine {

    boolean registerRule(Long ruleId, String ruleName, String expression, Object actionsMeta);

    boolean unregisterRule(Long ruleId);

    List<RuleMatchResult> evaluate(DeviceData data);

    List<RuleMatchResult> evaluate(DeviceData data, boolean dryRun);

    void reloadAll();

    int getLoadedRuleCount();

    Set<Long> getLoadedRuleIds();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RuleMatchResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long ruleId;

        private String ruleName;

        private LocalDateTime triggerTime;

        private String deviceId;

        private String matchedExpression;

        private List<DeviceData.ActionRequest> triggeredActions;
    }
}
