package com.iot.ruleengine.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTriggerHistory {

    private String id;

    private Long tenantId;

    private Long ruleId;

    private String ruleName;

    private String engineType;

    private String triggerTime;

    private String deviceId;

    private Double temperature;

    private Double humidity;

    private Map<String, Object> deviceSnapshot;

    private String matchedExpression;

    private List<ActionHistoryItem> actions;

    private Long executionMs;

    private String createTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionHistoryItem {

        private String actionType;

        private String targetDeviceId;

        private Map<String, Object> params;

        private boolean success;

        private String errorMsg;

        private Integer resultCode;
    }
}
