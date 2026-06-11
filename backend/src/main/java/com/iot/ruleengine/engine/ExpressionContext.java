package com.iot.ruleengine.engine;

import com.iot.ruleengine.drools.DeviceData;

import java.util.HashMap;
import java.util.Map;

public class ExpressionContext {

    private ExpressionContext() {
    }

    public static Map<String, Object> toEnvMap(DeviceData data) {
        Map<String, Object> env = new HashMap<>();
        if (data == null) {
            return env;
        }

        env.put("deviceId", data.getDeviceId());
        env.put("temperature", data.getTemperature());
        env.put("humidity", data.getHumidity());
        env.put("presence", data.getPresence());
        env.put("time", data.getTime());
        env.put("attributes", data.getAttributes());

        return env;
    }
}
