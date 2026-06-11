package com.iot.ruleengine.flink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "flink")
public class FlinkConfigProperties {

    private boolean enabled = false;

    private String mode = "embedded";

    private int parallelism = 2;

    private long checkpointInterval = 60000;

    private MqttConfig mqtt = new MqttConfig();

    @Data
    public static class MqttConfig {
        private List<String> sourceTopics;
        private String sinkTopicPrefix = "iot/device/";
        private String sinkTopicSuffix = "/command";
        private String hostUrl = "tcp://localhost:1883";
        private String username = "admin";
        private String password = "public";
        private int timeout = 30;
        private int keepAlive = 60;
    }
}
