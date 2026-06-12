package com.iot.ruleengine.simulator;

import com.alibaba.fastjson.JSON;
import com.iot.ruleengine.dto.DeviceSimulatorConfig;
import com.iot.ruleengine.dto.DeviceSimulatorStatus;
import com.iot.ruleengine.mqtt.MqttClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DeviceSimulatorService {

    private final MqttClientService mqttClientService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private final Map<String, SimulatorTask> runningTasks = new ConcurrentHashMap<>();

    private final Map<String, DeviceSimulatorConfig> configs = new ConcurrentHashMap<>();

    @Autowired
    public DeviceSimulatorService(MqttClientService mqttClientService) {
        this.mqttClientService = mqttClientService;
    }

    @PostConstruct
    public void init() {
        log.info("设备模拟器服务已启动");
    }

    @PreDestroy
    public void destroy() {
        log.info("设备模拟器服务正在关闭，停止所有模拟任务");
        runningTasks.values().forEach(SimulatorTask::stop);
        runningTasks.clear();
        scheduler.shutdown();
    }

    public DeviceSimulatorStatus startSimulator(DeviceSimulatorConfig config) {
        if (config.getDeviceId() == null || config.getDeviceId().isEmpty()) {
            throw new IllegalArgumentException("设备ID不能为空");
        }

        stopSimulator(config.getDeviceId());

        config.setEnabled(true);
        configs.put(config.getDeviceId(), config);

        SimulatorTask task = new SimulatorTask(config, mqttClientService);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                task,
                0,
                config.getIntervalSeconds(),
                TimeUnit.SECONDS
        );
        task.setFuture(future);
        runningTasks.put(config.getDeviceId(), task);

        log.info("设备模拟器已启动: deviceId={}, interval={}s",
                config.getDeviceId(), config.getIntervalSeconds());

        return getStatus(config.getDeviceId());
    }

    public DeviceSimulatorStatus stopSimulator(String deviceId) {
        SimulatorTask task = runningTasks.remove(deviceId);
        if (task != null) {
            task.stop();
            log.info("设备模拟器已停止: deviceId={}", deviceId);
        }

        DeviceSimulatorConfig config = configs.get(deviceId);
        if (config != null) {
            config.setEnabled(false);
        }

        return getStatus(deviceId);
    }

    public DeviceSimulatorStatus getStatus(String deviceId) {
        DeviceSimulatorStatus status = new DeviceSimulatorStatus();
        status.setDeviceId(deviceId);

        SimulatorTask task = runningTasks.get(deviceId);
        DeviceSimulatorConfig config = configs.get(deviceId);

        if (config != null) {
            status.setDeviceType(config.getDeviceType());
            status.setIntervalSeconds(config.getIntervalSeconds());
        }

        if (task != null) {
            status.setRunning(task.isRunning());
            status.setLastTemperature(task.getLastTemperature());
            status.setLastHumidity(task.getLastHumidity());
            status.setLastReportTime(task.getLastReportTime());
            status.setReportCount(task.getReportCount());
        } else {
            status.setRunning(false);
            status.setReportCount(0L);
        }

        return status;
    }

    public Map<String, DeviceSimulatorStatus> getAllStatus() {
        Map<String, DeviceSimulatorStatus> result = new HashMap<>();
        configs.keySet().forEach(deviceId -> result.put(deviceId, getStatus(deviceId)));
        return result;
    }

    public DeviceSimulatorConfig getConfig(String deviceId) {
        return configs.get(deviceId);
    }

    private static class SimulatorTask implements Runnable {

        private final DeviceSimulatorConfig config;
        private final MqttClientService mqttClientService;
        private volatile boolean running = true;
        private ScheduledFuture<?> future;
        private Double lastTemperature;
        private Double lastHumidity;
        private LocalDateTime lastReportTime;
        private long reportCount = 0;

        public SimulatorTask(DeviceSimulatorConfig config, MqttClientService mqttClientService) {
            this.config = config;
            this.mqttClientService = mqttClientService;
        }

        public void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        public void stop() {
            this.running = false;
            if (future != null && !future.isDone()) {
                future.cancel(false);
            }
        }

        public boolean isRunning() {
            return running;
        }

        public Double getLastTemperature() {
            return lastTemperature;
        }

        public Double getLastHumidity() {
            return lastHumidity;
        }

        public LocalDateTime getLastReportTime() {
            return lastReportTime;
        }

        public long getReportCount() {
            return reportCount;
        }

        @Override
        public void run() {
            if (!running) {
                return;
            }

            try {
                double temperature = config.getMinTemperature() +
                        Math.random() * (config.getMaxTemperature() - config.getMinTemperature());
                double humidity = config.getMinHumidity() +
                        Math.random() * (config.getMaxHumidity() - config.getMinHumidity());

                temperature = Math.round(temperature * 10.0) / 10.0;
                humidity = Math.round(humidity * 10.0) / 10.0;

                Map<String, Object> payload = new HashMap<>();
                payload.put("ts", System.currentTimeMillis());
                payload.put("deviceId", config.getDeviceId());
                payload.put("temperature", temperature);
                payload.put("humidity", humidity);

                String topic = "iot/device/" + config.getDeviceId() + "/telemetry";
                String jsonPayload = JSON.toJSONString(payload);

                mqttClientService.publish(topic, jsonPayload);

                lastTemperature = temperature;
                lastHumidity = humidity;
                lastReportTime = LocalDateTime.now();
                reportCount++;

                log.debug("模拟器上报数据: deviceId={}, temp={}, humidity={}",
                        config.getDeviceId(), temperature, humidity);

            } catch (Exception e) {
                log.error("模拟器上报数据失败: deviceId={}", config.getDeviceId(), e);
            }
        }
    }
}
