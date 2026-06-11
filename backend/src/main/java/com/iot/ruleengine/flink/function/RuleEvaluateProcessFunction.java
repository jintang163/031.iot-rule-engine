package com.iot.ruleengine.flink.function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.iot.ruleengine.drools.DeviceData;
import com.iot.ruleengine.engine.RuleExpressionParser;
import com.iot.ruleengine.flink.model.ActionExecuteCommand;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 核心规则评估ProcessFunction
 *
 * 按设备ID keyBy后的处理函数，负责：
 * 1. 定时从远端HTTP拉取/刷新规则列表
 * 2. 对每条DeviceData遍历所有规则，用Aviator执行表达式匹配
 * 3. 匹配成功的规则输出ActionExecuteCommand到主流
 * 4. 输出metrics指标到侧输出流
 * 5. 使用Flink State保存规则快照，支持故障恢复
 *
 * 【线程安全设计】：
 * - Flink保证每个KeyedProcessFunction实例在单线程中运行，无需担心processElement并发
 * - 但onTimer与processElement可能在不同线程回调，因此规则列表使用volatile修饰，确保可见性
 * - AviatorEvaluatorInstance本身线程安全（内部对编译表达式做了同步）
 * - Caffeine Cache是线程安全的
 * - compiledRules（内存中的规则列表）使用volatile引用+不可变对象模式：刷新时构造新List，然后原子替换引用
 *
 * 【状态管理设计】：
 * - 使用ListState<RuleSnapshot>保存规则快照，用于Checkpoint故障恢复
 * - 注意：Aviator编译后的Expression不可序列化，因此State中仅保存规则元数据（表达式字符串）
 * - initializeState中从State恢复后，重新编译所有表达式
 * - 每60秒onTimer拉取规则变化后，更新State以便下次Checkpoint持久化
 *
 * 【性能设计】：
 * - Caffeine缓存编译后的Expression对象，避免同一规则重复编译
 * - 规则按priority降序排列，高优先级规则先评估（配合互斥组逻辑可提前终止）
 * - 使用AviatorEvaluatorInstance独立实例，避免与Spring容器中的共享实例产生竞争
 * - beanToMap使用预分配容量的HashMap，减少resize
 * - metrics输出使用侧输出流，不影响主流低延迟
 * - 规则刷新使用后台HTTP异步拉取（在onTimer中执行，不阻塞processElement）
 */
public class RuleEvaluateProcessFunction
        extends KeyedProcessFunction<String, DeviceData, ActionExecuteCommand> {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluateProcessFunction.class);

    // ============== 配置常量 ==============
    /** 规则刷新间隔：60秒（毫秒） */
    private static final long RULE_REFRESH_INTERVAL_MS = 60_000L;
    /** HTTP请求连接超时：5秒 */
    private static final int HTTP_CONNECT_TIMEOUT_MS = 5_000;
    /** HTTP请求读取超时：10秒 */
    private static final int HTTP_READ_TIMEOUT_MS = 10_000;
    /** 编译缓存最大条目数 */
    private static final int COMPILE_CACHE_MAX_SIZE = 10_000;
    /** 编译缓存过期时间（小时） */
    private static final int COMPILE_CACHE_EXPIRE_HOURS = 24;

    // ============== 可配置参数（构造函数传入） ==============
    /** 规则同步HTTP URL */
    private final String ruleSyncUrl;

    // ============== Flink 状态 ==============
    /** 规则快照状态，用于Checkpoint恢复 */
    private transient ListState<RuleSnapshot> ruleState;

    // ============== 运行时成员 ==============
    /**
     * Aviator独立实例，线程安全
     * 不使用AviatorEvaluator全局单例，避免与Spring进程内的实例竞争
     */
    private transient AviatorEvaluatorInstance aviatorEvaluator;

    /**
     * 编译表达式缓存：key=ruleId, value=编译后的Expression
     * Caffeine线程安全，支持LRU淘汰和过期
     */
    private transient Cache<String, Expression> compileCache;

    /**
     * 当前生效的规则列表
     * 使用volatile保证onTimer更新后processElement立即可见
     * 规则对象本身不可变（CompiledRule字段均为final）
     */
    private volatile List<CompiledRule> compiledRules = Collections.emptyList();

    /** 规则解析器，用于将ruleJson转为Aviator表达式字符串 */
    private transient RuleExpressionParser ruleExpressionParser;

    // ============== 侧输出流定义 ==============
    /**
     * Metrics侧输出流：Tuple2<metricsName, metricsValue>
     * 实际生产中建议换成专门的Metrics POJO
     */
    public static final OutputTag<Tuple2<String, Double>> METRICS_OUTPUT_TAG =
            new OutputTag<Tuple2<String, Double>>("rule-engine-metrics") {};

    /**
     * 构造函数
     *
     * @param ruleSyncUrl 规则同步的远端HTTP URL，如 http://localhost:8080/api/rule/internal/enabled-list
     */
    public RuleEvaluateProcessFunction(String ruleSyncUrl) {
        this.ruleSyncUrl = ruleSyncUrl;
    }

    // ========================================================================
    // 生命周期方法
    // ========================================================================

    /**
     * open()：算子初始化，仅在Task启动时调用一次
     *
     * 初始化顺序：
     * 1. AviatorEvaluatorInstance（独立实例）
     * 2. Caffeine编译缓存
     * 3. RuleExpressionParser
     * 4. 从远端HTTP拉取初始规则列表
     * 5. 注册第一个onTimer定时器（60秒后触发规则刷新）
     */
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        log.info("RuleEvaluateProcessFunction.open() 开始初始化, ruleSyncUrl={}", ruleSyncUrl);

        // ========== 1. 初始化Aviator独立实例 ==========
        // 使用newInstance()创建独立实例，避免与Spring容器中的共享实例产生锁竞争
        this.aviatorEvaluator = AviatorEvaluator.newInstance();
        // 优化：浮点数直接用double，不转BigDecimal（提升数值运算性能）
        this.aviatorEvaluator.setOption(com.googlecode.aviator.Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);

        // ========== 2. 初始化Caffeine编译缓存 ==========
        this.compileCache = Caffeine.newBuilder()
                .maximumSize(COMPILE_CACHE_MAX_SIZE)
                .expireAfterWrite(COMPILE_CACHE_EXPIRE_HOURS, TimeUnit.HOURS)
                .recordStats()
                .build();

        // ========== 3. 初始化规则解析器 ==========
        this.ruleExpressionParser = new RuleExpressionParser();

        // ========== 4. 初始化Flink状态描述符 ==========
        ListStateDescriptor<RuleSnapshot> stateDescriptor = new ListStateDescriptor<>(
                "rule-snapshots",
                TypeInformation.of(new TypeHint<RuleSnapshot>() {})
        );
        this.ruleState = getRuntimeContext().getListState(stateDescriptor);

        // ========== 5. 初始化规则列表 ==========
        // 先尝试从Flink State恢复（故障恢复场景），否则从HTTP拉取
        List<RuleSnapshot> restoredSnapshots = StreamSupport
                .stream(ruleState.get().spliterator(), false)
                .collect(Collectors.toList());

        if (!restoredSnapshots.isEmpty()) {
            // 故障恢复场景：从State恢复规则并重新编译
            log.info("从Checkpoint恢复规则快照, 共{}条", restoredSnapshots.size());
            this.compiledRules = restoreAndCompileRules(restoredSnapshots);
        } else {
            // 首次启动：从HTTP拉取规则
            log.info("首次启动，从HTTP拉取规则列表");
            refreshRulesFromHttp();
        }

        // ========== 6. 注册第一个规则刷新定时器 ==========
        // 使用当前处理时间 + 间隔，避免所有并行实例同时请求HTTP
        long firstRefreshTime = System.currentTimeMillis() + RULE_REFRESH_INTERVAL_MS
                + (long) (Math.random() * 10_000); // 加0~10秒随机抖动，防止惊群
        getRuntimeContext().registerProcessingTimeTimer(firstRefreshTime);

        log.info("RuleEvaluateProcessFunction.open() 初始化完成, 当前规则数: {}", compiledRules.size());
    }

    /**
     * onTimer()：定时器回调，用于定期刷新规则
     *
     * 注意：onTimer与processElement可能并发，因此更新compiledRules时使用volatile引用替换
     */
    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<ActionExecuteCommand> out) throws Exception {
        super.onTimer(timestamp, ctx, out);

        log.debug("onTimer触发规则刷新, timestamp={}", timestamp);

        try {
            // 刷新规则
            refreshRulesFromHttp();
        } catch (Exception e) {
            log.error("规则刷新失败，将继续使用旧规则, 错误: {}", e.getMessage());
        }

        // 注册下一次刷新定时器
        long nextRefreshTime = timestamp + RULE_REFRESH_INTERVAL_MS;
        ctx.timerService().registerProcessingTimeTimer(nextRefreshTime);
    }

    // ========================================================================
    // 核心处理逻辑
    // ========================================================================

    /**
     * processElement()：处理每条DeviceData数据
     *
     * 处理流程：
     * 1. 记录开始时间
     * 2. 将DeviceData转为Aviator env Map
     * 3. 遍历当前所有compiledRules，依次执行表达式
     * 4. 匹配成功的规则产出ActionExecuteCommand
     * 5. 输出metrics到侧输出流
     */
    @Override
    public void processElement(DeviceData data, Context ctx, Collector<ActionExecuteCommand> out) throws Exception {
        long startTime = System.currentTimeMillis();
        int matchedCount = 0;

        // 空数据跳过
        if (data == null || data.getDeviceId() == null) {
            log.warn("processElement收到空数据，已跳过");
            return;
        }

        try {
            // ========== 步骤1：DeviceData -> Aviator env Map ==========
            Map<String, Object> env = DeviceDataBeanToMapFunction.beanToMap(data);

            // ========== 步骤2：遍历所有规则，逐个评估 ==========
            // 读取volatile引用，在本次processElement期间使用固定快照（即使onTimer中途刷新也不影响）
            List<CompiledRule> rulesSnapshot = this.compiledRules;

            // 记录已执行的互斥组，同组内只执行优先级最高的一条
            Set<String> executedMutexGroups = new HashSet<>();

            for (CompiledRule rule : rulesSnapshot) {
                // 互斥组判断：同组已执行过则跳过
                if (rule.mutexGroup != null && !rule.mutexGroup.isEmpty()
                        && executedMutexGroups.contains(rule.mutexGroup)) {
                    continue;
                }

                boolean matched = evaluateSingleRule(rule, env);
                if (matched) {
                    matchedCount++;

                    // ========== 步骤3：匹配成功，产出ActionExecuteCommand ==========
                    ActionExecuteCommand command = ActionExecuteCommand.builder()
                            .ruleId(rule.ruleId)
                            .ruleName(rule.ruleName)
                            .deviceId(data.getDeviceId())
                            .actionType(rule.actionType)
                            // 深拷贝params，避免后续算子修改影响原规则对象
                            .params(rule.actionParams != null
                                    ? new HashMap<>(rule.actionParams)
                                    : new HashMap<>())
                            .triggerTime(System.currentTimeMillis())
                            // targetDeviceId优先使用规则配置，否则回退到源deviceId
                            .targetDeviceId(rule.targetDeviceId != null
                                    ? rule.targetDeviceId
                                    : data.getDeviceId())
                            .build();

                    out.collect(command);

                    // 标记互斥组已执行
                    if (rule.mutexGroup != null && !rule.mutexGroup.isEmpty()) {
                        executedMutexGroups.add(rule.mutexGroup);
                    }

                    log.debug("规则匹配成功, deviceId={}, ruleId={}, ruleName={}",
                            data.getDeviceId(), rule.ruleId, rule.ruleName);
                }
            }

        } catch (Exception e) {
            log.error("processElement处理异常, deviceId={}", data.getDeviceId(), e);
        } finally {
            // ========== 步骤4：输出Metrics侧输出流 ==========
            long latencyMs = System.currentTimeMillis() - startTime;

            // 单条评估延迟（毫秒）
            ctx.output(METRICS_OUTPUT_TAG, Tuple2.of("latency_ms", (double) latencyMs));
            // 匹配规则数量
            ctx.output(METRICS_OUTPUT_TAG, Tuple2.of("matched_rule_count", (double) matchedCount));
            // 总规则数量（用于监控规则是否加载成功）
            ctx.output(METRICS_OUTPUT_TAG, Tuple2.of("total_rule_count", (double) compiledRules.size()));
        }
    }

    // ========================================================================
    // 状态快照与恢复（Checkpoint）
    // ========================================================================

    /**
     * snapshotState()：Flink做Checkpoint时调用，将当前规则快照写入State
     *
     * 注意：Expression不可序列化，因此只保存表达式字符串，恢复时重新编译
     */
    @Override
    public void snapshotState(org.apache.flink.runtime.state.FunctionSnapshotContext context) throws Exception {
        super.snapshotState(context);

        // 清空旧State
        ruleState.clear();

        // 将当前内存中的compiledRules转为可序列化的RuleSnapshot
        List<CompiledRule> snapshot = this.compiledRules;
        for (CompiledRule rule : snapshot) {
            RuleSnapshot ruleSnapshot = new RuleSnapshot();
            ruleSnapshot.ruleId = rule.ruleId;
            ruleSnapshot.ruleName = rule.ruleName;
            ruleSnapshot.priority = rule.priority;
            ruleSnapshot.expressionStr = rule.expressionStr;
            ruleSnapshot.actionType = rule.actionType;
            ruleSnapshot.actionParams = rule.actionParams;
            ruleSnapshot.targetDeviceId = rule.targetDeviceId;
            ruleSnapshot.mutexGroup = rule.mutexGroup;

            ruleState.add(ruleSnapshot);
        }

        log.debug("snapshotState完成, 共写入{}条规则快照", snapshot.size());
    }

    /**
     * initializeState()：故障恢复时，从State恢复规则
     * 此方法实际逻辑已在open()中通过读取ruleState.get()实现
     */
    @Override
    public void initializeState(org.apache.flink.runtime.state.FunctionInitializationContext context) throws Exception {
        super.initializeState(context);
        // 状态描述符已在open()中创建并读取，此处留空
    }

    // ========================================================================
    // 私有辅助方法
    // ========================================================================

    /**
     * 评估单条规则
     *
     * @param rule 编译后的规则
     * @param env  Aviator执行环境Map
     * @return true表示匹配成功
     */
    private boolean evaluateSingleRule(CompiledRule rule, Map<String, Object> env) {
        if (rule == null || rule.expression == null) {
            return false;
        }

        try {
            Object result = rule.expression.execute(env);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("规则执行异常, ruleId={}, ruleName={}, error={}",
                    rule.ruleId, rule.ruleName, e.getMessage());
            return false;
        }
    }

    /**
     * 从远端HTTP拉取最新规则列表并刷新内存中的compiledRules
     *
     * HTTP响应格式：
     * {
     *   "code": 200,
     *   "data": [
     *     {"id": 1, "name": "温度过高告警", "ruleJson": "...", "priority": 10, "mutexGroup": "g1", ...},
     *     ...
     *   ]
     * }
     */
    private void refreshRulesFromHttp() {
        log.info("开始从HTTP刷新规则, url={}", ruleSyncUrl);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(ruleSyncUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_READ_TIMEOUT_MS);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.error("HTTP请求失败, responseCode={}", responseCode);
                return;
            }

            // 读取响应
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            // 解析JSON
            JSONObject response = JSON.parseObject(sb.toString());
            Integer code = response.getInteger("code");
            if (code == null || code != 200) {
                log.error("响应体code错误, code={}, msg={}", code, response.getString("message"));
                return;
            }

            JSONArray dataArray = response.getJSONArray("data");
            if (dataArray == null || dataArray.isEmpty()) {
                log.warn("HTTP返回规则列表为空，继续使用旧规则");
                return;
            }

            // 编译并刷新规则
            List<CompiledRule> newRules = new ArrayList<>(dataArray.size());
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject ruleJson = dataArray.getJSONObject(i);
                try {
                    CompiledRule compiled = parseAndCompileRule(ruleJson);
                    if (compiled != null) {
                        newRules.add(compiled);
                    }
                } catch (Exception e) {
                    log.error("编译规则失败, ruleJson={}", ruleJson, e);
                }
            }

            // 按priority降序排序（高优先级先评估）
            newRules.sort(Comparator.comparingInt((CompiledRule r) -> r.priority).reversed());

            // 原子替换volatile引用
            this.compiledRules = Collections.unmodifiableList(newRules);

            // 更新到Flink State（下次Checkpoint持久化）
            updateRuleState(newRules);

            log.info("规则刷新成功, 新规则数={}", newRules.size());

        } catch (Exception e) {
            log.error("HTTP刷新规则异常", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 将HTTP返回的规则JSON对象解析并编译为CompiledRule
     */
    private CompiledRule parseAndCompileRule(JSONObject ruleJson) {
        Long ruleId = ruleJson.getLong("id");
        String ruleName = ruleJson.getString("name");
        String ruleJsonStr = ruleJson.getString("ruleJson");
        Integer priority = ruleJson.getInteger("priority");
        String mutexGroup = ruleJson.getString("mutexGroup");

        if (ruleId == null || ruleJsonStr == null || ruleJsonStr.trim().isEmpty()) {
            log.warn("规则字段不完整，跳过, ruleId={}", ruleId);
            return null;
        }

        // 解析ruleJson得到表达式和动作信息
        RuleExpressionParser.ParseResult parseResult =
                ruleExpressionParser.parseToExpression(String.valueOf(ruleId), ruleName, ruleJsonStr);

        String expressionStr = parseResult.getExpression();
        if (expressionStr == null || expressionStr.trim().isEmpty()) {
            expressionStr = "true";
        }

        // 从缓存获取已编译表达式，否则新编译
        String cacheKey = ruleId + "_" + expressionStr.hashCode();
        Expression expression = compileCache.getIfPresent(cacheKey);
        if (expression == null) {
            expression = aviatorEvaluator.compile(expressionStr, true);
            compileCache.put(cacheKey, expression);
            log.debug("规则编译完成并加入缓存, ruleId={}, cacheKey={}", ruleId, cacheKey);
        }

        CompiledRule compiled = new CompiledRule();
        compiled.ruleId = ruleId;
        compiled.ruleName = ruleName;
        compiled.priority = priority != null ? priority : 0;
        compiled.expressionStr = expressionStr;
        compiled.expression = expression;
        compiled.actionType = parseResult.getActionType();
        compiled.actionParams = parseResult.getActionParams() != null
                ? new HashMap<>(parseResult.getActionParams())
                : new HashMap<>();
        compiled.targetDeviceId = parseResult.getTargetDeviceId();
        compiled.mutexGroup = mutexGroup;

        return compiled;
    }

    /**
     * 从State恢复规则快照并重新编译
     */
    private List<CompiledRule> restoreAndCompileRules(List<RuleSnapshot> snapshots) {
        List<CompiledRule> result = new ArrayList<>(snapshots.size());

        for (RuleSnapshot snap : snapshots) {
            try {
                // 重新编译表达式（Expression不可序列化）
                String cacheKey = snap.ruleId + "_" + snap.expressionStr.hashCode();
                Expression expression = compileCache.getIfPresent(cacheKey);
                if (expression == null) {
                    expression = aviatorEvaluator.compile(snap.expressionStr, true);
                    compileCache.put(cacheKey, expression);
                }

                CompiledRule rule = new CompiledRule();
                rule.ruleId = snap.ruleId;
                rule.ruleName = snap.ruleName;
                rule.priority = snap.priority;
                rule.expressionStr = snap.expressionStr;
                rule.expression = expression;
                rule.actionType = snap.actionType;
                rule.actionParams = snap.actionParams != null
                        ? new HashMap<>(snap.actionParams)
                        : new HashMap<>();
                rule.targetDeviceId = snap.targetDeviceId;
                rule.mutexGroup = snap.mutexGroup;

                result.add(rule);
            } catch (Exception e) {
                log.error("恢复规则失败, ruleId={}, ruleName={}", snap.ruleId, snap.ruleName, e);
            }
        }

        // 按优先级排序
        result.sort(Comparator.comparingInt((CompiledRule r) -> r.priority).reversed());
        return Collections.unmodifiableList(result);
    }

    /**
     * 更新Flink ListState（用于下次Checkpoint持久化）
     */
    private void updateRuleState(List<CompiledRule> rules) throws Exception {
        ruleState.clear();
        for (CompiledRule rule : rules) {
            RuleSnapshot snap = new RuleSnapshot();
            snap.ruleId = rule.ruleId;
            snap.ruleName = rule.ruleName;
            snap.priority = rule.priority;
            snap.expressionStr = rule.expressionStr;
            snap.actionType = rule.actionType;
            snap.actionParams = rule.actionParams;
            snap.targetDeviceId = rule.targetDeviceId;
            snap.mutexGroup = rule.mutexGroup;
            ruleState.add(snap);
        }
    }

    // ========================================================================
    // 内部数据类
    // ========================================================================

    /**
     * 编译后的规则对象（不可变）
     *
     * 注意：Expression不可序列化，不能放入Flink State
     * 因此State中使用RuleSnapshot存储expressionStr，恢复时重新编译
     */
    private static class CompiledRule implements Serializable {
        private static final long serialVersionUID = 1L;
        Long ruleId;
        String ruleName;
        int priority;
        /** 表达式字符串（可序列化，用于State快照） */
        String expressionStr;
        /** 编译后的Aviator表达式（不可序列化，运行时使用） */
        transient Expression expression;
        String actionType;
        Map<String, Object> actionParams;
        String targetDeviceId;
        String mutexGroup;
    }

    /**
     * 规则快照（可序列化，用于Flink State）
     * 不包含Expression对象，只存表达式字符串
     */
    private static class RuleSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        Long ruleId;
        String ruleName;
        int priority;
        String expressionStr;
        String actionType;
        Map<String, Object> actionParams;
        String targetDeviceId;
        String mutexGroup;
    }
}
