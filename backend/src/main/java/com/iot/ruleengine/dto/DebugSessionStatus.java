package com.iot.ruleengine.dto;

import com.iot.ruleengine.debug.DebugSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebugSessionStatus {

    private String sessionId;
    private Long ruleId;
    private String ruleName;
    private Set<String> breakpointNodeIds;
    private DebugSession.DebugState state;
    private boolean singleStepMode;
    private int currentStepIndex;
    private int totalSteps;
    private List<DebugSession.DebugStep> executionSteps;
    private DebugSession.DebugStep currentStep;
    private Map<String, Object> contextSnapshot;
    private String currentPausedNodeId;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private String message;
}
