package com.iot.ruleengine.engine;

import com.iot.ruleengine.drools.DeviceData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ruleId;

    private String ruleName;

    private Integer priority;

    private Boolean matched;

    @Builder.Default
    private List<DeviceData.ActionRequest> triggeredActions = new ArrayList<>();

    private Long evalTimeMs;

    public void addTriggeredAction(DeviceData.ActionRequest action) {
        if (this.triggeredActions == null) {
            this.triggeredActions = new ArrayList<>();
        }
        this.triggeredActions.add(action);
    }
}
