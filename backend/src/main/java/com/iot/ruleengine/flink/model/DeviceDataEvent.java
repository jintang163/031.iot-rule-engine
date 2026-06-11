package com.iot.ruleengine.flink.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDataEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;

    private Double temperature;

    private Double humidity;

    private Boolean presence;

    private String time;

    private String deviceStatus;

    private Map<String, Object> attributes = new HashMap<>();

    private long timestamp;

    private String topic;

    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("deviceId", deviceId);
        map.put("temperature", temperature);
        map.put("humidity", humidity);
        map.put("presence", presence);
        map.put("time", time);
        map.put("deviceStatus", deviceStatus);
        map.put("timestamp", timestamp);
        if (attributes != null) {
            map.putAll(attributes);
        }
        return map;
    }
}
