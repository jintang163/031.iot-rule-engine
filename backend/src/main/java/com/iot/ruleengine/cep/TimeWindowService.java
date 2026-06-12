package com.iot.ruleengine.cep;

import com.iot.ruleengine.drools.DeviceData;
import com.iot.ruleengine.entity.Rule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TimeWindowService {

    private final Map<String, Deque<WindowDataPoint>> windowDataStore = new ConcurrentHashMap<>();

    public static class WindowDataPoint {
        private final long timestamp;
        private final Double value;

        public WindowDataPoint(long timestamp, Double value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Double getValue() {
            return value;
        }
    }

    public static class WindowResult {
        private final boolean conditionMet;
        private final Double aggregatedValue;
        private final int dataPointCount;
        private final String windowType;
        private final String aggregation;
        private final long windowDuration;

        public WindowResult(boolean conditionMet, Double aggregatedValue, int dataPointCount,
                           String windowType, String aggregation, long windowDuration) {
            this.conditionMet = conditionMet;
            this.aggregatedValue = aggregatedValue;
            this.dataPointCount = dataPointCount;
            this.windowType = windowType;
            this.aggregation = aggregation;
            this.windowDuration = windowDuration;
        }

        public boolean isConditionMet() {
            return conditionMet;
        }

        public Double getAggregatedValue() {
            return aggregatedValue;
        }

        public int getDataPointCount() {
            return dataPointCount;
        }

        public String getWindowType() {
            return windowType;
        }

        public String getAggregation() {
            return aggregation;
        }

        public long getWindowDuration() {
            return windowDuration;
        }
    }

    public void recordDataPoint(String deviceId, String field, Double value) {
        String key = buildKey(deviceId, field);
        long now = System.currentTimeMillis();

        windowDataStore.compute(key, (k, deque) -> {
            if (deque == null) {
                deque = new LinkedList<>();
            }
            deque.addLast(new WindowDataPoint(now, value));
            return deque;
        });

        log.debug("记录时间窗口数据点: deviceId={}, field={}, value={}", deviceId, field, value);
    }

    public WindowResult evaluateWindow(Rule rule, DeviceData data) {
        if (rule.getWindowEnabled() == null || rule.getWindowEnabled() != 1) {
            return new WindowResult(true, null, 0, null, null, 0);
        }

        String field = rule.getWindowField();
        Double fieldValue = extractFieldValue(data, field);
        if (fieldValue == null) {
            log.debug("无法提取字段值: deviceId={}, field={}", data.getDeviceId(), field);
            return new WindowResult(false, null, 0, rule.getWindowType(), rule.getWindowAggregation(),
                    rule.getWindowDuration() != null ? rule.getWindowDuration() : 0);
        }

        recordDataPoint(data.getDeviceId(), field, fieldValue);

        String key = buildKey(data.getDeviceId(), field);
        Deque<WindowDataPoint> deque = windowDataStore.get(key);

        int windowSeconds = rule.getWindowDuration() != null ? rule.getWindowDuration() : 60;
        long windowMs = TimeUnit.SECONDS.toMillis(windowSeconds);
        long cutoffTime = System.currentTimeMillis() - windowMs;

        if (deque != null) {
            while (!deque.isEmpty() && deque.peekFirst().getTimestamp() < cutoffTime) {
                deque.pollFirst();
            }
        }

        if (deque == null || deque.isEmpty()) {
            return new WindowResult(false, null, 0, rule.getWindowType(), rule.getWindowAggregation(), windowSeconds);
        }

        List<Double> values = deque.stream()
                .map(WindowDataPoint::getValue)
                .collect(Collectors.toList());

        Double aggregatedValue = calculateAggregation(values, rule.getWindowAggregation(), rule.getWindowType());
        if (aggregatedValue == null) {
            return new WindowResult(false, null, values.size(), rule.getWindowType(), rule.getWindowAggregation(), windowSeconds);
        }

        boolean conditionMet = compareWithThreshold(aggregatedValue, rule.getWindowOperator(), rule.getWindowThreshold());

        log.debug("时间窗口评估: ruleId={}, deviceId={}, field={}, aggregation={}, value={}, threshold={}, met={}",
                rule.getId(), data.getDeviceId(), field, rule.getWindowAggregation(),
                aggregatedValue, rule.getWindowThreshold(), conditionMet);

        return new WindowResult(conditionMet, aggregatedValue, values.size(),
                rule.getWindowType(), rule.getWindowAggregation(), windowSeconds);
    }

    public void cleanupExpiredData() {
        long oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        for (Map.Entry<String, Deque<WindowDataPoint>> entry : windowDataStore.entrySet()) {
            Deque<WindowDataPoint> deque = entry.getValue();
            while (!deque.isEmpty() && deque.peekFirst().getTimestamp() < oneDayAgo) {
                deque.pollFirst();
            }
            if (deque.isEmpty()) {
                windowDataStore.remove(entry.getKey());
            }
        }
    }

    public void clearDeviceData(String deviceId) {
        Set<String> keysToRemove = windowDataStore.keySet().stream()
                .filter(key -> key.startsWith(deviceId + ":"))
                .collect(Collectors.toSet());
        keysToRemove.forEach(windowDataStore::remove);
    }

    public Map<String, Object> getWindowStats(String deviceId, String field) {
        String key = buildKey(deviceId, field);
        Deque<WindowDataPoint> deque = windowDataStore.get(key);
        Map<String, Object> stats = new HashMap<>();
        if (deque != null) {
            stats.put("count", deque.size());
            if (!deque.isEmpty()) {
                stats.put("oldest", new Date(deque.peekFirst().getTimestamp()));
                stats.put("newest", new Date(deque.peekLast().getTimestamp()));
            }
        } else {
            stats.put("count", 0);
        }
        return stats;
    }

    private String buildKey(String deviceId, String field) {
        return deviceId + ":" + field;
    }

    private Double extractFieldValue(DeviceData data, String field) {
        if (data == null || field == null) {
            return null;
        }
        switch (field.toLowerCase()) {
            case "temperature":
                return data.getTemperature();
            case "humidity":
                return data.getHumidity();
            default:
                Object attr = data.getAttribute(field);
                if (attr instanceof Number) {
                    return ((Number) attr).doubleValue();
                }
                return null;
        }
    }

    private Double calculateAggregation(List<Double> values, String aggregation, String windowType) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        if (aggregation == null) {
            aggregation = "AVG";
        }

        switch (aggregation.toUpperCase()) {
            case "SUM":
                return values.stream().mapToDouble(Double::doubleValue).sum();
            case "AVG":
                return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "MAX":
                return values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case "MIN":
                return values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "COUNT":
                return (double) values.size();
            case "DELTA":
            case "DIFF":
                if (values.size() < 2) {
                    return null;
                }
                return values.get(values.size() - 1) - values.get(0);
            default:
                return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
    }

    private boolean compareWithThreshold(Double value, String operator, java.math.BigDecimal threshold) {
        if (value == null || threshold == null) {
            return false;
        }
        if (operator == null) {
            operator = ">";
        }

        double thresholdValue = threshold.doubleValue();

        switch (operator) {
            case ">":
                return value > thresholdValue;
            case "<":
                return value < thresholdValue;
            case ">=":
                return value >= thresholdValue;
            case "<=":
                return value <= thresholdValue;
            case "==":
            case "=":
                return Math.abs(value - thresholdValue) < 0.0001;
            case "!=":
                return Math.abs(value - thresholdValue) >= 0.0001;
            default:
                return value > thresholdValue;
        }
    }
}
