package com.iot.ruleengine.simulator;

import com.alibaba.fastjson.JSON;
import com.iot.ruleengine.dto.DeviceSimulatorConfig;
import com.iot.ruleengine.dto.DeviceSimulatorStatus;
import com.iot.ruleengine.mqtt.DeviceDataHandler;
import com.iot.ruleengine.mqtt.MqttClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
    private final DeviceDataHandler deviceDataHandler;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private final Map<String, SimulatorTask> runningTasks = new ConcurrentHashMap<>();

    private final Map<String, DeviceSimulatorConfig> configs = new ConcurrentHashMap<>();

    @Autowired
    public DeviceSimulatorService(MqttClientService mqttClientService, DeviceDataHandler deviceDataHandler) {
        this.mqttClientService = mqttClientService;
        this.deviceDataHandler = deviceDataHandler;
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
        if (config.getProtocol() == null) {
            config.setProtocol("MQTT");
        }
        configs.put(config.getDeviceId(), config);

        SimulatorTask task = new SimulatorTask(config, mqttClientService, deviceDataHandler,
                restTemplate, serverPort, contextPath);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                task,
                0,
                config.getIntervalSeconds(),
                TimeUnit.SECONDS
        );
        task.setFuture(future);
        runningTasks.put(config.getDeviceId(), task);

        log.info("设备模拟器已启动: deviceId={}, protocol={}, interval={}s",
                config.getDeviceId(), config.getProtocol(), config.getIntervalSeconds());

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
            status.setProtocol(config.getProtocol());
            status.setHttpUrl(config.getHttpUrl());
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
        private final DeviceDataHandler deviceDataHandler;
        private final RestTemplate restTemplate;
        private final String serverPort;
        private final String contextPath;
        private volatile boolean running = true;
        private ScheduledFuture<?> future;
        private Double lastTemperature;
        private Double lastHumidity;
        private LocalDateTime lastReportTime;
        private long reportCount = 0;

        public SimulatorTask(DeviceSimulatorConfig config,
                             MqttClientService mqttClientService,
                             DeviceDataHandler deviceDataHandler,
                             RestTemplate restTemplate,
                             String serverPort,
                             String contextPath) {
            this.config = config;
            this.mqttClientService = mqttClientService;
            this.deviceDataHandler = deviceDataHandler;
            this.restTemplate = restTemplate;
            this.serverPort = serverPort;
            this.contextPath = contextPath;
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

                String jsonPayload = JSON.toJSONString(payload);

                if ("HTTP".equalsIgnoreCase(config.getProtocol())) {
                    sendViaHttp(jsonPayload);
                } else {
                    sendViaMqtt(jsonPayload);
                }

                lastTemperature = temperature;
                lastHumidity = humidity;
                lastReportTime = LocalDateTime.now();
                reportCount++;

                log.debug("模拟器上报数据: deviceId={}, protocol={}, temp={}, humidity={}",
                        config.getDeviceId(), config.getProtocol(), temperature, humidity);

            } catch (Exception e) {
                log.error("模拟器上报数据失败: deviceId={}", config.getDeviceId(), e);
            }
        }

        private void sendViaMqtt(String jsonPayload) {
            String topic = "iot/device/" + config.getDeviceId() + "/telemetry";
            mqttClientService.publish(topic, jsonPayload);
        }

        private void sendViaHttp(String jsonPayload) {
            String url = config.getHttpUrl();
            if (url == null || url.isEmpty()) {
                url = "http://localhost:" + serverPort + contextPath + "/device/data/" + config.getDeviceId() + "/telemetry";
            }

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.debug("HTTP上报成功: deviceId={}, url={}", config.getDeviceId(), url);
                } else {
                    log.warn("HTTP上报返回非200状态: deviceId={}, status={}",
                            config.getDeviceId(), response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("HTTP上报失败: deviceId={}, url={}", config.getDeviceId(), url, e);
                throw e;
            }
        }
    }
}
