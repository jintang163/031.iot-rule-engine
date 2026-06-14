-- ============================================================
-- 5. 规则版本快照表 (rule_version)
-- 记录规则每次保存的版本快照，支持版本对比和回滚
-- ============================================================
DROP TABLE IF EXISTS `rule_version`;
CREATE TABLE `rule_version` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `rule_id` BIGINT NOT NULL COMMENT '规则ID',
  `version` INT NOT NULL DEFAULT 1 COMMENT '版本号（递增）',
  `name` VARCHAR(200) NOT NULL COMMENT '规则名称（快照时的名称）',
  `description` VARCHAR(500) COMMENT '规则描述',
  `rule_json` MEDIUMTEXT COMMENT 'JSON格式规则定义（前端画布）',
  `drl_content` MEDIUMTEXT COMMENT 'Drools DRL规则内容',
  `aviator_expression` MEDIUMTEXT COMMENT 'Aviator表达式',
  `aviator_actions` MEDIUMTEXT COMMENT 'Aviator动作定义JSON',
  `status` TINYINT DEFAULT 0 COMMENT '快照时的规则状态: 1启用 0禁用',
  `priority` INT DEFAULT 5 COMMENT '优先级1-10',
  `mutex_group` VARCHAR(100) COMMENT '互斥规则组名',
  `window_enabled` TINYINT DEFAULT 0 COMMENT '是否启用时间窗口',
  `window_type` VARCHAR(20) DEFAULT 'TUMBLING' COMMENT '窗口类型',
  `window_duration` INT DEFAULT 60 COMMENT '窗口时长(秒)',
  `window_aggregation` VARCHAR(50) COMMENT '窗口聚合函数',
  `window_field` VARCHAR(100) COMMENT '窗口聚合字段',
  `window_operator` VARCHAR(10) COMMENT '窗口比较运算符',
  `window_threshold` DECIMAL(10,2) COMMENT '窗口比较阈值',
  `cooldown_seconds` INT DEFAULT 0 COMMENT '重复触发冷却时间(秒)',
  `chain_trigger_enabled` TINYINT DEFAULT 0 COMMENT '是否启用规则链触发',
  `chain_next_rule_ids` VARCHAR(500) COMMENT '规则链: 触发后启用的规则ID列表',
  `chain_disable_self` TINYINT DEFAULT 0 COMMENT '触发后是否禁用自身',
  `change_summary` VARCHAR(500) COMMENT '变更摘要（自动生成或用户填写）',
  `comment` VARCHAR(1000) COMMENT '版本注释/修改原因说明',
  `create_by` VARCHAR(100) COMMENT '创建人',
  `tenant_id` BIGINT COMMENT '租户ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_rule_id` (`rule_id`),
  INDEX `idx_version` (`rule_id`, `version`),
  INDEX `idx_tenant_id` (`tenant_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则版本快照表';
