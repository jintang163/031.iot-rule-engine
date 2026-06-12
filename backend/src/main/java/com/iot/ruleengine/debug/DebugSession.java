package com.iot.ruleengine.debug;

import com.iot.ruleengine.drools.DeviceData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebugSession {

    private String sessionId;
    private Long ruleId;
    private String ruleName;
    private Set<String> breakpointNodeIds;
    private DeviceData inputData;
    private volatile DebugState state;
    private List<DebugStep> executionSteps;
    private int currentStepIndex;
    private DeviceData currentContext;
    private volatile boolean singleStepMode;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private int maxWaitSeconds;

    private final transient ReentrantLock lock = new ReentrantLock();
    private final transient Condition stepCondition = lock.newCondition();
    private final transient AtomicBoolean paused = new AtomicBoolean(false);
    private final transient AtomicBoolean stopped = new AtomicBoolean(false);
    private final transient CountDownLatch completionLatch = new CountDownLatch(1);
    private final transient AtomicInteger stepCounter = new AtomicInteger(0);

    public enum DebugState {
        WAITING,
        RUNNING,
        PAUSED,
        STEPPING,
        COMPLETED,
        ERROR,
        STOPPED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugStep {
        private int stepIndex;
        private String nodeId;
        private String nodeType;
        private String nodeName;
        private String condition;
        private Object actualValue;
        private boolean conditionResult;
        private Map<String, Object> contextSnapshot;
        private LocalDateTime timestamp;
        private String message;
    }

    public void pause() {
        paused.set(true);
        state = DebugState.PAUSED;
        lastActiveAt = LocalDateTime.now();
    }

    public void resume() {
        lock.lock();
        try {
            paused.set(false);
            state = DebugState.RUNNING;
            stepCondition.signalAll();
        } finally {
            lock.unlock();
        }
        lastActiveAt = LocalDateTime.now();
    }

    public void stepNext() {
        lock.lock();
        try {
            paused.set(false);
            state = DebugState.STEPPING;
            stepCondition.signal();
        } finally {
            lock.unlock();
        }
        lastActiveAt = LocalDateTime.now();
    }

    public void stop() {
        stopped.set(true);
        paused.set(false);
        state = DebugState.STOPPED;
        lock.lock();
        try {
            stepCondition.signalAll();
        } finally {
            lock.unlock();
        }
        completionLatch.countDown();
        lastActiveAt = LocalDateTime.now();
    }

    public boolean waitForStepOrResume() throws InterruptedException {
        if (stopped.get()) {
            return false;
        }
        if (!singleStepMode && !paused.get()) {
            return true;
        }
        lock.lock();
        try {
            if (stopped.get()) {
                return false;
            }
            boolean signaled = stepCondition.await(maxWaitSeconds, TimeUnit.SECONDS);
            if (!signaled || stopped.get()) {
                return false;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void complete() {
        if (state != DebugState.ERROR && state != DebugState.STOPPED) {
            state = DebugState.COMPLETED;
        }
        completionLatch.countDown();
        lastActiveAt = LocalDateTime.now();
    }

    public void error(String message) {
        state = DebugState.ERROR;
        completionLatch.countDown();
        lastActiveAt = LocalDateTime.now();
    }

    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return completionLatch.await(timeout, unit);
    }

    public int incrementAndGetStepIndex() {
        return stepCounter.incrementAndGet();
    }

    public void addExecutionStep(DebugStep step) {
        if (executionSteps == null) {
            executionSteps = new ArrayList<>();
        }
        executionSteps.add(step);
        currentStepIndex = executionSteps.size() - 1;
    }

    public boolean isBreakpoint(String nodeId) {
        return breakpointNodeIds != null && breakpointNodeIds.contains(nodeId);
    }

    public boolean shouldPause(String nodeId) {
        if (stopped.get()) {
            return false;
        }
        if (singleStepMode) {
            return true;
        }
        return isBreakpoint(nodeId);
    }

    public Map<String, Object> getCurrentContextSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (currentContext != null) {
            snapshot.put("deviceId", currentContext.getDeviceId());
            snapshot.put("deviceName", currentContext.getDeviceName());
            snapshot.put("deviceType", currentContext.getDeviceType());
            snapshot.put("temperature", currentContext.getTemperature());
            snapshot.put("humidity", currentContext.getHumidity());
            snapshot.put("status", currentContext.getStatus());
            snapshot.put("online", currentContext.getOnline());
            snapshot.put("battery", currentContext.getBattery());
            if (currentContext.getAttributes() != null) {
                snapshot.putAll(currentContext.getAttributes());
            }
        }
        if (inputData != null && inputData.getAttributes() != null) {
            for (Map.Entry<String, Object> entry : inputData.getAttributes().entrySet()) {
                if (!snapshot.containsKey(entry.getKey())) {
                    snapshot.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return snapshot;
    }
}
