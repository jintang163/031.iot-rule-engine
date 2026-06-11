package com.iot.ruleengine.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.entity.DeviceData;
import com.iot.ruleengine.repository.DeviceDataRepository;
import com.iot.ruleengine.repository.DeviceRepository;
import com.iot.ruleengine.service.RuleEngineService;
import com.iot.ruleengine.websocket.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DeviceDataHandler implements MessageHandler {

    private static final Pattern TOPIC_PATTERN = Pattern.compile("iot/device/([^/]+)/(telemetry|status)");
    private static final String DEVICE_STATUS_KEY_PREFIX = "iot:device:status:";
    private static final String DEVICE_DATA_KEY_PREFIX = "iot:device:data:";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final DeviceRepository deviceRepository;
    private final DeviceDataRepository deviceDataRepository;
    private final RuleEngineService ruleEngineService;
    private final WebSocketService webSocketService;

    @Autowired
    public DeviceDataHandler(ObjectMapper objectMapper,
                             StringRedisTemplate redisTemplate,
                             DeviceRepository deviceRepository,
                             DeviceDataRepository deviceDataRepository,
                             RuleEngineService ruleEngineService,
                             WebSocketService webSocketService) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.deviceRepository = deviceRepository;
        this.deviceDataRepository = deviceDataRepository;
        this.ruleEngineService = ruleEngineService;
        this.webSocketService = webSocketService;
    }

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = message.getPayload().toString();

        log.debug("收到MQTT消息, 主题: {}, 内容: {}", topic, payload);

        Matcher matcher = TOPIC_PATTERN.matcher(topic);
        if (!matcher.matches()) {
            log.warn("无法识别的MQTT主题格式: {}", topic);
            return;
        }

        String deviceId = matcher.group(1);
        String messageType = matcher.group(2);

        try {
            if ("telemetry".equals(messageType)) {
                handleTelemetry(deviceId, payload);
            } else if ("status".equals(messageType)) {
                handleStatus(deviceId, payload);
            }
        } catch (Exception e) {
            log.error("处理设备消息异常, deviceId: {}, 主题: {}", deviceId, topic, e);
        }
    }

    private void handleTelemetry(String deviceId, String payload) throws Exception {
        Map<String, Object> telemetry = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});

        DeviceData deviceData = new DeviceData();
        deviceData.setDeviceId(deviceId);
        deviceData.setTelemetryData(payload);
        deviceData.setTelemetry(telemetry);
        deviceData.setStatus(1);

        if (telemetry.containsKey("temperature")) {
            deviceData.setTemperature(((Number) telemetry.get("temperature")).doubleValue());
        }
        if (telemetry.containsKey("humidity")) {
            deviceData.setHumidity(((Number) telemetry.get("humidity")).doubleValue());
        }
        if (telemetry.containsKey("pressure")) {
            deviceData.setPressure(((Number) telemetry.get("pressure")).doubleValue());
        }
        if (telemetry.containsKey("batteryLevel")) {
            deviceData.setBatteryLevel(((Number) telemetry.get("batteryLevel")).intValue());
        }

        updateRedisDeviceData(deviceId, payload);
        updateDeviceOnlineStatus(deviceId);
        saveDeviceData(deviceData);
        ruleEngineService.executeRules(deviceData);
        webSocketService.sendDeviceData(deviceId, telemetry);
    }

    private void handleStatus(String deviceId, String payload) throws Exception {
        Map<String, Object> statusMap = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        Integer onlineStatus = statusMap.containsKey("online") ?
                ((Boolean) statusMap.get("online") ? 1 : 0) :
                (statusMap.containsKey("status") ? ((Number) statusMap.get("status")).intValue() : 1);

        updateRedisDeviceStatus(deviceId, onlineStatus, payload);
        updateDeviceOnlineStatus(deviceId);
        webSocketService.sendDeviceStatus(deviceId, onlineStatus);

        DeviceData deviceData = new DeviceData();
        deviceData.setDeviceId(deviceId);
        deviceData.setDeviceStatus(payload);
        deviceData.setStatus(onlineStatus);
        ruleEngineService.executeRules(deviceData);
    }

    private void updateRedisDeviceData(String deviceId, String payload) {
        String key = DEVICE_DATA_KEY_PREFIX + deviceId;
        try {
            redisTemplate.opsForValue().set(key, payload, 24, TimeUnit.HOURS);
            log.debug("Redis设备数据更新成功, deviceId: {}", deviceId);
        } catch (Exception e) {
            log.error("Redis设备数据更新失败, deviceId: {}", deviceId, e);
        }
    }

    private void updateRedisDeviceStatus(String deviceId, Integer onlineStatus, String payload) {
        String key = DEVICE_STATUS_KEY_PREFIX + deviceId;
        try {
            Map<String, Object> statusData = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
            statusData.put("updateTime", System.currentTimeMillis());
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(statusData), 24, TimeUnit.HOURS);
            log.debug("Redis设备状态更新成功, deviceId: {}, onlineStatus: {}", deviceId, onlineStatus);
        } catch (Exception e) {
            log.error("Redis设备状态更新失败, deviceId: {}", deviceId, e);
        }
    }

    private void updateDeviceOnlineStatus(String deviceId) {
        try {
            deviceRepository.updateOnlineStatus(deviceId, 1, LocalDateTime.now());
            log.debug("数据库设备在线状态更新成功, deviceId: {}", deviceId);
        } catch (Exception e) {
            log.error("数据库设备在线状态更新失败, deviceId: {}", deviceId, e);
        }
    }

    private void saveDeviceData(DeviceData deviceData) {
        try {
            deviceDataRepository.insert(deviceData);
            log.debug("设备数据保存成功, deviceId: {}", deviceData.getDeviceId());
        } catch (Exception e) {
            log.error("设备数据保存失败, deviceId: {}", deviceData.getDeviceId(), e);
        }
    }
}
