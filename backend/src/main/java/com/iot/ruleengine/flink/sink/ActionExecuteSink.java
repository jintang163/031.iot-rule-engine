package com.iot.ruleengine.flink.sink;

import com.alibaba.fastjson.JSON;
import com.iot.ruleengine.flink.model.ActionExecuteCommand;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * 动作执行Sink：将ActionExecuteCommand下发到MQTT，并异步记录action_log
 *
 * 下发方式优先级（可配置）：
 * 1. MQTT发布：iot/device/{targetDeviceId}/command（默认）
 * 2. HTTP回调备用
 *
 * 【线程安全设计】：
 * - Flink保证每个RichSinkFunction实例在单线程中调用invoke()
 * - 但异步日志线程池中的线程会并发写日志队列，因此使用ConcurrentLinkedQueue（无锁非阻塞队列）
 * - MqttClient内部是线程安全的（Paho实现），publish方法可安全从单线程invoke调用
 * - HTTP异步发送使用独立线程池，避免阻塞invoke主流程
 * - 所有配置字段在open()中初始化后不再修改，final/volatile保证可见性
 *
 * 【状态与可靠性】：
 * - MQTT QoS=1（至少一次送达），配合失败重试3次+指数退避
 * - 注意：本Sink目前未实现Exactly-Once语义（需配合两阶段提交），生产环境建议至少开启MQTT持久化
 * - action_log通过HTTP异步批量写入，避免在Sink中直接连DB造成连接池压力
 *
 * 【性能设计】：
 * - 日志发送采用批量聚合+异步线程池模式，批量大小100或flush间隔500ms
 * - 使用MemoryPersistence（内存持久化）提升MQTT吞吐，接受重启后少量消息丢失的tradeoff
 * - MQTT clientId加上随机后缀，避免同Group多Sink实例ID冲突
 * - 指数退避重试：100ms -> 200ms -> 400ms，减少下游压力
 * - Sink并行度可独立设置（不同于上游），避免成为瓶颈
 */
public class ActionExecuteSink extends RichSinkFunction<ActionExecuteCommand> {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ActionExecuteSink.class);

    // ============== 重试配置 ==============
    /** 最大重试次数（含首次发送，共尝试3次） */
    private static final int MAX_RETRY_TIMES = 3;
    /** 初始重试间隔（毫秒），后续指数翻倍 */
    private static final long INITIAL_RETRY_BACKOFF_MS = 100L;

    // ============== MQTT QoS配置 ==============
    /** MQTT QoS 1 = 至少一次送达 */
    private static final int MQTT_QOS = 1;

    // ============== 异步日志批量配置 ==============
    /** action_log批量保存的最大条数 */
    private static final int LOG_BATCH_SIZE = 100;
    /** action_log flush间隔（毫秒） */
    private static final long LOG_FLUSH_INTERVAL_MS = 500L;
    /** action-log HTTP 批量保存URL */
    private static final String ACTION_LOG_BATCH_SAVE_URL = "/api/action-log/internal/batch-save";

    // ============== 可配置参数（构造函数传入） ==============
    /** MQTT broker地址，如 tcp://localhost:1883 */
    private final String mqttHostUrl;
    /** MQTT用户名 */
    private final String mqttUsername;
    /** MQTT密码 */
    private final String mqttPassword;
    /** rule-engine服务地址前缀，如 http://localhost:8080 */
    private final String ruleServiceBaseUrl;

    // ============== 运行时成员 ==============
    /** MQTT客户端（Paho实现，线程安全） */
    private transient MqttClient mqttClient;

    /** 异步保存action_log的线程池（单线程即可，批量发送） */
    private transient ScheduledExecutorService logScheduler;

    /** 待批量保存的action_log队列（线程安全无锁队列） */
    private transient ConcurrentLinkedQueue<Map<String, Object>> pendingLogQueue;

    /**
     * 构造函数
     *
     * @param mqttHostUrl        MQTT broker地址
     * @param mqttUsername       MQTT用户名
     * @param mqttPassword       MQTT密码
     * @param ruleServiceBaseUrl rule-engine服务地址前缀（用于写action_log）
     */
    public ActionExecuteSink(String mqttHostUrl, String mqttUsername, String mqttPassword,
                             String ruleServiceBaseUrl) {
        this.mqttHostUrl = mqttHostUrl;
        this.mqttUsername = mqttUsername;
        this.mqttPassword = mqttPassword;
        this.ruleServiceBaseUrl = ruleServiceBaseUrl;
    }

    // ========================================================================
    // 生命周期方法
    // ========================================================================

    /**
     * open()：初始化MQTT客户端和异步日志线程池
     *
     * 初始化顺序：
     * 1. 创建MQTT client并连接broker
     * 2. 初始化日志队列
     * 3. 启动定时flush线程
     */
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        log.info("ActionExecuteSink.open() 开始初始化, mqttHostUrl={}", mqttHostUrl);

        // ========== 1. 初始化MQTT客户端 ==========
        String clientId = "flink-action-sink-" + getRuntimeContext().getIndexOfThisSubtask()
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        this.mqttClient = new MqttClient(mqttHostUrl, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(mqttUsername);
        options.setPassword(mqttPassword.toCharArray());
        // 自动重连（Paho内部实现指数退避）
        options.setAutomaticReconnect(true);
        // 清理会话（无状态模式，重启不订阅旧消息）
        options.setCleanSession(true);
        // 连接超时30秒
        options.setConnectionTimeout(30);
        // KeepAlive 60秒
        options.setKeepAliveInterval(60);

        // 同步连接，失败则抛出异常（让Flink重启策略介入）
        mqttClient.connect(options);
        log.info("MQTT客户端连接成功, clientId={}", clientId);

        // ========== 2. 初始化异步日志组件 ==========
        this.pendingLogQueue = new ConcurrentLinkedQueue<>();

        // 单线程调度器：定期flush日志队列
        this.logScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "action-log-flush-thread");
            t.setDaemon(true);
            return t;
        });

        // 固定间隔flush：500ms一次
        this.logScheduler.scheduleAtFixedRate(
                this::flushLogBatch,
                LOG_FLUSH_INTERVAL_MS,
                LOG_FLUSH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        log.info("ActionExecuteSink.open() 初始化完成");
    }

    /**
     * close()：清理资源
     * 按创建逆序关闭：先停日志线程，再断MQTT连接
     */
    @Override
    public void close() throws Exception {
        super.close();

        log.info("ActionExecuteSink.close() 开始清理资源");

        // ========== 1. 关闭日志线程池 ==========
        if (logScheduler != null) {
            logScheduler.shutdown();
            try {
                // 等待最多5秒让剩余日志flush完毕
                if (!logScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // ========== 2. 最后flush一次剩余日志 ==========
        flushLogBatch();

        // ========== 3. 关闭MQTT客户端 ==========
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect(5000); // 5秒超时优雅断开
                }
                mqttClient.close();
            } catch (MqttException e) {
                log.warn("MQTT客户端关闭异常", e);
            }
        }

        log.info("ActionExecuteSink.close() 资源清理完成");
    }

    // ========================================================================
    // 核心处理逻辑
    // ========================================================================

    /**
     * invoke()：处理每条ActionExecuteCommand
     *
     * 处理流程：
     * 1. 构造MQTT消息payload（JSON）
     * 2. 发布到MQTT topic: iot/device/{targetDeviceId}/command
     * 3. 失败重试3次，指数退避
     * 4. 将action_log记录加入异步队列
     */
    @Override
    public void invoke(ActionExecuteCommand command, Context context) throws Exception {
        if (command == null || command.getTargetDeviceId() == null) {
            log.warn("invoke收到空命令，已跳过");
            return;
        }

        String targetDeviceId = command.getTargetDeviceId();
        String topic = "iot/device/" + targetDeviceId + "/command";

        // ========== 步骤1：构造payload JSON ==========
        Map<String, Object> payload = buildCommandPayload(command);
        byte[] payloadBytes = JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8);

        // ========== 步骤2：MQTT发布（带重试） ==========
        boolean success = publishWithRetry(topic, payloadBytes);

        if (!success) {
            log.error("MQTT命令下发最终失败（重试{}次后）, targetDeviceId={}, ruleId={}",
                    MAX_RETRY_TIMES, targetDeviceId, command.getRuleId());
        } else {
            log.debug("MQTT命令下发成功, topic={}, ruleId={}, actionType={}",
                    topic, command.getRuleId(), command.getActionType());
        }

        // ========== 步骤3：异步记录action_log（不影响主流程） ==========
        // 无论MQTT成功与否，都记录日志（含执行结果）
        enqueueActionLog(command, success);
    }

    // ========================================================================
    // 私有辅助方法
    // ========================================================================

    /**
     * 构建MQTT命令payload
     */
    private Map<String, Object> buildCommandPayload(ActionExecuteCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        // 规则信息
        payload.put("ruleId", command.getRuleId());
        payload.put("ruleName", command.getRuleName());
        // 动作信息
        payload.put("action", command.getActionType());
        payload.put("params", command.getParams() != null ? command.getParams() : new HashMap<>());
        // 触发信息
        payload.put("sourceDeviceId", command.getDeviceId());
        payload.put("targetDeviceId", command.getTargetDeviceId());
        payload.put("triggerTime", command.getTriggerTime());
        payload.put("messageId", UUID.randomUUID().toString().replace("-", ""));
        return payload;
    }

    /**
     * MQTT发布，带重试和指数退避
     *
     * @return true表示最终成功，false表示重试耗尽仍失败
     */
    private boolean publishWithRetry(String topic, byte[] payload) {
        long backoff = INITIAL_RETRY_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRY_TIMES; attempt++) {
            try {
                // 确保MQTT连接
                if (!mqttClient.isConnected()) {
                    log.warn("MQTT连接已断开，尝试重连...");
                    mqttClient.reconnect();
                }

                MqttMessage message = new MqttMessage(payload);
                message.setQos(MQTT_QOS);
                // retained=false：新订阅者不接收旧命令
                message.setRetained(false);

                mqttClient.publish(topic, message);
                return true;

            } catch (MqttException e) {
                log.warn("MQTT发布失败, attempt={}/{}, topic={}, error={}",
                        attempt, MAX_RETRY_TIMES, topic, e.getMessage());

                if (attempt < MAX_RETRY_TIMES) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    // 指数退避翻倍
                    backoff = Math.min(backoff * 2, 5000L); // 最大退避5秒
                }
            } catch (Exception e) {
                log.error("MQTT发布发生未知错误", e);
            }
        }

        return false;
    }

    /**
     * 将action_log加入异步队列
     * 队列满或flush触发时批量发送
     */
    private void enqueueActionLog(ActionExecuteCommand command, boolean executeSuccess) {
        Map<String, Object> logRecord = new LinkedHashMap<>();
        logRecord.put("ruleId", command.getRuleId());
        logRecord.put("ruleName", command.getRuleName());
        logRecord.put("deviceId", command.getDeviceId());
        logRecord.put("targetDeviceId", command.getTargetDeviceId());
        logRecord.put("actionType", command.getActionType());
        logRecord.put("params", command.getParams() != null
                ? JSON.toJSONString(command.getParams()) : "{}");
        logRecord.put("triggerTime", new java.sql.Timestamp(command.getTriggerTime()));
        logRecord.put("executeTime", new java.sql.Timestamp(System.currentTimeMillis()));
        logRecord.put("executeStatus", executeSuccess ? 1 : 0);
        logRecord.put("executeResult", executeSuccess ? "SUCCESS" : "FAILED");
        logRecord.put("createTime", new java.sql.Timestamp(System.currentTimeMillis()));

        pendingLogQueue.offer(logRecord);

        // 如果队列达到批量阈值，立即触发flush
        // 注意：size()对ConcurrentLinkedQueue是O(n)操作，这里用100阈值，可接受
        if (pendingLogQueue.size() >= LOG_BATCH_SIZE) {
            // 异步提交flush任务（不在invoke线程中执行HTTP）
            logScheduler.submit(this::flushLogBatch);
        }
    }

    /**
     * flush日志批：将队列中所有记录通过HTTP POST批量保存
     * 由定时调度线程和enqueueActionLog触发调用
     */
    private void flushLogBatch() {
        if (pendingLogQueue.isEmpty()) {
            return;
        }

        // 批量取出最多LOG_BATCH_SIZE条
        List<Map<String, Object>> batch = new ArrayList<>(LOG_BATCH_SIZE);
        for (int i = 0; i < LOG_BATCH_SIZE; i++) {
            Map<String, Object> record = pendingLogQueue.poll();
            if (record == null) {
                break;
            }
            batch.add(record);
        }

        if (batch.isEmpty()) {
            return;
        }

        HttpURLConnection conn = null;
        try {
            String fullUrl = ruleServiceBaseUrl + ACTION_LOG_BATCH_SAVE_URL;
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            // 写请求体
            try (OutputStream os = conn.getOutputStream()) {
                byte[] body = JSON.toJSONString(batch).getBytes(StandardCharsets.UTF_8);
                os.write(body);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.warn("action_log批量保存HTTP非200, code={}, batchSize={}", responseCode, batch.size());
                // 失败不重入队列（避免无限重试），生产环境可考虑加入死信队列
            } else {
                log.debug("action_log批量保存成功, batchSize={}", batch.size());
            }

        } catch (Exception e) {
            log.error("action_log批量保存异常, batchSize={}, error={}", batch.size(), e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
