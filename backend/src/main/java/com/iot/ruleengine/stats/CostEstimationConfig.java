package com.iot.ruleengine.stats;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rule.cost")
public class CostEstimationConfig {

    private BigDecimal electricityPricePerKwh = new BigDecimal("0.61");

    private Map<String, BigDecimal> devicePowerKw = new HashMap<String, BigDecimal>() {{
        put("aircon", new BigDecimal("1.5"));
        put("air_conditioner", new BigDecimal("1.5"));
        put("ac", new BigDecimal("1.5"));
        put("turn_on_aircon", new BigDecimal("1.5"));
        put("heater", new BigDecimal("2.0"));
        put("turn_on_heater", new BigDecimal("2.0"));
        put("light", new BigDecimal("0.06"));
        put("turn_on_light", new BigDecimal("0.06"));
        put("fan", new BigDecimal("0.05"));
        put("turn_on_fan", new BigDecimal("0.05"));
        put("humidifier", new BigDecimal("0.03"));
        put("dehumidifier", new BigDecimal("0.3"));
        put("default", new BigDecimal("0.1"));
    }};

    private Map<String, BigDecimal> runtimeMinutesByAction = new HashMap<String, BigDecimal>() {{
        put("aircon", new BigDecimal("60"));
        put("air_conditioner", new BigDecimal("60"));
        put("turn_on_aircon", new BigDecimal("60"));
        put("ac", new BigDecimal("60"));
        put("heater", new BigDecimal("45"));
        put("turn_on_heater", new BigDecimal("45"));
        put("light", new BigDecimal("120"));
        put("turn_on_light", new BigDecimal("120"));
        put("fan", new BigDecimal("60"));
        put("turn_on_fan", new BigDecimal("60"));
        put("humidifier", new BigDecimal("30"));
        put("dehumidifier", new BigDecimal("30"));
        put("default", new BigDecimal("30"));
    }};

    private BigDecimal defaultRuntimeMinutes = new BigDecimal("30");

    public BigDecimal getPowerKw(String actionType) {
        if (actionType == null) {
            return devicePowerKw.getOrDefault("default", new BigDecimal("0.1"));
        }
        String key = actionType.toLowerCase();
        if (devicePowerKw.containsKey(key)) {
            return devicePowerKw.get(key);
        }
        for (Map.Entry<String, BigDecimal> entry : devicePowerKw.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return devicePowerKw.getOrDefault("default", new BigDecimal("0.1"));
    }

    public BigDecimal getRuntimeMinutes(String actionType) {
        if (actionType == null) {
            return runtimeMinutesByAction.getOrDefault("default", defaultRuntimeMinutes);
        }
        String key = actionType.toLowerCase();
        if (runtimeMinutesByAction.containsKey(key)) {
            return runtimeMinutesByAction.get(key);
        }
        for (Map.Entry<String, BigDecimal> entry : runtimeMinutesByAction.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return runtimeMinutesByAction.getOrDefault("default", defaultRuntimeMinutes);
    }

    public boolean isEnergyConsumingAction(String actionType) {
        if (actionType == null) {
            return false;
        }
        String key = actionType.toLowerCase();
        return key.contains("on") || key.contains("start") || key.contains("run")
                || key.contains("aircon") || key.contains("heater") || key.contains("light")
                || key.contains("fan") || key.contains("humidifier");
    }
}
