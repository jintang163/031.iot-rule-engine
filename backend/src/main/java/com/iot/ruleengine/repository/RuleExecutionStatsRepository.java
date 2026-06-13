package com.iot.ruleengine.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.RuleExecutionStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RuleExecutionStatsRepository extends BaseMapper<RuleExecutionStats> {

    @Select("SELECT rule_id, rule_name, " +
            " SUM(trigger_count) as trigger_count, " +
            " SUM(action_count) as action_count, " +
            " SUM(total_execution_ms) as total_execution_ms, " +
            " MAX(max_execution_ms) as max_execution_ms, " +
            " AVG(avg_execution_ms) as avg_execution_ms, " +
            " SUM(estimated_cost_yuan) as estimated_cost_yuan " +
            " FROM rule_execution_stats " +
            " WHERE stat_date BETWEEN #{startDate} AND #{endDate} " +
            " GROUP BY rule_id, rule_name " +
            " ORDER BY trigger_count DESC")
    List<RuleExecutionStats> aggregateStatsByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Select("SELECT stat_date, " +
            " SUM(trigger_count) as trigger_count, " +
            " SUM(action_count) as action_count, " +
            " SUM(estimated_cost_yuan) as estimated_cost_yuan " +
            " FROM rule_execution_stats " +
            " WHERE stat_date BETWEEN #{startDate} AND #{endDate} " +
            " GROUP BY stat_date " +
            " ORDER BY stat_date ASC")
    List<RuleExecutionStats> aggregateDailyTrend(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
