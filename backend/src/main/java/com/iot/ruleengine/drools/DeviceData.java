package com.iot.ruleengine.drools;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class DeviceData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;

    private Double temperature;

    private Double humidity;

    private Boolean presence;

    private String time;

    private Map<String, Object> attributes = new HashMap<>();

    public DeviceData() {
    }

    public DeviceData(String deviceId) {
        this.deviceId = deviceId;
    }

    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }
}
