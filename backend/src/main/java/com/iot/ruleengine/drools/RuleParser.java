package com.iot.ruleengine.drools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class RuleParser {

    private static final String IMPORT_STATEMENTS =
            "import com.iot.ruleengine.drools.DeviceData;\n" +
            "import java.util.Map;\n" +
            "import java.util.HashMap;\n\n";

    public String parseToDrl(String ruleId, String ruleName, String jsonRules) {
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

        for (int i = 0; i < edges.size(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            String source = edge.getString("source");
            String target = edge.getString("target");

            adjacencyList.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
            reverseAdjacency.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
        }

        Set<String> startNodes = new HashSet<>();
        for (Node cond : conditionNodes) {
            if (!reverseAdjacency.containsKey(cond.getId())) {
                startNodes.add(cond.getId());
            }
        }

        List<Node> sortedNodes = topologicalSort(nodeMap, adjacencyList);

        String whenClause = buildWhenClause(conditionNodes, adjacencyList, reverseAdjacency, nodeMap, sortedNodes);
        String thenClause = buildThenClause(actionNodes);

        StringBuilder drl = new StringBuilder();
        drl.append("package rules;\n\n");
        drl.append(IMPORT_STATEMENTS);
        drl.append("rule \"").append(ruleName).append("\"\n");
        drl.append("    rule-id \"").append(ruleId).append("\"\n");
        drl.append("    salience 10\n");
        drl.append("    when\n");
        drl.append(whenClause);
        drl.append("    then\n");
        drl.append(thenClause);
        drl.append("end\n");

        log.debug("生成的DRL规则:\n{}", drl.toString());
        return drl.toString();
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

    private String buildWhenClause(List<Node> conditionNodes,
                                   Map<String, List<String>> adjacencyList,
                                   Map<String, List<String>> reverseAdjacency,
                                   Map<String, Node> nodeMap,
                                   List<Node> sortedNodes) {
        if (conditionNodes.isEmpty()) {
            return "        $data : DeviceData()\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("        $data : DeviceData(\n");

        List<String> conditions = new ArrayList<>();
        Map<String, String> nodeConditionMap = new HashMap<>();

        for (Node node : sortedNodes) {
            if ("condition".equals(node.getType())) {
                String condition = buildSingleCondition(node);
                nodeConditionMap.put(node.getId(), condition);
            }
        }

        Set<String> processed = new HashSet<>();
        Set<String> startNodes = new HashSet<>();
        for (Node cond : conditionNodes) {
            if (!reverseAdjacency.containsKey(cond.getId()) || reverseAdjacency.get(cond.getId()).isEmpty()) {
                startNodes.add(cond.getId());
            }
        }

        for (Node node : sortedNodes) {
            if (!"condition".equals(node.getType())) continue;
            if (processed.contains(node.getId())) continue;

            List<String> currentGroupConditions = new ArrayList<>();
            collectConditionGroup(node.getId(), nodeMap, adjacencyList, reverseAdjacency,
                    nodeConditionMap, currentGroupConditions, processed);

            if (!currentGroupConditions.isEmpty()) {
                String joined = String.join(" ", currentGroupConditions);
                conditions.add(joined);
            }
        }

        for (int i = 0; i < conditions.size(); i++) {
            sb.append("            ");
            if (i > 0) {
                sb.append("&& ");
            }
            sb.append(conditions.get(i));
            if (i < conditions.size() - 1) {
                sb.append("\n");
            }
        }

        if (conditions.isEmpty()) {
            for (String cond : nodeConditionMap.values()) {
                conditions.add(cond);
            }
            if (!conditions.isEmpty()) {
                sb.append("            ").append(String.join(" && ", conditions));
            }
        }

        sb.append("\n        )\n");
        return sb.toString();
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
        if (data == null) return "";

        String conditionType = data.getString("conditionType");
        String operator = data.getString("operator");
        String value = data.getString("value");

        if (conditionType == null) return "";

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
        if (value == null) return "";
        String op = mapOperator(operator);
        return "temperature != null && temperature " + op + " " + value;
    }

    private String buildHumidityCondition(String operator, String value) {
        if (value == null) return "";
        String op = mapOperator(operator);
        return "humidity != null && humidity " + op + " " + value;
    }

    private String buildPresenceCondition(String operator, String value) {
        boolean expected = "true".equalsIgnoreCase(value) || "1".equals(value) || "是".equals(value);
        return "presence != null && presence == " + expected;
    }

    private String buildTimeCondition(String operator, String value) {
        if (value == null) return "";

        String[] range = value.split("~");
        if (range.length == 2) {
            String startTime = range[0].trim();
            String endTime = range[1].trim();
            return "time != null && time.compareTo(\"" + startTime + "\") >= 0 && time.compareTo(\"" + endTime + "\") <= 0";
        }

        String op = mapOperator(operator);
        return "time != null && time.compareTo(\"" + value + "\") " + op + " 0";
    }

    private String buildAttributeCondition(String conditionType, String operator, String value) {
        if (value == null) return "";
        String op = mapOperator(operator);
        return "attributes.get(\"" + conditionType + "\") != null && "
                + "((java.lang.Comparable) attributes.get(\"" + conditionType + "\")).compareTo(" + value + ") " + op + " 0";
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

    private String buildThenClause(List<Node> actionNodes) {
        StringBuilder sb = new StringBuilder();

        for (Node actionNode : actionNodes) {
            sb.append(buildSingleAction(actionNode));
        }

        if (sb.length() == 0) {
            sb.append("        System.out.println(\"规则触发，无定义动作\");\n");
        }

        return sb.toString();
    }

    private String buildSingleAction(Node actionNode) {
        JSONObject data = actionNode.getData();
        if (data == null) return "";

        String actionType = data.getString("actionType");
        if (actionType == null) return "";

        StringBuilder sb = new StringBuilder();
        JSONObject params = data.getJSONObject("params");

        sb.append("        ");

        switch (actionType) {
            case "turn_on_aircon":
                sb.append(buildTurnOnAircon(params));
                break;
            case "turn_off_aircon":
                sb.append(buildTurnOffAircon());
                break;
            case "turn_on_light":
                sb.append(buildTurnOnLight(params));
                break;
            case "turn_off_light":
                sb.append(buildTurnOffLight());
                break;
            case "send_alert":
                sb.append(buildSendAlert(params));
                break;
            default:
                sb.append("System.out.println(\"未知动作: ").append(actionType).append("\");\n");
        }

        return sb.toString();
    }

    private String buildTurnOnAircon(JSONObject params) {
        double temp = 26.0;
        String mode = "cool";
        if (params != null) {
            if (params.containsKey("temperature")) {
                temp = params.getDoubleValue("temperature");
            }
            if (params.containsKey("mode")) {
                mode = params.getString("mode");
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("System.out.println(\"[动作] 开启空调 - 设备: \" + $data.getDeviceId()");
        sb.append(" + \", 温度: ").append(temp).append("°C, 模式: ").append(mode).append("\");\n");
        sb.append("        $data.addAttribute(\"aircon_status\", \"on\");\n");
        sb.append("        $data.addAttribute(\"aircon_temperature\", ").append(temp).append(");\n");
        sb.append("        $data.addAttribute(\"aircon_mode\", \"").append(mode).append("\");\n");
        return sb.toString();
    }

    private String buildTurnOffAircon() {
        StringBuilder sb = new StringBuilder();
        sb.append("System.out.println(\"[动作] 关闭空调 - 设备: \" + $data.getDeviceId());\n");
        sb.append("        $data.addAttribute(\"aircon_status\", \"off\");\n");
        return sb.toString();
    }

    private String buildTurnOnLight(JSONObject params) {
        int brightness = 100;
        String color = "white";
        if (params != null) {
            if (params.containsKey("brightness")) {
                brightness = params.getIntValue("brightness");
            }
            if (params.containsKey("color")) {
                color = params.getString("color");
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("System.out.println(\"[动作] 开启灯光 - 设备: \" + $data.getDeviceId()");
        sb.append(" + \", 亮度: ").append(brightness).append("%, 颜色: ").append(color).append("\");\n");
        sb.append("        $data.addAttribute(\"light_status\", \"on\");\n");
        sb.append("        $data.addAttribute(\"light_brightness\", ").append(brightness).append(");\n");
        sb.append("        $data.addAttribute(\"light_color\", \"").append(color).append("\");\n");
        return sb.toString();
    }

    private String buildTurnOffLight() {
        StringBuilder sb = new StringBuilder();
        sb.append("System.out.println(\"[动作] 关闭灯光 - 设备: \" + $data.getDeviceId());\n");
        sb.append("        $data.addAttribute(\"light_status\", \"off\");\n");
        return sb.toString();
    }

    private String buildSendAlert(JSONObject params) {
        String level = "info";
        String message = "规则触发告警";
        if (params != null) {
            if (params.containsKey("level")) {
                level = params.getString("level");
            }
            if (params.containsKey("message")) {
                message = params.getString("message");
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("System.out.println(\"[告警][").append(level).append("] ").append(message);
        sb.append(" - 设备: \" + $data.getDeviceId()");
        sb.append(" + \", 温度: \" + $data.getTemperature()");
        sb.append(" + \", 湿度: \" + $data.getHumidity()");
        sb.append(" + \", 人体存在: \" + $data.getPresence());\n");
        sb.append("        $data.addAttribute(\"alert_level\", \"").append(level).append("\");\n");
        sb.append("        $data.addAttribute(\"alert_message\", \"").append(message).append("\");\n");
        return sb.toString();
    }

    @Data
    public static class Node {
        private String id;
        private String type;
        private JSONObject data;
    }
}
