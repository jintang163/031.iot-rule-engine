package com.iot.ruleengine.engine;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class RuleExpressionParser {

    public ParseResult parseToExpression(String ruleId, String ruleName, String jsonRules) {
        JSONObject jsonObject = JSON.parseObject(jsonRules);
        JSONArray nodes = jsonObject.getJSONArray("nodes");
        JSONArray edges = jsonObject.getJSONArray("edges");

        Map<String, Node> nodeMap = new HashMap<>();
        List<Node> conditionNodes = new ArrayList<>();
        List<Node> actionNodes = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            JSONObject nodeJson = nodes.getJSONObject(i);
            Node node = new Node();
            node.setId(nodeJson.getString("id"));
            node.setType(nodeJson.getString("type"));
            node.setData(nodeJson.getJSONObject("data"));
            nodeMap.put(node.getId(), node);

            if ("condition".equals(node.getType())) {
                conditionNodes.add(node);
            } else if ("action".equals(node.getType())) {
                actionNodes.add(node);
            }
        }

        Map<String, List<String>> adjacencyList = new HashMap<>();
        Map<String, List<String>> reverseAdjacency = new HashMap<>();

        if (edges != null) {
            for (int i = 0; i < edges.size(); i++) {
                JSONObject edge = edges.getJSONObject(i);
                String source = edge.getString("source");
                String target = edge.getString("target");

                adjacencyList.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                reverseAdjacency.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
            }
        }

        List<Node> sortedNodes = topologicalSort(nodeMap, adjacencyList);

        String expression = buildExpression(conditionNodes, adjacencyList, reverseAdjacency, nodeMap, sortedNodes);

        ParseResult result = new ParseResult();
        result.setExpression(expression);
        result.setActionNodes(actionNodes);

        if (!actionNodes.isEmpty()) {
            Node firstAction = actionNodes.get(0);
            JSONObject actionData = firstAction.getData();
            if (actionData != null) {
                result.setActionType(actionData.getString("actionType"));
                result.setTargetDeviceId(actionData.containsKey("deviceId") ? actionData.getString("deviceId") : null);
                result.setActionParams(buildActionParams(actionData.getJSONObject("params")));
            }
        }

        log.debug("解析结果 - ruleId: {}, expression: {}, actionType: {}", ruleId, expression, result.getActionType());
        return result;
    }

    private Map<String, Object> buildActionParams(JSONObject params) {
        Map<String, Object> paramMap = new HashMap<>();
        if (params == null || params.isEmpty()) {
            return paramMap;
        }
        for (String key : params.keySet()) {
            paramMap.put(key, params.get(key));
        }
        return paramMap;
    }

    private List<Node> topologicalSort(Map<String, Node> nodeMap, Map<String, List<String>> adjacencyList) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nodeId : nodeMap.keySet()) {
            inDegree.put(nodeId, 0);
        }
        for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
            for (String target : entry.getValue()) {
                inDegree.put(target, inDegree.getOrDefault(target, 0) + 1);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<Node> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            Node node = nodeMap.get(nodeId);
            if (node != null) {
                result.add(node);
            }
            List<String> neighbors = adjacencyList.getOrDefault(nodeId, Collections.emptyList());
            for (String neighbor : neighbors) {
                int degree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, degree);
                if (degree == 0) {
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }

    private String buildExpression(List<Node> conditionNodes,
                                   Map<String, List<String>> adjacencyList,
                                   Map<String, List<String>> reverseAdjacency,
                                   Map<String, Node> nodeMap,
                                   List<Node> sortedNodes) {
        if (conditionNodes.isEmpty()) {
            return "true";
        }

        Map<String, String> nodeConditionMap = new HashMap<>();

        for (Node node : sortedNodes) {
            if ("condition".equals(node.getType())) {
                String condition = buildSingleCondition(node);
                nodeConditionMap.put(node.getId(), condition);
            }
        }

        Set<String> processed = new HashSet<>();
        List<String> conditions = new ArrayList<>();

        for (Node node : sortedNodes) {
            if (!"condition".equals(node.getType())) continue;
            if (processed.contains(node.getId())) continue;

            List<String> currentGroupConditions = new ArrayList<>();
            collectConditionGroup(node.getId(), nodeMap, adjacencyList, reverseAdjacency,
                    nodeConditionMap, currentGroupConditions, processed);

            if (!currentGroupConditions.isEmpty()) {
                String joined = String.join(" ", currentGroupConditions);
                if (currentGroupConditions.size() > 1) {
                    conditions.add("(" + joined + ")");
                } else {
                    conditions.add(joined);
                }
            }
        }

        if (conditions.isEmpty()) {
            conditions.addAll(nodeConditionMap.values());
        }

        if (conditions.isEmpty()) {
            return "true";
        }

        return String.join(" && ", conditions);
    }

    private void collectConditionGroup(String startId,
                                       Map<String, Node> nodeMap,
                                       Map<String, List<String>> adjacencyList,
                                       Map<String, List<String>> reverseAdjacency,
                                       Map<String, String> nodeConditionMap,
                                       List<String> result,
                                       Set<String> processed) {
        Node current = nodeMap.get(startId);
        if (current == null || processed.contains(startId)) return;

        processed.add(startId);
        String condition = nodeConditionMap.get(startId);
        if (condition != null) {
            if (result.isEmpty()) {
                result.add(condition);
            } else {
                String logicGate = extractLogicGate(current);
                result.add(mapLogicGate(logicGate) + " " + condition);
            }
        }

        List<String> neighbors = adjacencyList.getOrDefault(startId, Collections.emptyList());
        for (String neighborId : neighbors) {
            Node neighbor = nodeMap.get(neighborId);
            if (neighbor != null && "condition".equals(neighbor.getType()) && !processed.contains(neighborId)) {
                collectConditionGroup(neighborId, nodeMap, adjacencyList, reverseAdjacency,
                        nodeConditionMap, result, processed);
            }
        }
    }

    private String extractLogicGate(Node node) {
        JSONObject data = node.getData();
        if (data != null && data.containsKey("logicGate")) {
            return data.getString("logicGate");
        }
        return "AND";
    }

    private String mapLogicGate(String logicGate) {
        if (logicGate == null) return "&&";
        switch (logicGate.toUpperCase()) {
            case "OR":
            case "或":
                return "||";
            case "AND":
            case "且":
            default:
                return "&&";
        }
    }

    private String buildSingleCondition(Node node) {
        JSONObject data = node.getData();
        if (data == null) return "true";

        String conditionType = data.getString("conditionType");
        String operator = data.getString("operator");
        String value = data.getString("value");

        if (conditionType == null) return "true";

        switch (conditionType) {
            case "temperature":
                return buildTemperatureCondition(operator, value);
            case "humidity":
                return buildHumidityCondition(operator, value);
            case "presence":
                return buildPresenceCondition(operator, value);
            case "time":
                return buildTimeCondition(operator, value);
            default:
                return buildAttributeCondition(conditionType, operator, value);
        }
    }

    private String buildTemperatureCondition(String operator, String value) {
        if (value == null) return "true";
        String op = mapOperator(operator);
        return "temperature != nil && temperature " + op + " " + Double.parseDouble(value);
    }

    private String buildHumidityCondition(String operator, String value) {
        if (value == null) return "true";
        String op = mapOperator(operator);
        return "humidity != nil && humidity " + op + " " + Double.parseDouble(value);
    }

    private String buildPresenceCondition(String operator, String value) {
        boolean expected = "true".equalsIgnoreCase(value) || "1".equals(value) || "是".equals(value);
        return "presence != nil && presence == " + expected;
    }

    private String buildTimeCondition(String operator, String value) {
        if (value == null) return "true";

        String[] range = value.split("~");
        if (range.length == 2) {
            String startTime = range[0].trim();
            String endTime = range[1].trim();
            return "time != nil && string.compareTo(time, \"" + startTime + "\") >= 0 && string.compareTo(time, \"" + endTime + "\") <= 0";
        }

        String op = mapOperator(operator);
        return "time != nil && string.compareTo(time, \"" + value + "\") " + op + " 0";
    }

    private String buildAttributeCondition(String conditionType, String operator, String value) {
        if (value == null) return "true";
        String op = mapOperator(operator);
        return "attributes['" + conditionType + "'] != nil && ((java.lang.Comparable) attributes['" + conditionType + "']).compareTo(" + value + ") " + op + " 0";
    }

    private String mapOperator(String operator) {
        if (operator == null) return "==";
        switch (operator) {
            case ">":
            case "大于":
                return ">";
            case "<":
            case "小于":
                return "<";
            case ">=":
            case "大于等于":
                return ">=";
            case "<=":
            case "小于等于":
                return "<=";
            case "!=":
            case "不等于":
                return "!=";
            case "==":
            case "等于":
            default:
                return "==";
        }
    }

    @Data
    public static class ParseResult {
        private String expression;
        private String actionType;
        private Map<String, Object> actionParams;
        private String targetDeviceId;
        private List<Node> actionNodes;
    }

    @Data
    public static class Node {
        private String id;
        private String type;
        private JSONObject data;
    }
}
