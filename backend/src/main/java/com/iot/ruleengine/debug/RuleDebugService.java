package com.iot.ruleengine.debug;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.iot.ruleengine.dto.DebugRequest;
import com.iot.ruleengine.dto.DebugSessionStatus;
import com.iot.ruleengine.drools.DeviceData;
import com.iot.ruleengine.engine.ExpressionContext;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.repository.RuleRepository;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RuleDebugService {

    private final ConcurrentHashMap<String, DebugSession> sessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Thread> sessionThreads = new ConcurrentHashMap<>();

    private final RuleRepository ruleRepository;
    private final AviatorEvaluatorInstance aviatorEvaluator;

    private static final int DEFAULT_MAX_WAIT_SECONDS = 120;
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000;

    @Autowired
    public RuleDebugService(RuleRepository ruleRepository,
                            @Qualifier("ruleEngineAviatorEvaluator") AviatorEvaluatorInstance aviatorEvaluator) {
        this.ruleRepository = ruleRepository;
        this.aviatorEvaluator = aviatorEvaluator;
    }

    public DebugSessionStatus startDebugSession(DebugRequest request) {
        log.info("启动调试会话: ruleId={}, breakpoints={}, singleStep={}",
                request.getRuleId(), request.getBreakpointNodeIds(), request.isSingleStepMode());

        Rule rule = ruleRepository.selectById(request.getRuleId());
        if (rule == null) {
            throw new IllegalArgumentException("规则不存在: " + request.getRuleId());
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");

        DeviceData inputData = DeviceData.builder()
                .deviceId(request.getDeviceId() != null ? request.getDeviceId() : "debug_device_001")
                .deviceName("调试设备")
                .deviceType("sensor_temp")
                .temperature(request.getTemperature())
                .humidity(request.getHumidity())
                .status(request.getStatus())
                .online(request.getOnline() != null ? request.getOnline() : true)
                .timestamp(LocalDateTime.now())
                .attributes(new LinkedHashMap<>())
                .build();

        DebugSession session = DebugSession.builder()
                .sessionId(sessionId)
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .breakpointNodeIds(request.getBreakpointNodeIds() != null
                        ? request.getBreakpointNodeIds() : new HashSet<>())
                .inputData(inputData)
                .currentContext(inputData)
                .state(DebugSession.DebugState.WAITING)
                .singleStepMode(request.isSingleStepMode())
                .executionSteps(new ArrayList<>())
                .maxWaitSeconds(DEFAULT_MAX_WAIT_SECONDS)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();

        sessionMap.put(sessionId, session);

        Thread debugThread = new Thread(() -> runDebugSession(sessionId, session, rule));
        debugThread.setName("debug-session-" + sessionId.substring(0, 8));
        debugThread.setDaemon(true);
        debugThread.start();
        sessionThreads.put(sessionId, debugThread);

        return buildStatus(session);
    }

    private void runDebugSession(String sessionId, DebugSession session, Rule rule) {
        try {
            session.setState(DebugSession.DebugState.RUNNING);
            session.setLastActiveAt(LocalDateTime.now());

            String ruleJson = rule.getRuleJson();
            if (ruleJson == null || ruleJson.isEmpty()) {
                session.error("规则画布为空");
                return;
            }

            JSONObject canvasJson = JSON.parseObject(ruleJson);
            JSONArray nodes = canvasJson.getJSONArray("nodes");
            JSONArray edges = canvasJson.getJSONArray("edges");

            if (nodes == null || nodes.isEmpty()) {
                session.error("规则画布无节点");
                return;
            }

            List<GraphNode> nodeList = parseNodes(nodes);
            List<GraphEdge> edgeList = parseEdges(edges);
            Map<String, GraphNode> nodeMap = nodeList.stream()
                    .collect(Collectors.toMap(GraphNode::getId, n -> n));

            List<String> executionOrder = determineExecutionOrder(nodeList, edgeList);
            log.debug("调试执行顺序: {}", executionOrder);

            DeviceData context = new DeviceData(session.getInputData());
            session.setCurrentContext(context);

            Map<String, Object> envMap = buildEnvMap(context);

            boolean finalResult = true;

            for (String nodeId : executionOrder) {
                if (session.getState() == DebugSession.DebugState.STOPPED) {
                    log.info("调试会话已停止: {}", sessionId);
                    break;
                }

                GraphNode node = nodeMap.get(nodeId);
                if (node == null) {
                    continue;
                }

                String nodeType = node.getType();
                if (!"CONDITION".equals(nodeType) && !"ACTION".equals(nodeType)
                        && !"THRESHOLD".equals(nodeType) && !"TIME_RANGE".equals(nodeType)
                        && !"OPERATOR".equals(nodeType) && !"TRIGGER".equals(nodeType)) {
                    continue;
                }

                if (session.shouldPause(nodeId)) {
                    session.pause();
                    log.info("调试暂停在节点: {} ({})", nodeId, node.getLabel());
                    boolean canContinue = session.waitForStepOrResume();
                    if (!canContinue || session.getState() == DebugSession.DebugState.STOPPED) {
                        log.info("调试会话终止: {}", sessionId);
                        break;
                    }
                }

                DebugSession.DebugStep step = evaluateNode(session, node, context, envMap);
                session.addExecutionStep(step);

                if ("CONDITION".equals(nodeType) || "THRESHOLD".equals(nodeType)
                        || "TIME_RANGE".equals(nodeType) || "OPERATOR".equals(nodeType)) {
                    if (!step.isConditionResult()) {
                        finalResult = false;
                        step.setMessage("条件不满足，链路终止");
                    }
                }

                session.setCurrentContext(context);
                session.setLastActiveAt(LocalDateTime.now());
            }

            if (session.getState() != DebugSession.DebugState.STOPPED
                    && session.getState() != DebugSession.DebugState.ERROR) {
                session.complete();
                log.info("调试会话完成: {}, 最终结果: {}", sessionId, finalResult);
            }

        } catch (InterruptedException e) {
            log.info("调试会话被中断: {}", sessionId);
            session.setState(DebugSession.DebugState.STOPPED);
        } catch (Exception e) {
            log.error("调试会话异常: {}", sessionId, e);
            session.error("调试异常: " + e.getMessage());
        } finally {
            sessionThreads.remove(sessionId);
        }
    }

    private DebugSession.DebugStep evaluateNode(DebugSession session, GraphNode node,
                                                DeviceData context, Map<String, Object> envMap) {
        int stepIndex = session.incrementAndGetStepIndex();
        JSONObject props = node.getProperties();

        String condition = null;
        Object actualValue = null;
        boolean result = true;
        String message = null;

        String nodeType = node.getType();

        if ("CONDITION".equals(nodeType)) {
            condition = props != null ? props.getString("expression") : null;
            if (condition == null || condition.isEmpty()) {
                condition = "true";
            }
            try {
                Expression expr = aviatorEvaluator.compile(condition, true);
                Object evalResult = expr.execute(envMap);
                result = Boolean.TRUE.equals(evalResult);
                actualValue = result;
                message = result ? "条件满足" : "条件不满足";
            } catch (Exception e) {
                result = false;
                actualValue = null;
                message = "表达式错误: " + e.getMessage();
                log.error("表达式计算失败: {}, error={}", condition, e.getMessage());
            }
        } else if ("THRESHOLD".equals(nodeType)) {
            if (props != null) {
                String field = props.getString("field");
                String operator = props.getString("operator");
                String threshold = props.getString("threshold");
                condition = field + " " + operator + " " + threshold;

                Object fieldValue = envMap.get(field);
                actualValue = fieldValue;

                try {
                    double actual = ((Number) fieldValue).doubleValue();
                    double thresh = Double.parseDouble(threshold);
                    result = compareValues(actual, operator, thresh);
                    message = result ? "阈值条件满足" : "阈值条件不满足";
                } catch (Exception e) {
                    result = false;
                    message = "阈值计算错误: " + e.getMessage();
                }
            }
        } else if ("TIME_RANGE".equals(nodeType)) {
            if (props != null) {
                String startTime = props.getString("startTime");
                String endTime = props.getString("endTime");
                condition = "时间在 " + startTime + " ~ " + endTime + " 之间";

                LocalDateTime now = LocalDateTime.now();
                int nowMinutes = now.getHour() * 60 + now.getMinute();
                int start = parseTimeToMinutes(startTime);
                int end = parseTimeToMinutes(endTime);

                actualValue = now.getHour() + ":" + String.format("%02d", now.getMinute());
                if (start <= end) {
                    result = nowMinutes >= start && nowMinutes <= end;
                } else {
                    result = nowMinutes >= start || nowMinutes <= end;
                }
                message = result ? "在时间范围内" : "不在时间范围内";
            }
        } else if ("OPERATOR".equals(nodeType)) {
            if (props != null) {
                String operator = props.getString("operator");
                condition = operator;
                result = true;
                actualValue = operator;
                message = "逻辑运算符: " + operator;
            }
        } else if ("TRIGGER".equals(nodeType)) {
            if (props != null) {
                String triggerType = props.getString("triggerType");
                condition = "触发方式: " + triggerType;
                result = true;
                actualValue = triggerType;
                message = "触发条件: " + triggerType;
            }
        } else if ("ACTION".equals(nodeType)) {
            if (props != null) {
                String actionType = props.getString("actionType");
                JSONObject actionParams = props.getJSONObject("actionParams");
                condition = "动作: " + actionType;
                result = true;
                actualValue = actionType + (actionParams != null ? " " + actionParams.toJSONString() : "");
                message = "执行动作: " + actionType;
            }
        }

        return DebugSession.DebugStep.builder()
                .stepIndex(stepIndex)
                .nodeId(node.getId())
                .nodeType(nodeType)
                .nodeName(node.getLabel())
                .condition(condition)
                .actualValue(actualValue)
                .conditionResult(result)
                .contextSnapshot(session.getCurrentContextSnapshot())
                .timestamp(LocalDateTime.now())
                .message(message)
                .build();
    }

    private Map<String, Object> buildEnvMap(DeviceData data) {
        Map<String, Object> envMap = ExpressionContext.toEnvMap(data);
        if (data.getAttributes() != null) {
            envMap.putAll(data.getAttributes());
        }
        return envMap;
    }

    private boolean compareValues(double actual, String operator, double threshold) {
        switch (operator) {
            case ">": return actual > threshold;
            case "<": return actual < threshold;
            case ">=": return actual >= threshold;
            case "<=": return actual <= threshold;
            case "==": return actual == threshold;
            case "!=": return actual != threshold;
            default: return false;
        }
    }

    private int parseTimeToMinutes(String timeStr) {
        if (timeStr == null || !timeStr.contains(":")) {
            return 0;
        }
        String[] parts = timeStr.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    public DebugSessionStatus getSessionStatus(String sessionId) {
        DebugSession session = sessionMap.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("调试会话不存在: " + sessionId);
        }
        return buildStatus(session);
    }

    public DebugSessionStatus stepNext(String sessionId) {
        DebugSession session = sessionMap.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("调试会话不存在: " + sessionId);
        }
        session.stepNext();
        return buildStatus(session);
    }

    public DebugSessionStatus resume(String sessionId) {
        DebugSession session = sessionMap.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("调试会话不存在: " + sessionId);
        }
        session.resume();
        return buildStatus(session);
    }

    public DebugSessionStatus pause(String sessionId) {
        DebugSession session = sessionMap.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("调试会话不存在: " + sessionId);
        }
        session.pause();
        return buildStatus(session);
    }

    public DebugSessionStatus stopSession(String sessionId) {
        DebugSession session = sessionMap.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("调试会话不存在: " + sessionId);
        }
        session.stop();
        Thread thread = sessionThreads.get(sessionId);
        if (thread != null) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                thread.interrupt();
            }
        }
        return buildStatus(session);
    }

    private DebugSessionStatus buildStatus(DebugSession session) {
        String pausedNodeId = null;
        if (session.getState() == DebugSession.DebugState.PAUSED
                || session.getState() == DebugSession.DebugState.STEPPING) {
            if (session.getExecutionSteps() != null && !session.getExecutionSteps().isEmpty()) {
                DebugSession.DebugStep lastStep = session.getExecutionSteps()
                        .get(session.getExecutionSteps().size() - 1);
                pausedNodeId = lastStep.getNodeId();
            }
        }

        DebugSession.DebugStep currentStep = null;
        if (session.getExecutionSteps() != null && !session.getExecutionSteps().isEmpty()) {
            currentStep = session.getExecutionSteps()
                    .get(Math.min(session.getCurrentStepIndex(), session.getExecutionSteps().size() - 1));
        }

        String message = null;
        switch (session.getState()) {
            case WAITING: message = "调试会话准备中..."; break;
            case RUNNING: message = "调试运行中..."; break;
            case PAUSED: message = "调试已暂停，点击单步或继续"; break;
            case STEPPING: message = "单步执行中..."; break;
            case COMPLETED: message = "调试完成"; break;
            case ERROR: message = "调试出错"; break;
            case STOPPED: message = "调试已停止"; break;
        }

        return DebugSessionStatus.builder()
                .sessionId(session.getSessionId())
                .ruleId(session.getRuleId())
                .ruleName(session.getRuleName())
                .breakpointNodeIds(session.getBreakpointNodeIds())
                .state(session.getState())
                .singleStepMode(session.isSingleStepMode())
                .currentStepIndex(session.getCurrentStepIndex())
                .totalSteps(session.getExecutionSteps() != null ? session.getExecutionSteps().size() : 0)
                .executionSteps(session.getExecutionSteps())
                .currentStep(currentStep)
                .contextSnapshot(session.getCurrentContextSnapshot())
                .currentPausedNodeId(pausedNodeId)
                .createdAt(session.getCreatedAt())
                .lastActiveAt(session.getLastActiveAt())
                .message(message)
                .build();
    }

    private List<GraphNode> parseNodes(JSONArray nodesJson) {
        List<GraphNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodesJson.size(); i++) {
            JSONObject nodeJson = nodesJson.getJSONObject(i);
            GraphNode node = GraphNode.builder()
                    .id(nodeJson.getString("id"))
                    .type(nodeJson.getString("type"))
                    .label(nodeJson.getString("label"))
                    .properties(nodeJson.getJSONObject("properties"))
                    .x(nodeJson.getDouble("x"))
                    .y(nodeJson.getDouble("y"))
                    .build();
            nodes.add(node);
        }
        return nodes;
    }

    private List<GraphEdge> parseEdges(JSONArray edgesJson) {
        List<GraphEdge> edges = new ArrayList<>();
        if (edgesJson == null) {
            return edges;
        }
        for (int i = 0; i < edgesJson.size(); i++) {
            JSONObject edgeJson = edgesJson.getJSONObject(i);
            GraphEdge edge = GraphEdge.builder()
                    .id(edgeJson.getString("id"))
                    .source(edgeJson.getString("source"))
                    .target(edgeJson.getString("target"))
                    .build();
            edges.add(edge);
        }
        return edges;
    }

    private List<String> determineExecutionOrder(List<GraphNode> nodes, List<GraphEdge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, GraphNode> nodeMap = new HashMap<>();

        for (GraphNode node : nodes) {
            adjacency.put(node.getId(), new ArrayList<>());
            inDegree.put(node.getId(), 0);
            nodeMap.put(node.getId(), node);
        }

        for (GraphEdge edge : edges) {
            adjacency.get(edge.getSource()).add(edge.getTarget());
            inDegree.put(edge.getTarget(), inDegree.getOrDefault(edge.getTarget(), 0) + 1);
        }

        PriorityQueue<Map.Entry<String, GraphNode>> queue = new PriorityQueue<>(
                Comparator.comparingDouble(e -> {
                    GraphNode n = e.getValue();
                    return (n.getY() != null ? n.getY() : 0) * 10000 + (n.getX() != null ? n.getX() : 0);
                })
        );

        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(new AbstractMap.SimpleEntry<>(entry.getKey(), nodeMap.get(entry.getKey())));
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Map.Entry<String, GraphNode> entry = queue.poll();
            String nodeId = entry.getKey();
            result.add(nodeId);

            for (String neighbor : adjacency.get(nodeId)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.offer(new AbstractMap.SimpleEntry<>(neighbor, nodeMap.get(neighbor)));
                }
            }
        }

        return result;
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, DebugSession> entry : sessionMap.entrySet()) {
            DebugSession session = entry.getValue();
            if (session.getLastActiveAt() != null) {
                long lastActive = java.sql.Timestamp.valueOf(session.getLastActiveAt()).getTime();
                if (now - lastActive > SESSION_TIMEOUT_MS) {
                    session.stop();
                    toRemove.add(entry.getKey());
                }
            }
        }
        for (String sessionId : toRemove) {
            sessionMap.remove(sessionId);
            sessionThreads.remove(sessionId);
            log.debug("清理过期调试会话: {}", sessionId);
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GraphNode {
        private String id;
        private String type;
        private String label;
        private JSONObject properties;
        private Double x;
        private Double y;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GraphEdge {
        private String id;
        private String source;
        private String target;
    }
}
