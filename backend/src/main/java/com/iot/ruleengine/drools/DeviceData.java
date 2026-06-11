package com.iot.ruleengine.drools;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DeviceData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;

    private Double temperature;

    private Double humidity;

    private Boolean presence;

    private String time;

    private Map<String, Object> attributes = new HashMap<>();

    private List<ActionRequest> pendingActions = new ArrayList<>();

    public DeviceData() {
    }

    public DeviceData(String deviceId) {
        this.deviceId = deviceId;
    }

    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    public void addPendingAction(String actionType, Map<String, Object> params) {
        this.pendingActions.add(new ActionRequest(actionType, params, null));
    }

    public void addPendingAction(String actionType, Map<String, Object> params, String targetDeviceId) {
        this.pendingActions.add(new ActionRequest(actionType, params, targetDeviceId));
    }

    public void addPendingAction(String actionType, Map<String, Object> params, String targetDeviceId, String ruleId, String ruleName) {
        ActionRequest request = new ActionRequest(actionType, params, targetDeviceId);
        request.setRuleId(ruleId);
        request.setRuleName(ruleName);
        this.pendingActions.add(request);
    }

    @Data
    public static class ActionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String actionType;
        private Map<String, Object> params;
        private String targetDeviceId;
        private String ruleId;
        private String ruleName;

        public ActionRequest() {
        }

        public ActionRequest(String actionType, Map<String, Object> params, String targetDeviceId) {
            this.actionType = actionType;
            this.params = params;
            this.targetDeviceId = targetDeviceId;
        }
    }
}
