package com.iot.ruleengine.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.ruleengine.drools.DeviceData;
import com.iot.ruleengine.engine.RuleEngine;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.repository.DeviceDataRepository;
import com.iot.ruleengine.repository.DeviceRepository;
import com.iot.ruleengine.websocket.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    private static final String KAFKA_TOPIC_TELEMETRY = "iot-device-telemetry";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final DeviceRepository deviceRepository;
    private final DeviceDataRepository deviceDataRepository;
    private final RuleEngine ruleEngine;
    private final WebSocketService webSocketService;

    @Value("${rule.flink.enabled:false}")
    private boolean flinkEnabled;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public DeviceDataHandler(ObjectMapper objectMapper,
                             StringRedisTemplate redisTemplate,
                             DeviceRepository deviceRepository,
                             DeviceDataRepository deviceDataRepository,
                             RuleEngine ruleEngine,
                             WebSocketService webSocketService) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.deviceRepository = deviceRepository;
        this.deviceDataRepository = deviceDataRepository;
        this.ruleEngine = ruleEngine;
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

        com.iot.ruleengine.entity.DeviceData entityData = new com.iot.ruleengine.entity.DeviceData();
        entityData.setDeviceId(deviceId);
        entityData.setTelemetryData(payload);
        entityData.setTelemetry(telemetry);
        entityData.setStatus(1);

        DeviceData factData = new DeviceData(deviceId);
        factData.setTime(LocalTime.now().format(TIME_FORMATTER));

        if (telemetry.containsKey("temperature")) {
            Double temp = ((Number) telemetry.get("temperature")).doubleValue();
            entityData.setTemperature(temp);
            factData.setTemperature(temp);
        }
        if (telemetry.containsKey("humidity")) {
            Double humidity = ((Number) telemetry.get("humidity")).doubleValue();
            entityData.setHumidity(humidity);
            factData.setHumidity(humidity);
        }
        if (telemetry.containsKey("presence")) {
            Boolean presence = telemetry.get("presence") instanceof Boolean ?
                    (Boolean) telemetry.get("presence") :
                    "true".equalsIgnoreCase(String.valueOf(telemetry.get("presence")));
            factData.setPresence(presence);
        }
        if (telemetry.containsKey("pressure")) {
            entityData.setPressure(((Number) telemetry.get("pressure")).doubleValue());
        }
        if (telemetry.containsKey("batteryLevel")) {
            entityData.setBatteryLevel(((Number) telemetry.get("batteryLevel")).intValue());
        }

        for (Map.Entry<String, Object> entry : telemetry.entrySet()) {
            factData.addAttribute(entry.getKey(), entry.getValue());
        }

        updateRedisDeviceData(deviceId, payload);
        updateDeviceOnlineStatus(deviceId);
        saveDeviceData(entityData);

        log.debug("开始规则匹配, deviceId={}, temp={}, presence={}", deviceId, factData.getTemperature(), factData.getPresence());
        if (flinkEnabled && kafkaTemplate != null) {
            try {
                String kafkaPayload = objectMapper.writeValueAsString(factData);
                kafkaTemplate.send(KAFKA_TOPIC_TELEMETRY, deviceId, kafkaPayload);
                log.debug("Flink模式：设备数据已投递Kafka等待Flink处理, deviceId={}", deviceId);
            } catch (Exception e) {
                log.error("Flink模式：投递Kafka失败，降级为本地规则评估, deviceId={}", deviceId, e);
                ruleEngine.evaluate(factData);
            }
        } else {
            ruleEngine.evaluate(factData);
        }

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

        com.iot.ruleengine.entity.DeviceData entityData = new com.iot.ruleengine.entity.DeviceData();
        entityData.setDeviceId(deviceId);
        entityData.setDeviceStatus(payload);
        entityData.setStatus(onlineStatus);

        DeviceData factData = new DeviceData(deviceId);
        factData.setTime(LocalTime.now().format(TIME_FORMATTER));
        if (statusMap.containsKey("presence")) {
            Boolean presence = statusMap.get("presence") instanceof Boolean ?
                    (Boolean) statusMap.get("presence") :
                    "true".equalsIgnoreCase(String.valueOf(statusMap.get("presence")));
            factData.setPresence(presence);
        }
        for (Map.Entry<String, Object> entry : statusMap.entrySet()) {
            factData.addAttribute(entry.getKey(), entry.getValue());
        }

        if (flinkEnabled && kafkaTemplate != null) {
            try {
                String kafkaPayload = objectMapper.writeValueAsString(factData);
                kafkaTemplate.send(KAFKA_TOPIC_TELEMETRY, deviceId, kafkaPayload);
                log.debug("Flink模式：设备状态数据已投递Kafka等待Flink处理, deviceId={}", deviceId);
            } catch (Exception e) {
                log.error("Flink模式：投递Kafka失败，降级为本地规则评估, deviceId={}", deviceId, e);
                ruleEngine.evaluate(factData);
            }
        } else {
            ruleEngine.evaluate(factData);
        }
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
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Device> updateWrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            updateWrapper.eq("device_id", deviceId)
                    .set("online", 1)
                    .set("last_online_time", LocalDateTime.now());
            deviceRepository.update(null, updateWrapper);
            log.debug("数据库设备在线状态更新成功, deviceId: {}", deviceId);
        } catch (Exception e) {
            log.error("数据库设备在线状态更新失败, deviceId: {}", deviceId, e);
        }
    }

    private void saveDeviceData(com.iot.ruleengine.entity.DeviceData deviceData) {
        try {
            deviceDataRepository.insert(deviceData);
            log.debug("设备数据保存成功, deviceId: {}", deviceData.getDeviceId());
        } catch (Exception e) {
            log.error("设备数据保存失败, deviceId: {}", deviceData.getDeviceId(), e);
        }
    }
}
