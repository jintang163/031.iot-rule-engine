package com.iot.ruleengine.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public WebSocketService(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendDeviceStatus(String deviceId, Integer onlineStatus) {
        Map<String, Object> message = new HashMap<>();
        message.put("deviceId", deviceId);
        message.put("onlineStatus", onlineStatus);
        message.put("timestamp", System.currentTimeMillis());
        sendToTopic("/topic/device/status", message);
    }

    public void sendDeviceData(String deviceId, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("deviceId", deviceId);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());
        sendToTopic("/topic/device/data", message);
    }

    public void sendDeviceAlert(String deviceId, String level, String content) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("deviceId", deviceId);
        alert.put("level", level);
        alert.put("message", content);
        alert.put("timestamp", System.currentTimeMillis());
        sendToTopic("/topic/device/alert", alert);
    }

    public void sendToTopic(String destination, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            messagingTemplate.convertAndSend(destination, jsonPayload);
            log.debug("WebSocket消息发送成功, 目的地: {}, 内容: {}", destination, jsonPayload);
        } catch (JsonProcessingException e) {
            log.error("WebSocket消息序列化失败, 目的地: {}", destination, e);
        } catch (Exception e) {
            log.error("WebSocket消息发送失败, 目的地: {}", destination, e);
        }
    }

    public void sendToUser(String userId, String destination, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            messagingTemplate.convertAndSendToUser(userId, destination, jsonPayload);
            log.debug("WebSocket用户消息发送成功, 用户: {}, 目的地: {}, 内容: {}", userId, destination, jsonPayload);
        } catch (JsonProcessingException e) {
            log.error("WebSocket用户消息序列化失败, 用户: {}, 目的地: {}", userId, destination, e);
        } catch (Exception e) {
            log.error("WebSocket用户消息发送失败, 用户: {}, 目的地: {}", userId, destination, e);
        }
    }
}
