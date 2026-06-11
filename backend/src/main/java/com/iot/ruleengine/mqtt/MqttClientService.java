package com.iot.ruleengine.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MqttClientService {

    private final MessageChannel mqttOutboundChannel;

    @Autowired
    public MqttClientService(MessageChannel mqttOutboundChannel) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    public void publish(String topic, String payload) {
        try {
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .build());
            log.info("MQTT消息发布成功, 主题: {}, 内容: {}", topic, payload);
        } catch (Exception e) {
            log.error("MQTT消息发布失败, 主题: {}, 内容: {}", topic, payload, e);
            throw new RuntimeException("MQTT消息发布失败: " + e.getMessage(), e);
        }
    }

    public void publishCommand(String deviceId, String payload) {
        String topic = "iot/device/" + deviceId + "/command";
        publish(topic, payload);
    }

    public void subscribe(String topic) {
        log.info("订阅MQTT主题: {}", topic);
    }
}
