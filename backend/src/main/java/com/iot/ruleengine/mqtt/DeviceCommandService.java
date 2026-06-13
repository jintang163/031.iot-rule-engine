package com.iot.ruleengine.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.ruleengine.entity.ActionLog;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.history.RuleTriggerHistory;
import com.iot.ruleengine.repository.ActionLogRepository;
import com.iot.ruleengine.repository.DeviceRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DeviceCommandService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final String DEVICE_STATUS_KEY_PREFIX = "iot:device:status:";

    private final MqttClientService mqttClientService;
    private final DeviceRepository deviceRepository;
    private final ActionLogRepository actionLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public DeviceCommandService(MqttClientService mqttClientService,
                                DeviceRepository deviceRepository,
                                ActionLogRepository actionLogRepository,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper) {
        this.mqttClientService = mqttClientService;
        this.deviceRepository = deviceRepository;
        this.actionLogRepository = actionLogRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionExecutionResult {
        private String actionType;
        private String targetDeviceId;
        private Map<String, Object> params;
        private boolean success;
        private String errorMsg;
        private Integer resultCode;
    }

    @Transactional(rollbackFor = Exception.class)
    public void sendCommand(String deviceId, String action, Map<String, Object> params) {
        sendCommand(deviceId, action, params, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void sendCommand(String deviceId, String action, Map<String, Object> params, Long ruleId, String ruleName) {
        sendCommandWithResult(deviceId, action, params, ruleId, ruleName);
    }

    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult sendCommandWithResult(String deviceId, String action, Map<String, Object> params,
                                                       Long ruleId, String ruleName) {
        log.info("发送设备控制指令, deviceId: {}, action: {}, params: {}, ruleId: {}, ruleName: {}",
                deviceId, action, params, ruleId, ruleName);

        Map<String, Object> paramsCopy = params != null ? new HashMap<>(params) : null;
        ActionExecutionResult.ActionExecutionResultBuilder resultBuilder = ActionExecutionResult.builder()
                .actionType(action)
                .targetDeviceId(deviceId)
                .params(paramsCopy);

        boolean isOnline = checkDeviceOnline(deviceId);
        ActionLog actionLog = createActionLog(deviceId, action, params, ruleId, ruleName);

        try {
            String commandPayload = buildCommandPayload(deviceId, action, params);
            mqttClientService.publishCommand(deviceId, commandPayload);

            actionLog.setResult(1);
            actionLog.setExecuteTime(LocalDateTime.now());
            actionLogRepository.insert(actionLog);

            resultBuilder.success(true).resultCode(1);
            log.info("设备控制指令发送成功, deviceId: {}, action: {}, ruleId: {}", deviceId, action, ruleId);
        } catch (Exception e) {
            log.error("设备控制指令发送失败, deviceId: {}, action: {}, ruleId: {}", deviceId, action, ruleId, e);

            actionLog.setResult(0);
            actionLog.setErrorMsg(e.getMessage());
            actionLogRepository.insert(actionLog);

            resultBuilder.success(false).resultCode(0).errorMsg(e.getMessage());

            if (!isOnline) {
                log.warn("设备离线, 指令将进入重试队列, deviceId: {}", deviceId);
            }
        }

        return resultBuilder.build();
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void retryFailedCommands() {
        log.debug("开始重试失败的设备控制指令");

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ActionLog> queryWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("result", 0)
                .lt("retry_count", MAX_RETRY_COUNT)
                .orderByAsc("execute_time");

        List<ActionLog> failedCommands = actionLogRepository.selectList(queryWrapper);
        if (failedCommands == null || failedCommands.isEmpty()) {
            return;
        }

        log.info("找到{}条需要重试的失败指令", failedCommands.size());

        for (ActionLog actionLog : failedCommands) {
            if (actionLog.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("指令已达到最大重试次数, actionLogId: {}, deviceId: {}",
                        actionLog.getId(), actionLog.getDeviceId());
                continue;
            }

            retryCommand(actionLog);
        }
    }

    private void retryCommand(ActionLog actionLog) {
        log.info("重试设备指令, actionLogId: {}, deviceId: {}, action: {}, 重试次数: {}/{}",
                actionLog.getId(), actionLog.getDeviceId(), actionLog.getActionType(),
                actionLog.getRetryCount() + 1, MAX_RETRY_COUNT);

        try {
            Map<String, Object> params = objectMapper.readValue(actionLog.getActionParams(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

            String commandPayload = buildCommandPayload(actionLog.getDeviceId(), actionLog.getActionType(), params);
            mqttClientService.publishCommand(actionLog.getDeviceId(), commandPayload);

            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ActionLog> updateWrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            updateWrapper.eq("id", actionLog.getId())
                    .set("result", 1)
                    .set("error_msg", null)
                    .setSql("retry_count = retry_count + 1");
            actionLogRepository.update(null, updateWrapper);

            log.info("设备指令重试成功, actionLogId: {}, deviceId: {}", actionLog.getId(), actionLog.getDeviceId());
        } catch (Exception e) {
            log.error("设备指令重试失败, actionLogId: {}, deviceId: {}", actionLog.getId(), actionLog.getDeviceId(), e);
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ActionLog> updateWrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            updateWrapper.eq("id", actionLog.getId())
                    .set("result", 0)
                    .set("error_msg", e.getMessage())
                    .setSql("retry_count = retry_count + 1");
            actionLogRepository.update(null, updateWrapper);
        }
    }

    private boolean checkDeviceOnline(String deviceId) {
        try {
            String key = DEVICE_STATUS_KEY_PREFIX + deviceId;
            String statusJson = redisTemplate.opsForValue().get(key);
            if (statusJson != null) {
                Map<String, Object> statusData = objectMapper.readValue(statusJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                Object online = statusData.get("online");
                if (online instanceof Boolean) {
                    return (Boolean) online;
                }
                if (online instanceof Number) {
                    return ((Number) online).intValue() == 1;
                }
            }
        } catch (Exception e) {
            log.error("检查设备在线状态失败, deviceId: {}", deviceId, e);
        }

        try {
            Device device = deviceRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Device>()
                            .eq("device_id", deviceId));
            if (device != null) {
                return device.getOnline() != null && device.getOnline() == 1;
            }
        } catch (Exception e) {
            log.error("从数据库检查设备在线状态失败, deviceId: {}", deviceId, e);
        }

        return false;
    }

    private ActionLog createActionLog(String deviceId, String action, Map<String, Object> params) {
        return createActionLog(deviceId, action, params, null, null);
    }

    private ActionLog createActionLog(String deviceId, String action, Map<String, Object> params, Long ruleId, String ruleName) {
        ActionLog actionLog = new ActionLog();
        actionLog.setDeviceId(deviceId);
        actionLog.setActionType(action);
        actionLog.setRuleId(ruleId);
        actionLog.setRuleName(ruleName);
        try {
            actionLog.setActionParams(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            actionLog.setActionParams("{}");
        }
        actionLog.setResult(0);
        actionLog.setRetryCount(0);
        return actionLog;
    }

    private String buildCommandPayload(String deviceId, String action, Map<String, Object> params) throws Exception {
        Map<String, Object> command = new HashMap<>();
        command.put("deviceId", deviceId);
        command.put("action", action);
        command.put("params", params != null ? params : new HashMap<>());
        command.put("timestamp", System.currentTimeMillis());
        command.put("messageId", java.util.UUID.randomUUID().toString().replace("-", ""));
        return objectMapper.writeValueAsString(command);
    }
}
