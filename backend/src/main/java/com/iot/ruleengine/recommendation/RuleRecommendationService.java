package com.iot.ruleengine.recommendation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.ruleengine.dto.RuleRecommendationDTO;
import com.iot.ruleengine.entity.ActionLog;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.repository.ActionLogRepository;
import com.iot.ruleengine.repository.DeviceRepository;
import com.iot.ruleengine.repository.RuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RuleRecommendationService {

    private final ActionLogRepository actionLogRepository;
    private final DeviceRepository deviceRepository;
    private final RuleRepository ruleRepository;

    private static final int DAYS_TO_ANALYZE = 30;
    private static final int MIN_MANUAL_ACTIONS = 3;
    private static final int MAX_RECOMMENDATIONS = 6;

    private static final Map<String, String> DEVICE_TYPE_ICONS = new HashMap<>();
    private static final Map<String, String> FIELD_LABELS = new HashMap<>();
    private static final Map<String, String> ACTION_LABELS = new HashMap<>();

    static {
        DEVICE_TYPE_ICONS.put("light", "💡");
        DEVICE_TYPE_ICONS.put("aircon", "❄️");
        DEVICE_TYPE_ICONS.put("sensor_temp", "🌡️");
        DEVICE_TYPE_ICONS.put("sensor_humidity", "💧");
        DEVICE_TYPE_ICONS.put("sensor_presence", "👤");
        DEVICE_TYPE_ICONS.put("curtain", "🪟");
        DEVICE_TYPE_ICONS.put("tv", "📺");
        DEVICE_TYPE_ICONS.put("speaker", "🔊");

        FIELD_LABELS.put("power", "电源");
        FIELD_LABELS.put("temperature", "温度");
        FIELD_LABELS.put("humidity", "湿度");
        FIELD_LABELS.put("presence", "人体感应");
        FIELD_LABELS.put("brightness", "亮度");
        FIELD_LABELS.put("mode", "模式");

        ACTION_LABELS.put("turn_on", "开启");
        ACTION_LABELS.put("turn_off", "关闭");
        ACTION_LABELS.put("set_temperature", "设置温度");
        ACTION_LABELS.put("set_mode", "设置模式");
        ACTION_LABELS.put("open", "打开");
        ACTION_LABELS.put("close", "关闭");
    }

    @Autowired
    public RuleRecommendationService(ActionLogRepository actionLogRepository,
                                      DeviceRepository deviceRepository,
                                      RuleRepository ruleRepository) {
        this.actionLogRepository = actionLogRepository;
        this.deviceRepository = deviceRepository;
        this.ruleRepository = ruleRepository;
    }

    public List<RuleRecommendationDTO> getRecommendations() {
        log.info("开始生成规则推荐...");

        List<Device> devices = deviceRepository.selectList(
                new QueryWrapper<Device>().eq("deleted", 0).eq("online", 1));
        Map<String, Device> deviceMap = devices.stream()
                .collect(Collectors.toMap(Device::getDeviceId, d -> d));

        List<ActionLog> manualActions = getManualActions();
        List<Rule> existingRules = ruleRepository.selectList(new QueryWrapper<>());
        Set<String> existingActionPatterns = extractExistingActionPatterns(existingRules);

        Map<String, List<ActionLog>> deviceActionGroups = manualActions.stream()
                .filter(log -> log.getDeviceId() != null && log.getActionType() != null)
                .collect(Collectors.groupingBy(
                        log -> log.getDeviceId() + "_" + log.getActionType()));

        List<RuleRecommendationDTO> recommendations = new ArrayList<>();

        for (Map.Entry<String, List<ActionLog>> entry : deviceActionGroups.entrySet()) {
            List<ActionLog> actions = entry.getValue();
            if (actions.size() < MIN_MANUAL_ACTIONS) {
                continue;
            }

            String pattern = entry.getKey();
            if (existingActionPatterns.contains(pattern)) {
                log.debug("跳过已存在规则的模式: {}", pattern);
                continue;
            }

            RuleRecommendationDTO recommendation = buildRecommendation(
                    actions.get(0), actions.size(), deviceMap);
            if (recommendation != null) {
                recommendations.add(recommendation);
            }
        }

        addCollaborativeFilteringRecommendations(recommendations, manualActions, devices, existingActionPatterns);

        recommendations = recommendations.stream()
                .sorted((r1, r2) -> {
                    int priorityCompare = Integer.compare(
                            r2.getSuggestionPriority(), r1.getSuggestionPriority());
                    if (priorityCompare != 0) return priorityCompare;
                    return r2.getConfidence().compareTo(r1.getConfidence());
                })
                .limit(MAX_RECOMMENDATIONS)
                .collect(Collectors.toList());

        log.info("生成了 {} 条规则推荐", recommendations.size());
        return recommendations;
    }

    private List<ActionLog> getManualActions() {
        LocalDateTime startTime = LocalDateTime.now().minusDays(DAYS_TO_ANALYZE);
        QueryWrapper<ActionLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNull("rule_id")
                .ge("execute_time", startTime)
                .eq("result", 1)
                .orderByDesc("execute_time");
        return actionLogRepository.selectList(queryWrapper);
    }

    private Set<String> extractExistingActionPatterns(List<Rule> rules) {
        Set<String> patterns = new HashSet<>();
        for (Rule rule : rules) {
            if (rule.getAviatorActions() != null && !rule.getAviatorActions().isEmpty()) {
                try {
                    JSONArray actions = JSON.parseArray(rule.getAviatorActions());
                    for (int i = 0; i < actions.size(); i++) {
                        JSONObject action = actions.getJSONObject(i);
                        String deviceId = action.getString("deviceId");
                        String actionType = action.getString("action");
                        if (deviceId != null && actionType != null) {
                            patterns.add(deviceId + "_" + actionType);
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析规则动作失败: {}", rule.getName());
                }
            }
        }
        return patterns;
    }

    private RuleRecommendationDTO buildRecommendation(ActionLog sampleAction, int actionCount,
                                                       Map<String, Device> deviceMap) {
        Device targetDevice = deviceMap.get(sampleAction.getDeviceId());
        if (targetDevice == null) {
            return null;
        }

        String actionType = sampleAction.getActionType();
        RuleRecommendationDTO rec = new RuleRecommendationDTO();
        rec.setRecommendationId(UUID.randomUUID().toString());
        rec.setGeneratedAt(LocalDateTime.now());
        rec.setManualActionCount(actionCount);

        BigDecimal confidence = calculateConfidence(actionCount);
        rec.setConfidence(confidence);
        rec.setSuggestionPriority(calculatePriority(confidence, actionCount, targetDevice.getType()));

        String icon = DEVICE_TYPE_ICONS.getOrDefault(targetDevice.getType(), "🔧");
        rec.setIcon(icon);

        JSONObject actionParams = null;
        if (sampleAction.getActionParams() != null) {
            try {
                actionParams = JSON.parseObject(sampleAction.getActionParams());
            } catch (Exception ignored) {
            }
        }

        buildRecommendationContent(rec, targetDevice, actionType, actionParams, actionCount);

        return rec;
    }

    private void buildRecommendationContent(RuleRecommendationDTO rec, Device device,
                                            String actionType, JSONObject params, int count) {
        String deviceType = device.getType();
        String room = device.getRoom() != null ? device.getRoom() : "";
        String deviceName = device.getName();

        List<RuleRecommendationDTO.ConditionTemplate> conditions = new ArrayList<>();
        List<RuleRecommendationDTO.ActionTemplate> actions = new ArrayList<>();

        String title;
        String description;
        String reason;
        String category;

        if ("turn_off".equals(actionType) && "light".equals(deviceType)) {
            category = "energy_saving";
            title = "无人时自动关灯";
            description = String.format("当检测到%s无人时，自动关闭%s", room, deviceName);
            reason = String.format("检测到您在过去%d天内手动关灯%d次，建议设置自动化规则", DAYS_TO_ANALYZE, count);

            Device presenceSensor = findPresenceSensor(device.getRoom());
            if (presenceSensor != null) {
                RuleRecommendationDTO.ConditionTemplate cond = new RuleRecommendationDTO.ConditionTemplate();
                cond.setDeviceId(presenceSensor.getDeviceId());
                cond.setDeviceName(presenceSensor.getName());
                cond.setField("presence");
                cond.setFieldLabel("人体感应");
                cond.setOperator("==");
                cond.setValue(false);
                cond.setLabel("无人");
                conditions.add(cond);
            }
        } else if ("turn_on".equals(actionType) && "aircon".equals(deviceType)) {
            category = "comfort";
            title = "高温自动开空调";
            description = String.format("当%s温度过高时自动开启%s", room, deviceName);
            reason = String.format("检测到您在过去%d天内手动开启空调%d次，建议根据温度自动控制", DAYS_TO_ANALYZE, count);

            Device tempSensor = findTemperatureSensor(device.getRoom());
            if (tempSensor != null) {
                RuleRecommendationDTO.ConditionTemplate cond = new RuleRecommendationDTO.ConditionTemplate();
                cond.setDeviceId(tempSensor.getDeviceId());
                cond.setDeviceName(tempSensor.getName());
                cond.setField("temperature");
                cond.setFieldLabel("温度");
                cond.setOperator(">");
                cond.setValue(28);
                cond.setLabel("温度 > 28°C");
                conditions.add(cond);
            }
        } else if ("turn_off".equals(actionType) && "aircon".equals(deviceType)) {
            category = "energy_saving";
            title = "低温自动关空调";
            description = String.format("当%s温度适宜时自动关闭%s", room, deviceName);
            reason = String.format("检测到您在过去%d天内手动关闭空调%d次，建议根据温度自动控制", DAYS_TO_ANALYZE, count);

            Device tempSensor = findTemperatureSensor(device.getRoom());
            if (tempSensor != null) {
                RuleRecommendationDTO.ConditionTemplate cond = new RuleRecommendationDTO.ConditionTemplate();
                cond.setDeviceId(tempSensor.getDeviceId());
                cond.setDeviceName(tempSensor.getName());
                cond.setField("temperature");
                cond.setFieldLabel("温度");
                cond.setOperator("<");
                cond.setValue(24);
                cond.setLabel("温度 < 24°C");
                conditions.add(cond);
            }
        } else if ("turn_on".equals(actionType) && "light".equals(deviceType)) {
            category = "convenience";
            title = "有人时自动开灯";
            description = String.format("当检测到%s有人时，自动开启%s", room, deviceName);
            reason = String.format("检测到您在过去%d天内手动开灯%d次，建议设置自动感应", DAYS_TO_ANALYZE, count);

            Device presenceSensor = findPresenceSensor(device.getRoom());
            if (presenceSensor != null) {
                RuleRecommendationDTO.ConditionTemplate cond = new RuleRecommendationDTO.ConditionTemplate();
                cond.setDeviceId(presenceSensor.getDeviceId());
                cond.setDeviceName(presenceSensor.getName());
                cond.setField("presence");
                cond.setFieldLabel("人体感应");
                cond.setOperator("==");
                cond.setValue(true);
                cond.setLabel("有人");
                conditions.add(cond);
            }
        } else if ("close".equals(actionType) && "curtain".equals(deviceType)) {
            category = "convenience";
            title = "夜间自动关窗帘";
            description = String.format("夜间自动关闭%s的%s", room, deviceName);
            reason = String.format("检测到您在过去%d天内手动关窗帘%d次，建议设置定时自动化", DAYS_TO_ANALYZE, count);
        } else {
            category = "general";
            String actionLabel = ACTION_LABELS.getOrDefault(actionType, actionType);
            title = String.format("自动%s%s", actionLabel, deviceName);
            description = String.format("根据条件自动%s%s", actionLabel, deviceName);
            reason = String.format("检测到您在过去%d天内手动操作%d次", DAYS_TO_ANALYZE, count);
        }

        RuleRecommendationDTO.ActionTemplate action = new RuleRecommendationDTO.ActionTemplate();
        action.setDeviceId(device.getDeviceId());
        action.setDeviceName(device.getName());
        action.setAction(actionType);
        action.setActionLabel(ACTION_LABELS.getOrDefault(actionType, actionType));
        if (params != null && !params.isEmpty()) {
            action.setParams(params.getInnerMap());
        }
        action.setLabel(buildActionLabel(device, actionType, params));
        actions.add(action);

        rec.setTitle(title);
        rec.setDescription(description);
        rec.setReason(reason);
        rec.setCategory(category);
        rec.setConditions(conditions);
        rec.setActions(actions);
        rec.setTemplateRuleName(title);
        rec.setTemplateDescription(description);

        List<String> relatedDeviceIds = new ArrayList<>();
        relatedDeviceIds.add(device.getDeviceId());
        for (RuleRecommendationDTO.ConditionTemplate cond : conditions) {
            if (!relatedDeviceIds.contains(cond.getDeviceId())) {
                relatedDeviceIds.add(cond.getDeviceId());
            }
        }
        rec.setRelatedDeviceIds(relatedDeviceIds);

        rec.setRuleJson(buildRuleJson(rec));
    }

    private Map<String, Object> buildRuleJson(RuleRecommendationDTO rec) {
        Map<String, Object> ruleJson = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "start");
        startNode.put("type", "startNode");
        startNode.put("position", Map.of("x", 50, "y", 150));
        startNode.put("data", Map.of("label", "触发开始"));
        nodes.add(startNode);

        int xOffset = 250;
        int yOffset = 80;
        String lastNodeId = "start";
        String logicNodeId = null;

        if (!rec.getConditions().isEmpty()) {
            if (rec.getConditions().size() > 1) {
                logicNodeId = "logic_and";
                Map<String, Object> logicNode = new HashMap<>();
                logicNode.put("id", logicNodeId);
                logicNode.put("type", "logicNode");
                logicNode.put("position", Map.of("x", 450, "y", 150));
                logicNode.put("data", Map.of("label", "AND", "type", "AND"));
                nodes.add(logicNode);
            }

            for (int i = 0; i < rec.getConditions().size(); i++) {
                RuleRecommendationDTO.ConditionTemplate cond = rec.getConditions().get(i);
                String condNodeId = "cond_" + i;
                Map<String, Object> condNode = new HashMap<>();
                condNode.put("id", condNodeId);
                condNode.put("type", "conditionNode");
                condNode.put("position", Map.of("x", xOffset, "y", yOffset + (i * 140)));
                condNode.put("data", Map.of(
                        "label", cond.getLabel(),
                        "deviceId", cond.getDeviceId(),
                        "field", cond.getField(),
                        "operator", cond.getOperator(),
                        "value", cond.getValue()
                ));
                nodes.add(condNode);

                if (logicNodeId != null) {
                    Map<String, Object> edge = new HashMap<>();
                    edge.put("id", "e_cond_" + i);
                    edge.put("source", condNodeId);
                    edge.put("target", logicNodeId);
                    edge.put("sourceHandle", "out");
                    edge.put("targetHandle", "in" + (i + 1));
                    edges.add(edge);
                } else {
                    Map<String, Object> edge = new HashMap<>();
                    edge.put("id", "e_cond_" + i);
                    edge.put("source", "start");
                    edge.put("target", condNodeId);
                    edge.put("sourceHandle", "out");
                    edges.add(edge);
                    lastNodeId = condNodeId;
                }
            }

            if (logicNodeId != null) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("id", "e_start_logic");
                edge.put("source", "start");
                edge.put("target", logicNodeId);
                edge.put("sourceHandle", "out");
                edges.add(edge);
                lastNodeId = logicNodeId;
            }
        }

        for (int i = 0; i < rec.getActions().size(); i++) {
            RuleRecommendationDTO.ActionTemplate act = rec.getActions().get(i);
            String actionNodeId = "action_" + i;
            Map<String, Object> actionNode = new HashMap<>();
            actionNode.put("id", actionNodeId);
            actionNode.put("type", "actionNode");
            actionNode.put("position", Map.of("x", 650, "y", 150 + (i * 100)));
            Map<String, Object> actionData = new HashMap<>();
            actionData.put("label", act.getLabel());
            actionData.put("deviceId", act.getDeviceId());
            actionData.put("action", act.getAction());
            if (act.getParams() != null) {
                actionData.put("params", act.getParams());
            }
            actionNode.put("data", actionData);
            nodes.add(actionNode);

            Map<String, Object> edge = new HashMap<>();
            edge.put("id", "e_action_" + i);
            edge.put("source", lastNodeId);
            edge.put("target", actionNodeId);
            edge.put("sourceHandle", "out");
            edges.add(edge);
        }

        ruleJson.put("nodes", nodes);
        ruleJson.put("edges", edges);
        return ruleJson;
    }

    private String buildActionLabel(Device device, String actionType, JSONObject params) {
        String actionLabel = ACTION_LABELS.getOrDefault(actionType, actionType);
        StringBuilder label = new StringBuilder(actionLabel + device.getName());
        if (params != null && !params.isEmpty()) {
            List<String> paramParts = new ArrayList<>();
            for (String key : params.keySet()) {
                Object value = params.get(key);
                String fieldLabel = FIELD_LABELS.getOrDefault(key, key);
                paramParts.add(fieldLabel + value);
            }
            if (!paramParts.isEmpty()) {
                label.append("(").append(String.join(", ", paramParts)).append(")");
            }
        }
        return label.toString();
    }

    private Device findPresenceSensor(String room) {
        QueryWrapper<Device> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", "sensor_presence").eq("deleted", 0);
        if (room != null && !room.isEmpty()) {
            queryWrapper.eq("room", room);
        }
        List<Device> devices = deviceRepository.selectList(queryWrapper);
        return devices.isEmpty() ? null : devices.get(0);
    }

    private Device findTemperatureSensor(String room) {
        QueryWrapper<Device> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", "sensor_temp").eq("deleted", 0);
        if (room != null && !room.isEmpty()) {
            queryWrapper.eq("room", room);
        }
        List<Device> devices = deviceRepository.selectList(queryWrapper);
        return devices.isEmpty() ? null : devices.get(0);
    }

    private BigDecimal calculateConfidence(int actionCount) {
        double baseConfidence = Math.min(0.95, 0.5 + (actionCount - MIN_MANUAL_ACTIONS) * 0.1);
        return BigDecimal.valueOf(baseConfidence).setScale(2, RoundingMode.HALF_UP);
    }

    private int calculatePriority(BigDecimal confidence, int actionCount, String deviceType) {
        int priority = confidence.multiply(BigDecimal.valueOf(50)).intValue();
        priority += Math.min(actionCount * 2, 30);

        if ("aircon".equals(deviceType)) {
            priority += 10;
        } else if ("light".equals(deviceType)) {
            priority += 5;
        }

        return Math.min(priority, 100);
    }

    private void addCollaborativeFilteringRecommendations(List<RuleRecommendationDTO> recommendations,
                                                          List<ActionLog> manualActions,
                                                          List<Device> devices,
                                                          Set<String> existingPatterns) {
        if (devices.size() < 2) {
            return;
        }

        Map<String, List<ActionLog>> deviceActions = manualActions.stream()
                .filter(log -> log.getDeviceId() != null)
                .collect(Collectors.groupingBy(ActionLog::getDeviceId));

        List<String> deviceIds = new ArrayList<>(deviceActions.keySet());
        for (int i = 0; i < deviceIds.size(); i++) {
            for (int j = i + 1; j < deviceIds.size(); j++) {
                String dev1 = deviceIds.get(i);
                String dev2 = deviceIds.get(j);

                double similarity = calculateJaccardSimilarity(
                        deviceActions.getOrDefault(dev1, Collections.emptyList()),
                        deviceActions.getOrDefault(dev2, Collections.emptyList()));

                if (similarity > 0.3) {
                    Device d1 = devices.stream().filter(d -> d.getDeviceId().equals(dev1)).findFirst().orElse(null);
                    Device d2 = devices.stream().filter(d -> d.getDeviceId().equals(dev2)).findFirst().orElse(null);

                    if (d1 != null && d2 != null) {
                        String pattern = dev1 + "_turn_on";
                        if (!existingPatterns.contains(pattern)) {
                            boolean exists = recommendations.stream()
                                    .anyMatch(r -> r.getRelatedDeviceIds().contains(dev1)
                                            && r.getRelatedDeviceIds().contains(dev2));
                            if (!exists) {
                                RuleRecommendationDTO comboRec = buildComboRecommendation(
                                        d1, d2, similarity, deviceActions);
                                if (comboRec != null) {
                                    recommendations.add(comboRec);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private double calculateJaccardSimilarity(List<ActionLog> actions1, List<ActionLog> actions2) {
        if (actions1.isEmpty() || actions2.isEmpty()) {
            return 0.0;
        }

        Set<String> times1 = actions1.stream()
                .map(a -> a.getExecuteTime() != null ? a.getExecuteTime().toLocalDate().toString() : "")
                .collect(Collectors.toSet());
        Set<String> times2 = actions2.stream()
                .map(a -> a.getExecuteTime() != null ? a.getExecuteTime().toLocalDate().toString() : "")
                .collect(Collectors.toSet());

        Set<String> intersection = new HashSet<>(times1);
        intersection.retainAll(times2);

        Set<String> union = new HashSet<>(times1);
        union.addAll(times2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private RuleRecommendationDTO buildComboRecommendation(Device d1, Device d2, double similarity,
                                                            Map<String, List<ActionLog>> deviceActions) {
        int totalActions = deviceActions.getOrDefault(d1.getDeviceId(), Collections.emptyList()).size()
                + deviceActions.getOrDefault(d2.getDeviceId(), Collections.emptyList()).size();

        RuleRecommendationDTO rec = new RuleRecommendationDTO();
        rec.setRecommendationId(UUID.randomUUID().toString());
        rec.setGeneratedAt(LocalDateTime.now());
        rec.setConfidence(BigDecimal.valueOf(similarity).setScale(2, RoundingMode.HALF_UP));
        rec.setManualActionCount(totalActions);
        rec.setSuggestionPriority((int) (similarity * 60) + Math.min(totalActions, 20));
        rec.setIcon("🔗");
        rec.setCategory("combo");
        rec.setTitle(String.format("联动：%s + %s", d1.getName(), d2.getName()));
        rec.setDescription(String.format("当操作%s时自动联动控制%s", d1.getName(), d2.getName()));
        rec.setReason(String.format("基于协同过滤分析，这两个设备的操作行为相似度达%.0f%%", similarity * 100));

        List<RuleRecommendationDTO.ConditionTemplate> conditions = new ArrayList<>();
        List<RuleRecommendationDTO.ActionTemplate> actions = new ArrayList<>();
        List<String> relatedDeviceIds = new ArrayList<>();
        relatedDeviceIds.add(d1.getDeviceId());
        relatedDeviceIds.add(d2.getDeviceId());

        RuleRecommendationDTO.ConditionTemplate cond = new RuleRecommendationDTO.ConditionTemplate();
        cond.setDeviceId(d1.getDeviceId());
        cond.setDeviceName(d1.getName());
        cond.setField("power");
        cond.setFieldLabel("电源");
        cond.setOperator("==");
        cond.setValue("on");
        cond.setLabel(d1.getName() + " 开启");
        conditions.add(cond);

        RuleRecommendationDTO.ActionTemplate action = new RuleRecommendationDTO.ActionTemplate();
        action.setDeviceId(d2.getDeviceId());
        action.setDeviceName(d2.getName());
        action.setAction("turn_on");
        action.setActionLabel("开启");
        action.setLabel("开启 " + d2.getName());
        actions.add(action);

        rec.setConditions(conditions);
        rec.setActions(actions);
        rec.setRelatedDeviceIds(relatedDeviceIds);
        rec.setTemplateRuleName(rec.getTitle());
        rec.setTemplateDescription(rec.getDescription());
        rec.setRuleJson(buildRuleJson(rec));

        return rec;
    }
}
