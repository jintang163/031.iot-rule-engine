package com.iot.ruleengine.stats;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.ActionLog;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.entity.RuleExecutionStats;
import com.iot.ruleengine.repository.ActionLogRepository;
import com.iot.ruleengine.repository.RuleExecutionStatsRepository;
import com.iot.ruleengine.repository.RuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RuleStatsService {

    private final RuleExecutionStatsRepository statsRepository;
    private final ActionLogRepository actionLogRepository;
    private final RuleRepository ruleRepository;
    private final CostEstimationConfig costConfig;

    private final ConcurrentHashMap<Long, DailyAccumulator> dailyAccumulators = new ConcurrentHashMap<>();

    @Autowired
    public RuleStatsService(RuleExecutionStatsRepository statsRepository,
                            ActionLogRepository actionLogRepository,
                            RuleRepository ruleRepository,
                            CostEstimationConfig costConfig) {
        this.statsRepository = statsRepository;
        this.actionLogRepository = actionLogRepository;
        this.ruleRepository = ruleRepository;
        this.costConfig = costConfig;
    }

    public void recordExecution(Long ruleId, String ruleName, long executionMs,
                                int actionCount, List<String> actionTypes) {
        LocalDate today = LocalDate.now();
        long dayKey = today.toEpochDay() * 100000L + (ruleId != null ? ruleId : 0);

        DailyAccumulator acc = dailyAccumulators.computeIfAbsent(dayKey, k -> new DailyAccumulator(ruleId, ruleName, today));
        synchronized (acc) {
            acc.triggerCount++;
            acc.actionCount += actionCount;
            acc.totalExecutionMs += executionMs;
            acc.maxExecutionMs = Math.max(acc.maxExecutionMs, executionMs);
            if (actionTypes != null) {
                for (String actionType : actionTypes) {
                    acc.estimatedCostYuan = acc.estimatedCostYuan.add(estimateSingleActionCost(actionType));
                }
            }
        }
    }

    private BigDecimal estimateSingleActionCost(String actionType) {
        if (!costConfig.isEnergyConsumingAction(actionType)) {
            return BigDecimal.ZERO;
        }
        BigDecimal powerKw = costConfig.getPowerKw(actionType);
        BigDecimal runtimeHours = costConfig.getAvgRuntimeMinutesPerTrigger()
                .divide(new BigDecimal("60"), 6, RoundingMode.HALF_UP);
        BigDecimal kwh = powerKw.multiply(runtimeHours);
        return kwh.multiply(costConfig.getElectricityPricePerKwh())
                .setScale(4, RoundingMode.HALF_UP);
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void flushAccumulatorsToDb() {
        flushAllAccumulators();
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void flushYesterdayAccumulators() {
        flushAllAccumulators();
        backfillFromActionLogs();
    }

    private synchronized void flushAllAccumulators() {
        if (dailyAccumulators.isEmpty()) {
            return;
        }
        List<Long> keysToRemove = new ArrayList<>();
        for (Map.Entry<Long, DailyAccumulator> entry : dailyAccumulators.entrySet()) {
            DailyAccumulator acc = entry.getValue();
            if (acc.triggerCount > 0) {
                try {
                    upsertStats(acc);
                } catch (Exception e) {
                    log.error("刷新规则统计失败, ruleId={}", acc.ruleId, e);
                    continue;
                }
            }
            if (acc.statDate.isBefore(LocalDate.now())) {
                keysToRemove.add(entry.getKey());
            } else {
                acc.resetCounters();
            }
        }
        for (Long key : keysToRemove) {
            dailyAccumulators.remove(key);
        }
    }

    private void upsertStats(DailyAccumulator acc) {
        QueryWrapper<RuleExecutionStats> qw = new QueryWrapper<>();
        qw.eq("rule_id", acc.ruleId).eq("stat_date", acc.statDate);
        RuleExecutionStats existing = statsRepository.selectOne(qw);

        if (existing == null) {
            RuleExecutionStats stats = new RuleExecutionStats();
            stats.setRuleId(acc.ruleId);
            stats.setRuleName(acc.ruleName);
            stats.setStatDate(acc.statDate);
            stats.setTriggerCount(acc.triggerCount);
            stats.setActionCount(acc.actionCount);
            stats.setTotalExecutionMs(acc.totalExecutionMs);
            stats.setMaxExecutionMs(acc.maxExecutionMs);
            stats.setAvgExecutionMs(acc.triggerCount > 0
                    ? BigDecimal.valueOf(acc.totalExecutionMs).divide(BigDecimal.valueOf(acc.triggerCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            stats.setEstimatedCostYuan(acc.estimatedCostYuan);
            stats.setCreateTime(LocalDateTime.now());
            stats.setUpdateTime(LocalDateTime.now());
            statsRepository.insert(stats);
        } else {
            long newTriggerCount = existing.getTriggerCount() != null ? existing.getTriggerCount() + acc.triggerCount : acc.triggerCount;
            long newActionCount = existing.getActionCount() != null ? existing.getActionCount() + acc.actionCount : acc.actionCount;
            long newTotalMs = existing.getTotalExecutionMs() != null ? existing.getTotalExecutionMs() + acc.totalExecutionMs : acc.totalExecutionMs;
            long newMaxMs = Math.max(existing.getMaxExecutionMs() != null ? existing.getMaxExecutionMs() : 0L, acc.maxExecutionMs);
            BigDecimal newCost = (existing.getEstimatedCostYuan() != null ? existing.getEstimatedCostYuan() : BigDecimal.ZERO)
                    .add(acc.estimatedCostYuan);

            UpdateWrapper<RuleExecutionStats> uw = new UpdateWrapper<>();
            uw.eq("id", existing.getId())
                    .set("trigger_count", newTriggerCount)
                    .set("action_count", newActionCount)
                    .set("total_execution_ms", newTotalMs)
                    .set("max_execution_ms", newMaxMs)
                    .set("avg_execution_ms", newTriggerCount > 0
                            ? BigDecimal.valueOf(newTotalMs).divide(BigDecimal.valueOf(newTriggerCount), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO)
                    .set("estimated_cost_yuan", newCost)
                    .set("update_time", LocalDateTime.now());
            statsRepository.update(null, uw);
        }
    }

    private void backfillFromActionLogs() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            QueryWrapper<ActionLog> qw = new QueryWrapper<>();
            qw.ge("execute_time", yesterday.atStartOfDay())
                    .lt("execute_time", yesterday.atTime(LocalTime.MAX))
                    .isNotNull("rule_id");
            List<ActionLog> logs = actionLogRepository.selectList(qw);
            if (logs == null || logs.isEmpty()) {
                return;
            }
            Map<Long, List<ActionLog>> grouped = logs.stream()
                    .filter(l -> l.getRuleId() != null)
                    .collect(Collectors.groupingBy(ActionLog::getRuleId));

            for (Map.Entry<Long, List<ActionLog>> entry : grouped.entrySet()) {
                Long ruleId = entry.getKey();
                List<ActionLog> ruleLogs = entry.getValue();
                Rule rule = ruleRepository.selectById(ruleId);
                String ruleName = rule != null ? rule.getName() : ("Rule_" + ruleId);

                DailyAccumulator acc = new DailyAccumulator(ruleId, ruleName, yesterday);
                acc.triggerCount = ruleLogs.size();
                acc.actionCount = (long) ruleLogs.size();
                for (ActionLog log : ruleLogs) {
                    acc.estimatedCostYuan = acc.estimatedCostYuan.add(estimateSingleActionCost(log.getActionType()));
                }
                try {
                    upsertStats(acc);
                } catch (Exception e) {
                    log.error("回填ActionLog统计失败, ruleId={}", ruleId, e);
                }
            }
            log.info("回填昨日规则统计完成, 覆盖{}条规则", grouped.size());
        } catch (Exception e) {
            log.error("回填ActionLog统计异常", e);
        }
    }

    public Map<String, Object> getOverview(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(7);
        if (endDate == null) endDate = LocalDate.now();

        List<RuleExecutionStats> aggregated = statsRepository.aggregateStatsByDateRange(startDate, endDate);
        List<RuleExecutionStats> dailyTrend = statsRepository.aggregateDailyTrend(startDate, endDate);

        long totalTriggers = 0;
        long totalActions = 0;
        long totalExecutionMs = 0;
        long maxExecutionMs = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        long count = 0;

        for (RuleExecutionStats s : aggregated) {
            if (s.getTriggerCount() != null) totalTriggers += s.getTriggerCount();
            if (s.getActionCount() != null) totalActions += s.getActionCount();
            if (s.getTotalExecutionMs() != null) totalExecutionMs += s.getTotalExecutionMs();
            if (s.getMaxExecutionMs() != null) maxExecutionMs = Math.max(maxExecutionMs, s.getMaxExecutionMs());
            if (s.getEstimatedCostYuan() != null) totalCost = totalCost.add(s.getEstimatedCostYuan());
            count++;
        }

        BigDecimal avgExecutionMs = count > 0
                ? BigDecimal.valueOf(totalExecutionMs).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Map<String, Object>> ruleRankings = new ArrayList<>();
        for (RuleExecutionStats s : aggregated) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ruleId", s.getRuleId());
            map.put("ruleName", s.getRuleName());
            map.put("triggerCount", s.getTriggerCount());
            map.put("actionCount", s.getActionCount());
            map.put("avgExecutionMs", s.getAvgExecutionMs());
            map.put("maxExecutionMs", s.getMaxExecutionMs());
            map.put("estimatedCostYuan", s.getEstimatedCostYuan() != null
                    ? s.getEstimatedCostYuan().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            ruleRankings.add(map);
        }

        List<Map<String, Object>> dailySeries = new ArrayList<>();
        for (RuleExecutionStats s : dailyTrend) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("date", s.getStatDate().toString());
            map.put("triggerCount", s.getTriggerCount());
            map.put("actionCount", s.getActionCount());
            map.put("estimatedCostYuan", s.getEstimatedCostYuan() != null
                    ? s.getEstimatedCostYuan().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            dailySeries.add(map);
        }

        Map<String, Object> costBreakdown = buildCostBreakdown(aggregated);
        Map<String, Object> optimizationTips = buildOptimizationTips(aggregated);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("startDate", startDate.toString());
        overview.put("endDate", endDate.toString());
        overview.put("totalTriggers", totalTriggers);
        overview.put("totalActions", totalActions);
        overview.put("avgExecutionMs", avgExecutionMs);
        overview.put("maxExecutionMs", maxExecutionMs);
        overview.put("totalCostYuan", totalCost.setScale(2, RoundingMode.HALF_UP));
        overview.put("costBreakdown", costBreakdown);
        overview.put("ruleRankings", ruleRankings);
        overview.put("dailyTrend", dailySeries);
        overview.put("optimizationTips", optimizationTips);
        overview.put("costConfig", buildCostConfigInfo());

        return overview;
    }

    private Map<String, Object> buildCostBreakdown(List<RuleExecutionStats> aggregated) {
        BigDecimal airconCost = BigDecimal.ZERO;
        BigDecimal lightCost = BigDecimal.ZERO;
        BigDecimal heaterCost = BigDecimal.ZERO;
        BigDecimal otherCost = BigDecimal.ZERO;

        for (RuleExecutionStats s : aggregated) {
            String name = s.getRuleName() != null ? s.getRuleName().toLowerCase() : "";
            BigDecimal cost = s.getEstimatedCostYuan() != null ? s.getEstimatedCostYuan() : BigDecimal.ZERO;
            if (name.contains("空调") || name.contains("aircon") || name.contains("ac")) {
                airconCost = airconCost.add(cost);
            } else if (name.contains("灯") || name.contains("light")) {
                lightCost = lightCost.add(cost);
            } else if (name.contains("暖气") || name.contains("加热") || name.contains("heater")) {
                heaterCost = heaterCost.add(cost);
            } else {
                otherCost = otherCost.add(cost);
            }
        }

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("airConditioning", airconCost.setScale(2, RoundingMode.HALF_UP));
        breakdown.put("lighting", lightCost.setScale(2, RoundingMode.HALF_UP));
        breakdown.put("heating", heaterCost.setScale(2, RoundingMode.HALF_UP));
        breakdown.put("other", otherCost.setScale(2, RoundingMode.HALF_UP));
        return breakdown;
    }

    private List<Map<String, Object>> buildOptimizationTips(List<RuleExecutionStats> aggregated) {
        List<Map<String, Object>> tips = new ArrayList<>();

        List<RuleExecutionStats> sortedByCost = new ArrayList<>(aggregated);
        sortedByCost.sort((a, b) -> {
            BigDecimal ca = a.getEstimatedCostYuan() != null ? a.getEstimatedCostYuan() : BigDecimal.ZERO;
            BigDecimal cb = b.getEstimatedCostYuan() != null ? b.getEstimatedCostYuan() : BigDecimal.ZERO;
            return cb.compareTo(ca);
        });

        for (int i = 0; i < Math.min(3, sortedByCost.size()); i++) {
            RuleExecutionStats s = sortedByCost.get(i);
            BigDecimal cost = s.getEstimatedCostYuan() != null ? s.getEstimatedCostYuan() : BigDecimal.ZERO;
            if (cost.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> tip = new LinkedHashMap<>();
                tip.put("level", "high");
                tip.put("category", "cost");
                tip.put("title", "高能耗规则建议优化");
                tip.put("ruleId", s.getRuleId());
                tip.put("ruleName", s.getRuleName());
                tip.put("detail", String.format("规则「%s」预计产生电费 ¥%s，建议检查触发条件或降低动作频率。",
                        s.getRuleName(), cost.setScale(2, RoundingMode.HALF_UP)));
                tip.put("estimatedSavings", cost.multiply(new BigDecimal("0.3")).setScale(2, RoundingMode.HALF_UP));
                tips.add(tip);
            }
        }

        for (RuleExecutionStats s : aggregated) {
            BigDecimal avg = s.getAvgExecutionMs() != null ? s.getAvgExecutionMs() : BigDecimal.ZERO;
            if (avg.compareTo(new BigDecimal("500")) > 0) {
                Map<String, Object> tip = new LinkedHashMap<>();
                tip.put("level", "medium");
                tip.put("category", "performance");
                tip.put("title", "规则执行耗时较长");
                tip.put("ruleId", s.getRuleId());
                tip.put("ruleName", s.getRuleName());
                tip.put("detail", String.format("规则「%s」平均执行耗时 %s ms，建议简化条件表达式。",
                        s.getRuleName(), avg.setScale(0, RoundingMode.HALF_UP)));
                tips.add(tip);
            }
        }

        for (RuleExecutionStats s : aggregated) {
            long triggers = s.getTriggerCount() != null ? s.getTriggerCount() : 0;
            long actions = s.getActionCount() != null ? s.getActionCount() : 0;
            if (triggers > 0 && actions * 1.0 / triggers > 3) {
                Map<String, Object> tip = new LinkedHashMap<>();
                tip.put("level", "medium");
                tip.put("category", "action");
                tip.put("title", "规则触发动作用户过多");
                tip.put("ruleId", s.getRuleId());
                tip.put("ruleName", s.getRuleName());
                tip.put("detail", String.format("规则「%s」每次触发平均执行 %d 个动作，考虑合并动作以减少设备指令。",
                        s.getRuleName(), Math.round(actions * 1.0 / triggers)));
                tips.add(tip);
            }
        }

        if (tips.isEmpty()) {
            Map<String, Object> tip = new LinkedHashMap<>();
            tip.put("level", "info");
            tip.put("category", "general");
            tip.put("title", "规则运行健康");
            tip.put("detail", "当前所有规则的能耗与执行耗时都在合理范围内。");
            tips.add(tip);
        }

        return tips;
    }

    private Map<String, Object> buildCostConfigInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("electricityPricePerKwh", costConfig.getElectricityPricePerKwh());
        info.put("avgRuntimeMinutesPerTrigger", costConfig.getAvgRuntimeMinutesPerTrigger());
        info.put("devicePowerKw", costConfig.getDevicePowerKw());
        return info;
    }

    public String exportCsv(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> overview = getOverview(startDate, endDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rankings = (List<Map<String, Object>>) overview.get("ruleRankings");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> daily = (List<Map<String, Object>>) overview.get("dailyTrend");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tips = (List<Map<String, Object>>) overview.get("optimizationTips");
        @SuppressWarnings("unchecked")
        Map<String, Object> costBrk = (Map<String, Object>) overview.get("costBreakdown");

        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append("规则执行统计与成本分析报表\n");
        sb.append("统计周期: ").append(overview.get("startDate")).append(" 至 ").append(overview.get("endDate")).append("\n\n");

        sb.append("汇总指标\n");
        sb.append("总触发次数,总动作次数,平均执行耗时(ms),最大执行耗时(ms),预估电费(元)\n");
        sb.append(overview.get("totalTriggers")).append(',')
                .append(overview.get("totalActions")).append(',')
                .append(overview.get("avgExecutionMs")).append(',')
                .append(overview.get("maxExecutionMs")).append(',')
                .append(overview.get("totalCostYuan")).append('\n');
        sb.append('\n');

        sb.append("能耗分类\n");
        sb.append("空调电费,照明电费,采暖电费,其他电费\n");
        sb.append(costBrk.get("airConditioning")).append(',')
                .append(costBrk.get("lighting")).append(',')
                .append(costBrk.get("heating")).append(',')
                .append(costBrk.get("other")).append('\n');
        sb.append('\n');

        sb.append("规则排名\n");
        sb.append("规则ID,规则名称,触发次数,动作次数,平均耗时(ms),最大耗时(ms),预估电费(元)\n");
        for (Map<String, Object> r : rankings) {
            sb.append(r.get("ruleId")).append(',')
                    .append(escapeCsv(String.valueOf(r.get("ruleName")))).append(',')
                    .append(r.get("triggerCount")).append(',')
                    .append(r.get("actionCount")).append(',')
                    .append(r.get("avgExecutionMs")).append(',')
                    .append(r.get("maxExecutionMs")).append(',')
                    .append(r.get("estimatedCostYuan")).append('\n');
        }
        sb.append('\n');

        sb.append("每日趋势\n");
        sb.append("日期,触发次数,动作次数,预估电费(元)\n");
        for (Map<String, Object> d : daily) {
            sb.append(d.get("date")).append(',')
                    .append(d.get("triggerCount")).append(',')
                    .append(d.get("actionCount")).append(',')
                    .append(d.get("estimatedCostYuan")).append('\n');
        }
        sb.append('\n');

        sb.append("优化建议\n");
        sb.append("级别,类别,标题,规则ID,规则名称,详情,预计节省(元)\n");
        for (Map<String, Object> t : tips) {
            sb.append(t.getOrDefault("level", "")).append(',')
                    .append(t.getOrDefault("category", "")).append(',')
                    .append(escapeCsv(String.valueOf(t.getOrDefault("title", "")))).append(',')
                    .append(t.getOrDefault("ruleId", "")).append(',')
                    .append(escapeCsv(String.valueOf(t.getOrDefault("ruleName", "")))).append(',')
                    .append(escapeCsv(String.valueOf(t.getOrDefault("detail", "")))).append(',')
                    .append(t.getOrDefault("estimatedSavings", "")).append('\n');
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public String exportJson(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> overview = getOverview(startDate, endDate);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("reportType", "rule_execution_stats_and_cost_analysis");
        report.putAll(overview);
        return JSON.toJSONString(report, true);
    }

    private static class DailyAccumulator {
        final Long ruleId;
        final String ruleName;
        final LocalDate statDate;
        long triggerCount;
        long actionCount;
        long totalExecutionMs;
        long maxExecutionMs;
        BigDecimal estimatedCostYuan = BigDecimal.ZERO;

        DailyAccumulator(Long ruleId, String ruleName, LocalDate statDate) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.statDate = statDate;
        }

        void resetCounters() {
            this.triggerCount = 0;
            this.actionCount = 0;
            this.totalExecutionMs = 0;
            this.maxExecutionMs = 0;
            this.estimatedCostYuan = BigDecimal.ZERO;
        }
    }
}
