package com.iot.ruleengine.flink;

import com.iot.ruleengine.drools.DeviceData;
import com.iot.ruleengine.flink.function.RuleEvaluateProcessFunction;
import com.iot.ruleengine.flink.model.ActionExecuteCommand;
import com.iot.ruleengine.flink.schema.DeviceDataDeserializationSchema;
import com.iot.ruleengine.flink.sink.ActionExecuteSink;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * IoT规则引擎Flink独立作业入口类
 *
 * 可通过以下两种方式运行：
 * 1. 提交到Flink集群：
 *    flink run -c com.iot.ruleengine.flink.IotRuleFlinkJob rule-engine-1.0.0.jar
 *        --kafka-bootstrap kafka1:9092,kafka2:9092
 *        --mqtt-host tcp://mqtt-broker:1883
 *        --mqtt-username admin
 *        --mqtt-password public
 *        --rule-service-url http://rule-engine:8080
 *
 * 2. 本地IDE运行（调试模式）：
 *    直接运行main方法，使用默认参数
 *
 * 【作业拓扑】：
 *   KafkaSource (iot-device-telemetry)
 *       │
 *       ▼
 *   WatermarkStrategy (乱序容忍2秒，处理时间戳)
 *       │
 *       ▼
 *   keyBy(deviceId) ── 按设备分区，保证同一设备数据顺序处理
 *       │
 *       ▼
 *   RuleEvaluateProcessFunction ── 核心规则评估（Aviator+规则刷新+状态管理）
 *       │                └── 侧输出流：metrics (latency_ms, matched_rule_count等)
 *       ▼
 *   ActionExecuteSink ── MQTT命令下发 + 异步action_log记录
 *
 * 【状态管理与一致性】：
 * - Checkpoint间隔：60秒，EXACTLY_ONCE语义
 * - 重启策略：固定延迟重启，最多3次，间隔10秒
 * - 并行度：4（可在提交时通过-p参数覆盖）
 * - Kafka Source消费位点：latest（从最新消息开始，避免回放历史数据）
 *
 * 【性能调优点】：
 * - parallelism=4：与Kafka topic分区数对齐（建议iot-device-telemetry设为4分区）
 * - Watermark乱序容忍2秒：根据IoT数据实际延迟调整
 * - keyBy(deviceId)：保证同一设备数据进入同一TaskManager，利用本地性
 */
public class IotRuleFlinkJob {

    private static final Logger log = LoggerFactory.getLogger(IotRuleFlinkJob.class);

    // ============== 默认参数值 ==============
    /** 默认Kafka Bootstrap服务器 */
    private static final String DEFAULT_KAFKA_BOOTSTRAP = "localhost:9092";
    /** 默认MQTT Host URL */
    private static final String DEFAULT_MQTT_HOST = "tcp://localhost:1883";
    /** 默认MQTT用户名 */
    private static final String DEFAULT_MQTT_USERNAME = "admin";
    /** 默认MQTT密码 */
    private static final String DEFAULT_MQTT_PASSWORD = "public";
    /** 默认规则服务地址前缀 */
    private static final String DEFAULT_RULE_SERVICE_URL = "http://localhost:8080";

    /** Kafka源Topic：设备遥测数据 */
    private static final String SOURCE_TOPIC = "iot-device-telemetry";
    /** Kafka消费组ID */
    private static final String CONSUMER_GROUP_ID = "iot-rule-engine-group";

    // ============== Checkpoint配置 ==============
    /** Checkpoint间隔：60秒（毫秒） */
    private static final long CHECKPOINT_INTERVAL_MS = 60_000L;
    /** Checkpoint最小间隔：防止Checkpoint过于频繁 */
    private static final long CHECKPOINT_MIN_PAUSE_MS = 30_000L;
    /** Checkpoint超时：10分钟 */
    private static final long CHECKPOINT_TIMEOUT_MS = 600_000L;
    /** 最大同时进行的Checkpoint数：1 */
    private static final int MAX_CONCURRENT_CHECKPOINTS = 1;

    // ============== 重启策略配置 ==============
    /** 最大重启次数 */
    private static final int RESTART_MAX_ATTEMPTS = 3;
    /** 重启间隔：10秒 */
    private static final long RESTART_DELAY_SECONDS = 10L;

    // ============== 并行度配置 ==============
    /** 默认并行度（建议与Kafka分区数对齐） */
    private static final int DEFAULT_PARALLELISM = 4;

    // ============== Watermark配置 ==============
    /** 乱序数据容忍时间：2秒 */
    private static final long WATERMARK_OUT_OF_ORDERNESS_SECONDS = 2L;

    /**
     * 主入口方法
     *
     * 支持的命令行参数：
     *   args[0] = kafka-bootstrap（默认localhost:9092）
     *   args[1] = mqtt-host（默认tcp://localhost:1883）
     *   args[2] = mqtt-username（默认admin）
     *   args[3] = mqtt-password（默认public）
     *   args[4] = rule-service-url（默认http://localhost:8080）
     *
     * 或者使用 --key=value 格式：
     *   --kafka-bootstrap=localhost:9092
     *   --mqtt-host=tcp://localhost:1883
     *   --mqtt-username=admin
     *   --mqtt-password=public
     *   --rule-service-url=http://localhost:8080
     */
    public static void main(String[] args) throws Exception {
        log.info("===============================================");
        log.info("  IoT Rule Engine Flink Job 启动中...");
        log.info("===============================================");

        // ========== 步骤1：解析命令行参数 ==========
        JobParams params = parseArgs(args);
        log.info("参数配置:");
        log.info("  kafka-bootstrap    : {}", params.kafkaBootstrap);
        log.info("  mqtt-host          : {}", params.mqttHost);
        log.info("  mqtt-username      : {}", params.mqttUsername);
        log.info("  rule-service-url   : {}", params.ruleServiceUrl);
        log.info("  source-topic       : {}", SOURCE_TOPIC);
        log.info("  consumer-group     : {}", CONSUMER_GROUP_ID);

        // ========== 步骤2：创建并配置StreamExecutionEnvironment ==========
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // --- Checkpoint配置 ---
        // 开启EXACTLY_ONCE语义的Checkpoint，间隔60秒
        env.enableCheckpointing(CHECKPOINT_INTERVAL_MS, CheckpointingMode.EXACTLY_ONCE);
        // Checkpoint最小间隔（两个Checkpoint之间至少30秒）
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(CHECKPOINT_MIN_PAUSE_MS);
        // Checkpoint超时时间（10分钟未完成则丢弃）
        env.getCheckpointConfig().setCheckpointTimeout(CHECKPOINT_TIMEOUT_MS);
        // 最大同时进行的Checkpoint数（避免占用过多网络/IO）
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(MAX_CONCURRENT_CHECKPOINTS);
        // 任务取消时保留Checkpoint（可用于手动恢复）
        env.getCheckpointConfig().enableExternalizedCheckpoints(
                org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
        );
        // 允许Checkpoint连续失败次数（避免偶发网络错误导致作业失败）
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);

        // --- 重启策略配置 ---
        // 固定延迟重启：失败后最多重启3次，每次间隔10秒
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
                RESTART_MAX_ATTEMPTS,
                Time.of(RESTART_DELAY_SECONDS, TimeUnit.SECONDS)
        ));

        // --- 并行度配置 ---
        env.setParallelism(DEFAULT_PARALLELISM);

        // --- 其他优化 ---
        // 关闭Operator链（调试时可打开，生产环境建议保持默认）
        // env.disableOperatorChaining();

        log.info("Flink环境配置完成:");
        log.info("  checkpoint-interval: {}s", CHECKPOINT_INTERVAL_MS / 1000);
        log.info("  checkpoint-mode    : EXACTLY_ONCE");
        log.info("  restart-strategy   : fixedDelay(attempts={}, delay={}s)",
                RESTART_MAX_ATTEMPTS, RESTART_DELAY_SECONDS);
        log.info("  parallelism        : {}", DEFAULT_PARALLELISM);

        // ========== 步骤3：构建Kafka Source ==========
        KafkaSource<DeviceData> kafkaSource = KafkaSource.<DeviceData>builder()
                .setBootstrapServers(params.kafkaBootstrap)
                .setTopics(SOURCE_TOPIC)
                .setGroupId(CONSUMER_GROUP_ID)
                // 使用自定义反序列化Schema：Kafka JSON -> DeviceData对象
                .setValueOnlyDeserializer(new DeviceDataDeserializationSchema())
                // 从最新位点开始消费（避免回放历史数据）
                // 生产环境如需断点续传，可改为OffsetsInitializer.committedOffsets()
                .setStartingOffsets(OffsetsInitializer.latest())
                // 开启Kafka消费者自动提交位点（与Checkpoint配合实现Exactly-Once）
                .setProperty("enable.auto.commit", "false")
                // 消费者会话超时
                .setProperty("session.timeout.ms", "45000")
                // 请求超时
                .setProperty("request.timeout.ms", "60000")
                // 单次拉取最大记录数（吞吐优化）
                .setProperty("max.poll.records", "500")
                .build();

        log.info("Kafka Source构建完成, bootstrap={}, topic={}", params.kafkaBootstrap, SOURCE_TOPIC);

        // ========== 步骤4：添加Source到执行环境 ==========
        DataStreamSource<DeviceData> sourceStream = env.fromSource(
                kafkaSource,
                // Watermark策略：乱序容忍2秒，使用当前系统时间作为事件时间
                // 说明：IoT场景下设备时钟可能不同步，因此使用处理时间（System.currentTimeMillis()）
                // 如果设备上报数据中包含准确时间戳，可改为从DeviceData.getTime()提取
                WatermarkStrategy.<DeviceData>forBoundedOutOfOrderness(
                        Duration.ofSeconds(WATERMARK_OUT_OF_ORDERNESS_SECONDS)
                ).withTimestampAssigner((deviceData, recordTimestamp) ->
                        // 使用当前系统时间作为事件时间
                        // 如数据中自带时间戳，可替换为：Long.parseLong(deviceData.getTime())
                        System.currentTimeMillis()
                ),
                "Kafka-Source-" + SOURCE_TOPIC
        );

        // ========== 步骤5：构建规则同步URL ==========
        // 规则列表拉取接口：GET http://{rule-service-host}/api/rule/internal/enabled-list
        String ruleSyncUrl = params.ruleServiceUrl + "/api/rule/internal/enabled-list";

        // ========== 步骤6：核心处理链 ==========
        // 6.1 keyBy：按设备ID分区，保证同一设备的数据进入同一Task，顺序处理
        //     同时保证状态（如互斥组）的正确维护
        KeyedProcessFunction<String, DeviceData, ActionExecuteCommand> ruleProcessFunction =
                new RuleEvaluateProcessFunction(ruleSyncUrl);

        // 6.2 process：核心规则评估
        SingleOutputStreamOperator<ActionExecuteCommand> commandStream = sourceStream
                .keyBy(DeviceData::getDeviceId)
                .process(ruleProcessFunction, "RuleEvaluateProcess");

        // ========== 步骤6扩展（可选）：消费Metrics侧输出流 ==========
        // 生产环境中可以将metrics流写入Prometheus PushGateway / InfluxDB / Kafka等
        DataStream<org.apache.flink.api.java.tuple.Tuple2<String, Double>> metricsStream =
                commandStream.getSideOutput(RuleEvaluateProcessFunction.METRICS_OUTPUT_TAG);

        // 示例：打印metrics到日志（生产环境请替换为实际sink）
        metricsStream
                .name("Metrics-Printer")
                .uid("metrics-printer-uid")
                .print("METRICS");

        // ========== 步骤7：添加ActionExecuteSink ==========
        // MQTT sink：下发控制命令到设备
        ActionExecuteSink actionSink = new ActionExecuteSink(
                params.mqttHost,
                params.mqttUsername,
                params.mqttPassword,
                params.ruleServiceUrl
        );

        commandStream
                .addSink(actionSink)
                .name("ActionExecuteSink-MQTT")
                .uid("action-execute-sink-uid")
                // sink的并行度可独立调整（如MQTT连接数不够可提高）
                .setParallelism(DEFAULT_PARALLELISM);

        // ========== 步骤8：启动作业 ==========
        String jobName = "IotRuleEngineFlinkJob";
        log.info("===============================================");
        log.info("  提交Flink作业: {}", jobName);
        log.info("===============================================");

        // execute()是阻塞方法，直到作业停止（cancel或失败）
        env.execute(jobName);
    }

    // ========================================================================
    // 私有辅助：参数解析
    // ========================================================================

    /**
     * 解析命令行参数
     * 支持两种格式：
     *   1. 位置参数：args[0]=kafka, args[1]=mqtt-host, args[2]=mqtt-user, args[3]=mqtt-pass, args[4]=rule-service
     *   2. 命名参数：--kafka-bootstrap=xxx --mqtt-host=xxx --mqtt-username=xxx --mqtt-password=xxx --rule-service-url=xxx
     */
    private static JobParams parseArgs(String[] args) {
        JobParams params = new JobParams();
        params.kafkaBootstrap = DEFAULT_KAFKA_BOOTSTRAP;
        params.mqttHost = DEFAULT_MQTT_HOST;
        params.mqttUsername = DEFAULT_MQTT_USERNAME;
        params.mqttPassword = DEFAULT_MQTT_PASSWORD;
        params.ruleServiceUrl = DEFAULT_RULE_SERVICE_URL;

        if (args == null || args.length == 0) {
            log.info("未指定命令行参数，使用默认配置");
            return params;
        }

        // 判断是否为--key=value格式
        if (args[0].startsWith("--")) {
            for (String arg : args) {
                if (arg.startsWith("--kafka-bootstrap=")) {
                    params.kafkaBootstrap = arg.substring("--kafka-bootstrap=".length());
                } else if (arg.startsWith("--mqtt-host=")) {
                    params.mqttHost = arg.substring("--mqtt-host=".length());
                } else if (arg.startsWith("--mqtt-username=")) {
                    params.mqttUsername = arg.substring("--mqtt-username=".length());
                } else if (arg.startsWith("--mqtt-password=")) {
                    params.mqttPassword = arg.substring("--mqtt-password=".length());
                } else if (arg.startsWith("--rule-service-url=")) {
                    params.ruleServiceUrl = arg.substring("--rule-service-url=".length());
                } else {
                    log.warn("未知参数: {}", arg);
                }
            }
        } else {
            // 位置参数格式
            if (args.length >= 1 && !args[0].trim().isEmpty()) {
                params.kafkaBootstrap = args[0].trim();
            }
            if (args.length >= 2 && !args[1].trim().isEmpty()) {
                params.mqttHost = args[1].trim();
            }
            if (args.length >= 3 && !args[2].trim().isEmpty()) {
                params.mqttUsername = args[2].trim();
            }
            if (args.length >= 4 && !args[3].trim().isEmpty()) {
                params.mqttPassword = args[3].trim();
            }
            if (args.length >= 5 && !args[4].trim().isEmpty()) {
                params.ruleServiceUrl = args[4].trim();
            }
        }

        return params;
    }

    /**
     * 作业参数封装类（纯数据对象）
     */
    private static class JobParams {
        String kafkaBootstrap;
        String mqttHost;
        String mqttUsername;
        String mqttPassword;
        String ruleServiceUrl;
    }
}
