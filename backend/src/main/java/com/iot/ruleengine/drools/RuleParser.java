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

    // ==================== 公共解析数据结构 ====================

    @Data
    public static class ParseContext {
        private Map<String, Node> nodeMap;
        private List<Node> conditionNodes;
        private List<Node> actionNodes;
        private Map<String, List<String>> adjacencyList;
        private Map<String, List<String>> reverseAdjacency;
        private List<Node> sortedNodes;
    }

    @Data
    public static class ConditionGroup {
        private List<String> expressions;
        private List<String> logicGates;
    }

    // ==================== Aviator 结果类 ====================

    @Data
    public static class AviatorParseResult {
        private String expression;
        private List<ActionDef> actions;
        private String ruleName;
        private String ruleId;
    }

    @Data
    public static class ActionDef {
        private String actionType;
        private Map<String, Object> params;
        private String targetDeviceId;
        private int order;
    }

    // ==================== 公共节点类 ====================

    @Data
    public static class Node {
        private String id;
        private String type;
        private JSONObject data;
    }

    // ==================== 公共入口方法 ====================

    public String parseToDrl(String ruleId, String ruleName, String jsonRules) {
        ParseContext ctx = parseJsonAndBuildGraph(jsonRules);

        String whenClause = buildWhenClause(ctx);
        String thenClause = buildThenClause(ctx.getActionNodes());

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

    public AviatorParseResult parseToAviator(String ruleId, String ruleName, String jsonRules) {
        ParseContext ctx = parseJsonAndBuildGraph(jsonRules);

        String expression = buildAviatorExpression(ctx);
        List<ActionDef> actions = buildActionDefList(ctx);

        AviatorParseResult result = new AviatorParseResult();
        result.setExpression(expression);
        result.setActions(actions);
        result.setRuleId(ruleId);
        result.setRuleName(ruleName);

        log.debug("生成的Aviator表达式: {}, 动作数: {}", expression, actions.size());
        return result;
    }

    // ==================== 公共抽取方法 ====================

    private ParseContext parseJsonAndBuildGraph(String jsonRules) {
        JSONObject jsonObject = JSON.parseObject(jsonRules);
        JSONArray nodes = jsonObject.getJSONArray("nodes");
        JSONArray edges = jsonObject.getJSONArray("edges");

        ParseContext ctx = new ParseContext();
        ctx.setNodeMap(new HashMap<>());
        ctx.setConditionNodes(new ArrayList<>());
        ctx.setActionNodes(new ArrayList<>());
        ctx.setAdjacencyList(new HashMap<>());
        ctx.setReverseAdjacency(new HashMap<>());

        extractNodes(nodes, ctx);
        extractEdges(edges, ctx);

        List<Node> sorted = topologicalSort(ctx.getNodeMap(), ctx.getAdjacencyList());
        ctx.setSortedNodes(sorted);

        return ctx;
    }

    private void extractNodes(JSONArray nodes, ParseContext ctx) {
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject nodeJson = nodes.getJSONObject(i);
            Node node = new Node();
            node.setId(nodeJson.getString("id"));
            node.setType(nodeJson.getString("type"));
            node.setData(nodeJson.getJSONObject("data"));
            ctx.getNodeMap().put(node.getId(), node);

            if ("condition".equals(node.getType())) {
                ctx.getConditionNodes().add(node);
            } else if ("action".equals(node.getType())) {
                ctx.getActionNodes().add(node);
            }
        }
    }

    private void extractEdges(JSONArray edges, ParseContext ctx) {
        for (int i = 0; i < edges.size(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            String source = edge.getString("source");
            String target = edge.getString("target");

            ctx.getAdjacencyList().computeIfAbsent(source, k -> new ArrayList<>()).add(target);
            ctx.getReverseAdjacency().computeIfAbsent(target, k -> new ArrayList<>()).add(source);
        }
    }

    public List<Node> extractConditionNodes(ParseContext ctx) {
        return ctx.getConditionNodes();
    }

    public List<Node> extractActionNodes(ParseContext ctx) {
        return ctx.getActionNodes();
    }

    public List<ConditionGroup> buildConditionGroups(ParseContext ctx, boolean isAviator) {
        List<ConditionGroup> groups = new ArrayList<>();
        Map<String, String> nodeExprMap = new HashMap<>();

        for (Node node : ctx.getSortedNodes()) {
            if ("condition".equals(node.getType())) {
                String expr = isAviator ? buildAviatorSingleCondition(node) : buildSingleCondition(node);
                nodeExprMap.put(node.getId(), expr);
            }
        }

        Set<String> processed = new HashSet<>();
        Set<String> startNodes = new HashSet<>();
        for (Node cond : ctx.getConditionNodes()) {
            List<String> rev = ctx.getReverseAdjacency().get(cond.getId());
            if (rev == null || rev.isEmpty()) {
                startNodes.add(cond.getId());
            }
        }

        for (Node node : ctx.getSortedNodes()) {
            if (!"condition".equals(node.getType())) continue;
            if (processed.contains(node.getId())) continue;

            ConditionGroup group = new ConditionGroup();
            group.setExpressions(new ArrayList<>());
            group.setLogicGates(new ArrayList<>());

            collectConditionGroup(node.getId(), ctx.getNodeMap(), ctx.getAdjacencyList(),
                    ctx.getReverseAdjacency(), nodeExprMap, group, processed);

            if (!group.getExpressions().isEmpty()) {
                groups.add(group);
            }
        }

        if (groups.isEmpty() && !nodeExprMap.isEmpty()) {
            ConditionGroup group = new ConditionGroup();
            group.setExpressions(new ArrayList<>(nodeExprMap.values()));
            group.setLogicGates(new ArrayList<>());
            groups.add(group);
        }

        return groups;
    }

    private void collectConditionGroup(String startId,
                                       Map<String, Node> nodeMap,
                                       Map<String, List<String>> adjacencyList,
                                       Map<String, List<String>> reverseAdjacency,
                                       Map<String, String> nodeExprMap,
                                       ConditionGroup group,
                                       Set<String> processed) {
        Node current = nodeMap.get(startId);
        if (current == null || processed.contains(startId)) return;

        processed.add(startId);
        String expr = nodeExprMap.get(startId);
        if (expr != null) {
            if (group.getExpressions().isEmpty()) {
                group.getExpressions().add(expr);
            } else {
                String logicGate = extractLogicGate(current);
                group.getLogicGates().add(mapLogicGate(logicGate));
                group.getExpressions().add(expr);
            }
        }

        List<String> neighbors = adjacencyList.getOrDefault(startId, Collections.emptyList());
        for (String neighborId : neighbors) {
            Node neighbor = nodeMap.get(neighborId);
            if (neighbor != null && "condition".equals(neighbor.getType()) && !processed.contains(neighborId)) {
                collectConditionGroup(neighborId, nodeMap, adjacencyList, reverseAdjacency,
                        nodeExprMap, group, processed);
            }
        }
    }

    // ==================== 拓扑排序 ====================

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

    // ==================== DRL: When 子句构建 ====================

    private String buildWhenClause(ParseContext ctx) {
        if (ctx.getConditionNodes().isEmpty()) {
            return "        $data : DeviceData()\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("        $data : DeviceData(\n");

        List<ConditionGroup> groups = buildConditionGroups(ctx, false);
        List<String> groupStrings = new ArrayList<>();

        for (ConditionGroup group : groups) {
            StringBuilder groupSb = new StringBuilder();
            List<String> exprs = group.getExpressions();
            List<String> gates = group.getLogicGates();
            for (int i = 0; i < exprs.size(); i++) {
                if (i > 0 && i <= gates.size()) {
                    groupSb.append(" ").append(gates.get(i - 1)).append(" ");
                }
                groupSb.append(exprs.get(i));
            }
            if (exprs.size() > 1) {
                groupStrings.add("(" + groupSb.toString() + ")");
            } else {
                groupStrings.add(groupSb.toString());
            }
        }

        for (int i = 0; i < groupStrings.size(); i++) {
            sb.append("            ");
            if (i > 0) {
                sb.append("&& ");
            }
            sb.append(groupStrings.get(i));
            if (i < groupStrings.size() - 1) {
                sb.append("\n");
            }
        }

        sb.append("\n        )\n");
        return sb.toString();
    }

    // ==================== DRL: 条件构建 ====================

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

    public String mapOperator(String operator) {
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

    // ==================== DRL: Then 子句构建 ====================

    private String buildThenClause(List<Node> actionNodes) {
        StringBuilder sb = new StringBuilder();

        for (Node actionNode : actionNodes) {
            sb.append(buildSingleAction(actionNode));
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
        String targetDeviceId = data.containsKey("deviceId") ? data.getString("deviceId") : null;

        sb.append("        ");

        switch (actionType) {
            case "turn_on_aircon":
                sb.append(buildTurnOnAircon(params, targetDeviceId));
                break;
            case "turn_off_aircon":
                sb.append(buildTurnOffAircon(targetDeviceId));
                break;
            case "turn_on_light":
                sb.append(buildTurnOnLight(params, targetDeviceId));
                break;
            case "turn_off_light":
                sb.append(buildTurnOffLight(targetDeviceId));
                break;
            case "send_alert":
                sb.append(buildSendAlert(params, targetDeviceId));
                break;
            default:
                sb.append(buildGenericAction(actionType, params, targetDeviceId));
        }

        return sb.toString();
    }

    private String buildGenericAction(String actionType, JSONObject params, String targetDeviceId) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> paramMap = new HashMap<>();
        if (params != null) {
            for (String key : params.keySet()) {
                paramMap.put(key, params.get(key));
            }
        }
        String mapStr = buildMapLiteral(paramMap);
        String ruleIdExpr = "String.valueOf(drools.getRule().getMetaData().get(\"rule-id\"))";
        String ruleNameExpr = "drools.getRule().getName()";
        if (targetDeviceId != null && !targetDeviceId.trim().isEmpty()) {
            sb.append("$data.addPendingAction(\"").append(actionType).append("\", ")
                    .append(mapStr).append(", \"").append(targetDeviceId).append("\", ")
                    .append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        } else {
            sb.append("$data.addPendingAction(\"").append(actionType).append("\", ")
                    .append(mapStr).append(", null, ")
                    .append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        }
        return sb.toString();
    }

    private String buildMapLiteral(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "java.util.Map.of()";
        }
        if (params.size() <= 10) {
            StringBuilder sb = new StringBuilder("java.util.Map.of(");
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) sb.append(", ");
                sb.append("\"").append(entry.getKey()).append("\", ");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else if (value instanceof Number) {
                    if (value instanceof Double || value instanceof Float) {
                        sb.append(((Number) value).doubleValue());
                    } else {
                        sb.append(((Number) value).intValue());
                    }
                } else if (value instanceof Boolean) {
                    sb.append(value);
                } else {
                    sb.append("\"").append(value != null ? value.toString() : "").append("\"");
                }
                first = false;
            }
            sb.append(")");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder("new java.util.HashMap<>(){{");
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                sb.append("put(\"").append(entry.getKey()).append("\", ");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else if (value instanceof Number) {
                    if (value instanceof Double || value instanceof Float) {
                        sb.append(((Number) value).doubleValue());
                    } else {
                        sb.append(((Number) value).intValue());
                    }
                } else if (value instanceof Boolean) {
                    sb.append(value);
                } else {
                    sb.append("\"").append(value != null ? value.toString() : "").append("\"");
                }
                sb.append("); ");
            }
            sb.append("}}");
            return sb.toString();
        }
    }

    private String buildTurnOnAircon(JSONObject params, String targetDeviceId) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("temperature", 26.0);
        paramMap.put("mode", "cool");
        if (params != null) {
            if (params.containsKey("temperature")) {
                paramMap.put("temperature", params.getDoubleValue("temperature"));
            }
            if (params.containsKey("mode")) {
                paramMap.put("mode", params.getString("mode"));
            }
        }
        String mapStr = buildMapLiteral(paramMap);
        String ruleIdExpr = "String.valueOf(drools.getRule().getMetaData().get(\"rule-id\"))";
        String ruleNameExpr = "drools.getRule().getName()";
        StringBuilder sb = new StringBuilder();
        if (targetDeviceId != null && !targetDeviceId.trim().isEmpty()) {
            sb.append("$data.addPendingAction(\"turn_on_aircon\", ").append(mapStr)
                    .append(", \"").append(targetDeviceId).append("\", ")
                    .append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        } else {
            sb.append("$data.addPendingAction(\"turn_on_aircon\", ").append(mapStr)
                    .append(", null, ").append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        }
        return sb.toString();
    }

    private String buildTurnOffAircon(String targetDeviceId) {
        String mapStr = "java.util.Map.of()";
        String ruleIdExpr = "String.valueOf(drools.getRule().getMetaData().get(\"rule-id\"))";
        String ruleNameExpr = "drools.getRule().getName()";
        StringBuilder sb = new StringBuilder();
        if (targetDeviceId != null && !targetDeviceId.trim().isEmpty()) {
            sb.append("$data.addPendingAction(\"turn_off_aircon\", ").append(mapStr)
                    .append(", \"").append(targetDeviceId).append("\", ")
                    .append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        } else {
            sb.append("$data.addPendingAction(\"turn_off_aircon\", ").append(mapStr)
                    .append(", null, ").append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        }
        return sb.toString();
    }

    private String buildTurnOnLight(JSONObject params, String targetDeviceId) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("brightness", 100);
        paramMap.put("color", "white");
        if (params != null) {
            if (params.containsKey("brightness")) {
                paramMap.put("brightness", params.getIntValue("brightness"));
            }
            if (params.containsKey("color")) {
                paramMap.put("color", params.getString("color"));
            }
        }
        String mapStr = buildMapLiteral(paramMap);
        String ruleIdExpr = "String.valueOf(drools.getRule().getMetaData().get(\"rule-id\"))";
        String ruleNameExpr = "drools.getRule().getName()";
        StringBuilder sb = new StringBuilder();
        if (targetDeviceId != null && !targetDeviceId.trim().isEmpty()) {
            sb.append("$data.addPendingAction(\"turn_on_light\", ").append(mapStr)
                    .append(", \"").append(targetDeviceId).append("\", ")
                    .append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        } else {
            sb.append("$data.addPendingAction(\"turn_on_light\", ").append(mapStr)
                    .append(", null, ").append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        }
        return sb.toString();
    }

    private String buildTurnOffLight(String targetDeviceId) {
        String mapStr = "java.util.Map.of()";
        String ruleIdExpr = "String.valueOf(drools.getRule().getMetaData().get(\"rule-id\"))";
        String ruleNameExpr = "drools.getRule().getName()";
        StringBuilder sb = new StringBuilder();
        if (targetDeviceId != null && !targetDeviceId.trim().isEmpty()) {
            sb.append("$data.addPendingAction(\"turn_off_light\", ").append(mapStr)
                    .append(", \"").append(targetDeviceId).append("\", ")
                    .append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        } else {
            sb.append("$data.addPendingAction(\"turn_off_light\", ").append(mapStr)
                    .append(", null, ").append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        }
        return sb.toString();
    }

    private String buildSendAlert(JSONObject params, String targetDeviceId) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("level", "info");
        paramMap.put("message", "规则触发告警");
        paramMap.put("deviceId", "$data.getDeviceId()");
        paramMap.put("temperature", "$data.getTemperature()");
        paramMap.put("humidity", "$data.getHumidity()");
        paramMap.put("presence", "$data.getPresence()");
        if (params != null) {
            if (params.containsKey("level")) {
                paramMap.put("level", params.getString("level"));
            }
            if (params.containsKey("message")) {
                paramMap.put("message", params.getString("message"));
            }
        }
        String mapStr = buildAlertMapLiteral(paramMap);
        String ruleIdExpr = "String.valueOf(drools.getRule().getMetaData().get(\"rule-id\"))";
        String ruleNameExpr = "drools.getRule().getName()";
        StringBuilder sb = new StringBuilder();
        if (targetDeviceId != null && !targetDeviceId.trim().isEmpty()) {
            sb.append("$data.addPendingAction(\"send_alert\", ").append(mapStr)
                    .append(", \"").append(targetDeviceId).append("\", ")
                    .append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        } else {
            sb.append("$data.addPendingAction(\"send_alert\", ").append(mapStr)
                    .append(", null, ").append(ruleIdExpr).append(", ").append(ruleNameExpr).append(");\n");
        }
        return sb.toString();
    }

    private String buildAlertMapLiteral(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "java.util.Map.of()";
        }
        if (params.size() <= 10) {
            StringBuilder sb = new StringBuilder("java.util.Map.of(");
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) sb.append(", ");
                sb.append("\"").append(entry.getKey()).append("\", ");
                Object value = entry.getValue();
                if (value instanceof String && ((String) value).startsWith("$data.")) {
                    sb.append(value);
                } else if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else if (value instanceof Number) {
                    if (value instanceof Double || value instanceof Float) {
                        sb.append(((Number) value).doubleValue());
                    } else {
                        sb.append(((Number) value).intValue());
                    }
                } else if (value instanceof Boolean) {
                    sb.append(value);
                } else {
                    sb.append("\"").append(value != null ? value.toString() : "").append("\"");
                }
                first = false;
            }
            sb.append(")");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder("new java.util.HashMap<>(){{");
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                sb.append("put(\"").append(entry.getKey()).append("\", ");
                Object value = entry.getValue();
                if (value instanceof String && ((String) value).startsWith("$data.")) {
                    sb.append(value);
                } else if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else if (value instanceof Number) {
                    if (value instanceof Double || value instanceof Float) {
                        sb.append(((Number) value).doubleValue());
                    } else {
                        sb.append(((Number) value).intValue());
                    }
                } else if (value instanceof Boolean) {
                    sb.append(value);
                } else {
                    sb.append("\"").append(value != null ? value.toString() : "").append("\"");
                }
                sb.append("); ");
            }
            sb.append("}}");
            return sb.toString();
        }
    }

    // ==================== Aviator: 表达式构建 ====================

    private String buildAviatorExpression(ParseContext ctx) {
        if (ctx.getConditionNodes().isEmpty()) {
            return "true";
        }

        List<ConditionGroup> groups = buildConditionGroups(ctx, true);
        List<String> groupStrings = new ArrayList<>();

        for (ConditionGroup group : groups) {
            String groupExpr = joinGroupWithParentheses(group);
            groupStrings.add(groupExpr);
        }

        if (groupStrings.isEmpty()) {
            return "true";
        }
        if (groupStrings.size() == 1) {
            return groupStrings.get(0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(String.join(" && ", groupStrings));
        sb.append(")");
        return sb.toString();
    }

    private String joinGroupWithParentheses(ConditionGroup group) {
        List<String> exprs = group.getExpressions();
        List<String> gates = group.getLogicGates();

        if (exprs.isEmpty()) {
            return "true";
        }
        if (exprs.size() == 1) {
            return exprs.get(0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < exprs.size(); i++) {
            if (i > 0 && i <= gates.size()) {
                sb.append(" ").append(gates.get(i - 1)).append(" ");
            }
            sb.append(exprs.get(i));
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildAviatorSingleCondition(Node node) {
        JSONObject data = node.getData();
        if (data == null) return "true";

        String conditionType = data.getString("conditionType");
        String operator = data.getString("operator");
        String value = data.getString("value");

        if (conditionType == null) return "true";

        switch (conditionType) {
            case "temperature":
                return buildAviatorTemperatureCondition(operator, value);
            case "humidity":
                return buildAviatorHumidityCondition(operator, value);
            case "presence":
                return buildAviatorPresenceCondition(operator, value);
            case "time":
                return buildAviatorTimeCondition(operator, value);
            default:
                return buildAviatorAttributeCondition(conditionType, operator, value);
        }
    }

    private String buildAviatorTemperatureCondition(String operator, String value) {
        if (value == null) return "true";
        String op = mapOperator(operator);
        Object numVal = parseNumberValue(value);
        return "temperature != nil && temperature " + op + " " + numVal;
    }

    private String buildAviatorHumidityCondition(String operator, String value) {
        if (value == null) return "true";
        String op = mapOperator(operator);
        Object numVal = parseNumberValue(value);
        return "humidity != nil && humidity " + op + " " + numVal;
    }

    private String buildAviatorPresenceCondition(String operator, String value) {
        boolean expected = "true".equalsIgnoreCase(value) || "1".equals(value) || "是".equals(value);
        String boolStr = expected ? "true" : "false";
        return "presence != nil && presence == " + boolStr;
    }

    private String buildAviatorTimeCondition(String operator, String value) {
        if (value == null) return "true";

        String[] range = value.split("~");
        if (range.length == 2) {
            String startTime = escapeStringValue(range[0].trim());
            String endTime = escapeStringValue(range[1].trim());
            return "time != nil && time >= '" + startTime + "' && time <= '" + endTime + "'";
        }

        String op = mapOperator(operator);
        String escapedVal = escapeStringValue(value);
        return "time != nil && time " + op + " '" + escapedVal + "'";
    }

    private String buildAviatorAttributeCondition(String conditionType, String operator, String value) {
        if (value == null) return "true";
        String op = mapOperator(operator);
        String attrKey = escapeStringValue(conditionType);
        Object numVal = tryParseNumberValue(value);

        StringBuilder sb = new StringBuilder();
        sb.append("get(attributes, '").append(attrKey).append("') != nil");
        sb.append(" && get(attributes, '").append(attrKey).append("') ").append(op).append(" ").append(numVal);
        return sb.toString();
    }

    // ==================== Aviator: 动作构建 ====================

    private List<ActionDef> buildActionDefList(ParseContext ctx) {
        List<ActionDef> result = new ArrayList<>();
        List<Node> actionNodes = ctx.getActionNodes();
        Map<String, List<String>> adjacency = ctx.getAdjacencyList();
        Map<String, Node> nodeMap = ctx.getNodeMap();

        Set<String> actionIdSet = new HashSet<>();
        for (Node n : actionNodes) {
            actionIdSet.add(n.getId());
        }

        Map<String, Integer> inDegree = new HashMap<>();
        for (Node n : actionNodes) {
            inDegree.put(n.getId(), 0);
        }
        for (Node n : actionNodes) {
            List<String> targets = adjacency.getOrDefault(n.getId(), Collections.emptyList());
            for (String t : targets) {
                if (actionIdSet.contains(t)) {
                    inDegree.put(t, inDegree.getOrDefault(t, 0) + 1);
                }
            }
        }
        for (String condId : ctx.getNodeMap().keySet()) {
            Node cond = nodeMap.get(condId);
            if (cond != null && "condition".equals(cond.getType())) {
                List<String> targets = adjacency.getOrDefault(condId, Collections.emptyList());
                for (String t : targets) {
                    if (actionIdSet.contains(t)) {
                        inDegree.put(t, inDegree.getOrDefault(t, 0));
                    }
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Node n : actionNodes) {
            if (inDegree.getOrDefault(n.getId(), 0) == 0) {
                queue.add(n.getId());
            }
        }

        Map<String, Integer> orderMap = new HashMap<>();
        int order = 0;
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            orderMap.put(nodeId, order++);

            List<String> targets = adjacency.getOrDefault(nodeId, Collections.emptyList());
            for (String t : targets) {
                if (actionIdSet.contains(t)) {
                    int deg = inDegree.get(t) - 1;
                    inDegree.put(t, deg);
                    if (deg == 0) {
                        queue.add(t);
                    }
                }
            }
        }

        for (Node n : actionNodes) {
            if (!orderMap.containsKey(n.getId())) {
                orderMap.put(n.getId(), order++);
            }
        }

        List<Node> sortedActions = new ArrayList<>(actionNodes);
        sortedActions.sort(Comparator.comparingInt(a -> orderMap.getOrDefault(a.getId(), 0)));

        int idx = 0;
        for (Node actionNode : sortedActions) {
            ActionDef def = buildSingleActionDef(actionNode, idx++);
            if (def != null) {
                result.add(def);
            }
        }

        return result;
    }

    private ActionDef buildSingleActionDef(Node actionNode, int order) {
        JSONObject data = actionNode.getData();
        if (data == null) return null;

        String actionType = data.getString("actionType");
        if (actionType == null) return null;

        ActionDef def = new ActionDef();
        def.setActionType(actionType);
        def.setOrder(order);

        String targetDeviceId = data.containsKey("deviceId") ? data.getString("deviceId") : null;
        if (targetDeviceId != null && targetDeviceId.trim().isEmpty()) {
            targetDeviceId = null;
        }
        def.setTargetDeviceId(targetDeviceId);

        JSONObject params = data.getJSONObject("params");
        Map<String, Object> paramMap = buildActionParamMap(actionType, params);
        def.setParams(paramMap);

        return def;
    }

    private Map<String, Object> buildActionParamMap(String actionType, JSONObject params) {
        Map<String, Object> result = new LinkedHashMap<>();

        switch (actionType) {
            case "turn_on_aircon":
                result.put("temperature", coerceNumber(26));
                result.put("mode", "cool");
                break;
            case "turn_on_light":
                result.put("brightness", coerceNumber(100));
                result.put("color", "white");
                break;
            case "send_alert":
                result.put("level", "info");
                result.put("message", "告警触发");
                break;
            default:
                break;
        }

        if (params != null) {
            for (String key : params.keySet()) {
                Object val = params.get(key);
                if (val instanceof Number) {
                    result.put(key, coerceNumber((Number) val));
                } else if (val instanceof String) {
                    String strVal = (String) val;
                    if (isTemplatePlaceholder(strVal)) {
                        result.put(key, strVal);
                    } else {
                        result.put(key, strVal);
                    }
                } else {
                    result.put(key, val);
                }
            }
        }

        return result;
    }

    private boolean isTemplatePlaceholder(String val) {
        return val != null && val.startsWith("${") && val.endsWith("}");
    }

    // ==================== 工具方法 ====================

    public String escapeStringValue(String val) {
        if (val == null) return "";
        return val.replace("'", "\\'");
    }

    public Number coerceNumber(Number val) {
        if (val == null) return 0.0;
        if (val instanceof Double || val instanceof Float) {
            return val.doubleValue();
        }
        return val.doubleValue();
    }

    private Object parseNumberValue(String valueStr) {
        try {
            double d = Double.parseDouble(valueStr.trim());
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return coerceNumber((long) d);
            }
            return d;
        } catch (NumberFormatException e) {
            return "'" + escapeStringValue(valueStr) + "'";
        }
    }

    private Object tryParseNumberValue(String valueStr) {
        try {
            double d = Double.parseDouble(valueStr.trim());
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return coerceNumber((long) d);
            }
            return d;
        } catch (NumberFormatException e) {
            return "'" + escapeStringValue(valueStr) + "'";
        }
    }
}
