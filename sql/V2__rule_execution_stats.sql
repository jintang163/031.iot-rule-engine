-- ============================================================
-- IoT Rule Engine - V2 统计表迁移脚本
-- 功能: 新增 rule_execution_stats 表，支撑规则执行统计与成本分析
-- 数据库: MySQL 8.0+
-- 执行顺序: 在 init.sql 之后执行
-- ============================================================

USE iot_rule_engine;

-- ============================================================
-- rule_execution_stats: 规则执行统计日维度表
-- 每条规则每天一行，记录触发次数、动作次数、执行耗时、预估电费
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_execution_stats` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `rule_id` BIGINT NOT NULL COMMENT '规则ID',
  `rule_name` VARCHAR(200) DEFAULT NULL COMMENT '规则名称(冗余)',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `trigger_count` BIGINT NOT NULL DEFAULT 0 COMMENT '触发次数',
  `action_count` BIGINT NOT NULL DEFAULT 0 COMMENT '触发的动作总次数',
  `total_execution_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '总执行耗时(毫秒)',
  `max_execution_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '单次最大执行耗时(毫秒)',
  `avg_execution_ms` DECIMAL(18,2) DEFAULT NULL COMMENT '平均执行耗时(毫秒) = total_execution_ms / trigger_count',
  `estimated_cost_yuan` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '预估电费(元)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_rule_date` (`rule_id`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_rule_id` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则执行统计日维度表';
