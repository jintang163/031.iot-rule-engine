package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.iot.ruleengine.engine.RuleEngine;
import com.iot.ruleengine.engine.cache.RuleCompilerCache;
import com.iot.ruleengine.engine.cache.RuleJsonParseCache;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.repository.DeviceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/metrics")
@CrossOrigin
public class MetricsController {

    private final MeterRegistry registry;
    private final RuleCompilerCache ruleCompilerCache;
    private final RuleJsonParseCache ruleJsonParseCache;
    private final RuleEngine ruleEngine;
    private final DeviceRepository deviceRepository;

    @Value("${rule.flink.enabled:false}")
    private boolean flinkEnabled;

    @Value("${rule.engine:aviator}")
    private String engineType;

    private final ConcurrentLinkedDeque<Long> evaluationTimestamps = new ConcurrentLinkedDeque<>();
    private final AtomicLong lastEvaluationCount = new AtomicLong(0);
    private final Object throughputLock = new Object();

    @Autowired
    public MetricsController(MeterRegistry registry,
                             RuleCompilerCache ruleCompilerCache,
                             RuleJsonParseCache ruleJsonParseCache,
                             RuleEngine ruleEngine,
                             DeviceRepository deviceRepository) {
        this.registry = registry;
        this.ruleCompilerCache = ruleCompilerCache;
        this.ruleJsonParseCache = ruleJsonParseCache;
        this.ruleEngine = ruleEngine;
        this.deviceRepository = deviceRepository;
    }

    @GetMapping("/rule-engine")
    public Map<String, Object> getRuleEngineMetrics() {
        Map<String, Object> result = new HashMap<>();

        result.put("engineType", engineType);
        result.put("loadedRuleCount", ruleEngine.getLoadedRuleCount());
        result.put("loadedRuleIds", new ArrayList<>(ruleEngine.getLoadedRuleIds()));
        result.put("flinkEnabled", flinkEnabled);

        Map<String, Object> cacheStats = new HashMap<>();

        CacheStats compileStats = ruleCompilerCache.stats();
        Map<String, Object> compileCacheStats = new HashMap<>();
        compileCacheStats.put("hitCount", compileStats.hitCount());
        compileCacheStats.put("missCount", compileStats.missCount());
        compileCacheStats.put("hitRate", compileStats.hitRate());
        compileCacheStats.put("loadCount", compileStats.loadCount());
        compileCacheStats.put("evictionCount", compileStats.evictionCount());
        cacheStats.put("compileCache", compileCacheStats);

        CacheStats parseStats = ruleJsonParseCache.stats();
        Map<String, Object> parseCacheStats = new HashMap<>();
        parseCacheStats.put("hitCount", parseStats.hitCount());
        parseCacheStats.put("missCount", parseStats.missCount());
        parseCacheStats.put("hitRate", parseStats.hitRate());
        parseCacheStats.put("loadCount", parseStats.loadCount());
        parseCacheStats.put("evictionCount", parseStats.evictionCount());
        cacheStats.put("parseCache", parseCacheStats);

        result.put("cacheStats", cacheStats);

        return result;
    }

    @GetMapping("/latency")
    public Map<String, Object> getLatencyMetrics() {
        Map<String, Object> result = new HashMap<>();

        Timer evaluationTimer = registry.find("rules_evaluation_seconds").timer();

        if (evaluationTimer != null) {
            Timer.Snapshot snapshot = evaluationTimer.takeSnapshot();

            result.put("evaluationAvgMs", evaluationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
            result.put("evaluationP50Ms", snapshot.percentileValues()[0].value(java.util.concurrent.TimeUnit.MILLISECONDS));
            result.put("evaluationP95Ms", snapshot.percentileValues()[1].value(java.util.concurrent.TimeUnit.MILLISECONDS));
            result.put("evaluationP99Ms", snapshot.percentileValues()[2].value(java.util.concurrent.TimeUnit.MILLISECONDS));
            result.put("evaluationCount", evaluationTimer.count());
        } else {
            result.put("evaluationAvgMs", 0.0);
            result.put("evaluationP50Ms", 0.0);
            result.put("evaluationP95Ms", 0.0);
            result.put("evaluationP99Ms", 0.0);
            result.put("evaluationCount", 0L);
        }

        Counter rulesRegisteredCounter = registry.find("rules_registered_total").counter();
        result.put("totalRegisteredRules", rulesRegisteredCounter != null ? rulesRegisteredCounter.count() : 0.0);

        Counter actionsTriggeredCounter = registry.find("actions_triggered_total").counter();
        result.put("totalActionsTriggered", actionsTriggeredCounter != null ? actionsTriggeredCounter.count() : 0.0);

        return result;
    }

    @GetMapping("/throughput")
    public Map<String, Object> getThroughputMetrics() {
        Map<String, Object> result = new HashMap<>();

        Timer evaluationTimer = registry.find("rules_evaluation_seconds").timer();
        long evaluationCount = evaluationTimer != null ? evaluationTimer.count() : 0L;

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        double uptimeSeconds = uptimeMs / 1000.0;

        double avgQps = uptimeSeconds > 0 ? evaluationCount / uptimeSeconds : 0.0;

        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - 5000;

        synchronized (throughputLock) {
            long currentCount = evaluationCount;
            long lastCount = lastEvaluationCount.getAndSet(currentCount);
            long diff = currentCount - lastCount;
            if (diff > 0) {
                for (int i = 0; i < diff; i++) {
                    evaluationTimestamps.addLast(currentTime);
                }
            }

            while (!evaluationTimestamps.isEmpty() && evaluationTimestamps.peekFirst() < windowStart) {
                evaluationTimestamps.pollFirst();
            }
        }

        int recentEvaluations5s = evaluationTimestamps.size();
        double recentQps5s = recentEvaluations5s / 5.0;

        result.put("totalEvaluations", evaluationCount);
        result.put("uptimeMs", uptimeMs);
        result.put("avgQps", avgQps);
        result.put("recentEvaluations5s", recentEvaluations5s);
        result.put("recentQps5s", recentQps5s);

        return result;
    }

    @GetMapping("/device-status")
    public Map<String, Object> getDeviceStatusMetrics() {
        Map<String, Object> result = new HashMap<>();

        try {
            Long totalDevices = deviceRepository.selectCount(null);
            result.put("totalDevices", totalDevices != null ? totalDevices : 0L);

            QueryWrapper<Device> onlineWrapper = new QueryWrapper<>();
            onlineWrapper.eq("online", 1);
            Long onlineDevices = deviceRepository.selectCount(onlineWrapper);
            result.put("onlineDevices", onlineDevices != null ? onlineDevices : 0L);

            QueryWrapper<Device> offlineWrapper = new QueryWrapper<>();
            offlineWrapper.eq("online", 0).or().isNull("online");
            Long offlineDevices = deviceRepository.selectCount(offlineWrapper);
            result.put("offlineDevices", offlineDevices != null ? offlineDevices : 0L);

            List<Device> allDevices = deviceRepository.selectList(null);
            Map<String, Long> devicesByType = new HashMap<>();
            if (allDevices != null) {
                for (Device device : allDevices) {
                    String type = device.getType() != null ? device.getType() : "unknown";
                    devicesByType.merge(type, 1L, Long::sum);
                }
            }
            result.put("devicesByType", devicesByType);

        } catch (Exception e) {
            log.error("获取设备状态统计失败", e);
            result.put("totalDevices", 0L);
            result.put("onlineDevices", 0L);
            result.put("offlineDevices", 0L);
            result.put("devicesByType", new HashMap<>());
            result.put("error", e.getMessage());
        }

        return result;
    }
}
