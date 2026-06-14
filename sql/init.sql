-- ============================================================
-- IoT Rule Engine - MySQL Database Initialization Script
-- Database Version: MySQL 8.0+
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS iot_rule_engine DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE iot_rule_engine;

-- ============================================================
-- 1. 规则表 (rule)
-- 存储用户通过前端画布编排的规则定义及编译后的DRL内容
-- ============================================================
DROP TABLE IF EXISTS `rule`;
CREATE TABLE `rule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `name` VARCHAR(200) NOT NULL COMMENT '规则名称',
  `description` VARCHAR(500) COMMENT '规则描述',
  `rule_json` MEDIUMTEXT COMMENT 'JSON格式规则定义（前端画布）',
  `drl_content` MEDIUMTEXT COMMENT 'Drools DRL规则内容',
  `aviator_expression` MEDIUMTEXT COMMENT 'Aviator表达式' AFTER drl_content,
  `aviator_actions` MEDIUMTEXT COMMENT 'Aviator动作定义JSON' AFTER aviator_expression,
  `window_enabled` TINYINT DEFAULT 0 COMMENT '是否启用时间窗口: 0否 1是',
  `window_type` VARCHAR(20) DEFAULT 'TUMBLING' COMMENT '窗口类型: TUMBLING(滚动)/SLIDING(滑动)/SESSION(会话)',
  `window_duration` INT DEFAULT 60 COMMENT '窗口时长(秒)',
  `window_aggregation` VARCHAR(50) COMMENT '窗口聚合函数: SUM/AVG/MAX/MIN/COUNT/DELTA(差值)',
  `window_field` VARCHAR(100) COMMENT '窗口聚合字段: temperature/humidity等',
  `window_operator` VARCHAR(10) COMMENT '窗口比较运算符: >/</>=/<=/==',
  `window_threshold` DECIMAL(10,2) COMMENT '窗口比较阈值',
  `cooldown_seconds` INT DEFAULT 0 COMMENT '重复触发冷却时间(秒), 0表示不限制',
  `chain_trigger_enabled` TINYINT DEFAULT 0 COMMENT '是否启用规则链触发: 0否 1是',
  `chain_next_rule_ids` VARCHAR(500) COMMENT '规则链: 本规则触发后启用的规则ID列表(逗号分隔)',
  `chain_disable_self` TINYINT DEFAULT 0 COMMENT '规则触发后是否禁用自身: 0否 1是',
  `status` TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  `priority` INT DEFAULT 5 COMMENT '优先级1-10',
  `mutex_group` VARCHAR(100) COMMENT '互斥规则组名',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_status` (`status`),
  INDEX `idx_mutex_group` (`mutex_group`),
  INDEX `idx_window_enabled` (`window_enabled`),
  INDEX `idx_chain_enabled` (`chain_trigger_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则表';

-- 向后兼容：老库升级ALTER语句
-- ALTER TABLE rule ADD COLUMN aviator_expression MEDIUMTEXT COMMENT 'Aviator表达式' AFTER drl_content;
-- ALTER TABLE rule ADD COLUMN aviator_actions MEDIUMTEXT COMMENT 'Aviator动作定义JSON' AFTER aviator_expression;
-- ALTER TABLE rule ADD COLUMN `window_enabled` TINYINT DEFAULT 0 COMMENT '是否启用时间窗口' AFTER aviator_actions;
-- ALTER TABLE rule ADD COLUMN `window_type` VARCHAR(20) DEFAULT 'TUMBLING' COMMENT '窗口类型' AFTER window_enabled;
-- ALTER TABLE rule ADD COLUMN `window_duration` INT DEFAULT 60 COMMENT '窗口时长(秒)' AFTER window_type;
-- ALTER TABLE rule ADD COLUMN `window_aggregation` VARCHAR(50) COMMENT '窗口聚合函数' AFTER window_duration;
-- ALTER TABLE rule ADD COLUMN `window_field` VARCHAR(100) COMMENT '窗口聚合字段' AFTER window_aggregation;
-- ALTER TABLE rule ADD COLUMN `window_operator` VARCHAR(10) COMMENT '窗口比较运算符' AFTER window_field;
-- ALTER TABLE rule ADD COLUMN `window_threshold` DECIMAL(10,2) COMMENT '窗口比较阈值' AFTER window_operator;
-- ALTER TABLE rule ADD COLUMN `cooldown_seconds` INT DEFAULT 0 COMMENT '重复触发冷却时间(秒)' AFTER window_threshold;
-- ALTER TABLE rule ADD COLUMN `chain_trigger_enabled` TINYINT DEFAULT 0 COMMENT '是否启用规则链触发' AFTER cooldown_seconds;
-- ALTER TABLE rule ADD COLUMN `chain_next_rule_ids` VARCHAR(500) COMMENT '规则链: 触发后启用的规则ID列表' AFTER chain_trigger_enabled;
-- ALTER TABLE rule ADD COLUMN `chain_disable_self` TINYINT DEFAULT 0 COMMENT '触发后是否禁用自身' AFTER chain_next_rule_ids;
-- ALTER TABLE rule ADD INDEX `idx_window_enabled` (`window_enabled`);
-- ALTER TABLE rule ADD INDEX `idx_chain_enabled` (`chain_trigger_enabled`);

-- ============================================================
-- 2. 设备注册表 (iot_device)
-- 管理所有接入平台的物联网设备元数据及实时状态
-- ============================================================
DROP TABLE IF EXISTS `iot_device`;
CREATE TABLE `iot_device` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `device_id` VARCHAR(100) NOT NULL UNIQUE COMMENT '设备唯一ID(MQTT ClientID)',
  `name` VARCHAR(200) NOT NULL COMMENT '设备名称',
  `type` VARCHAR(50) NOT NULL COMMENT '设备类型: aircon/light/sensor_temp/sensor_humidity/sensor_presence',
  `room` VARCHAR(200) COMMENT '所属房间',
  `protocol` VARCHAR(20) DEFAULT 'MQTT' COMMENT '通信协议: MQTT/HTTP',
  `actions` JSON COMMENT '支持的动作列表JSON',
  `status` JSON COMMENT '当前状态JSON如{"power":"off","temperature":26}',
  `online` TINYINT DEFAULT 0 COMMENT '1在线 0离线',
  `last_online_time` DATETIME COMMENT '最后在线时间',
  `location` VARCHAR(200) COMMENT '安装位置',
  `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_device_id` (`device_id`),
  INDEX `idx_type` (`type`),
  INDEX `idx_online` (`online`),
  INDEX `idx_room` (`room`),
  INDEX `idx_protocol` (`protocol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备注册表';

-- 向后兼容：老库从 device 表迁移到 iot_device，以及补充新字段，单独执行以下语句
-- RENAME TABLE `device` TO `iot_device`;
-- ALTER TABLE `iot_device` ADD COLUMN `room` VARCHAR(200) COMMENT '所属房间' AFTER `type`;
-- ALTER TABLE `iot_device` ADD COLUMN `protocol` VARCHAR(20) DEFAULT 'MQTT' COMMENT '通信协议: MQTT/HTTP' AFTER `room`;
-- ALTER TABLE `iot_device` ADD COLUMN `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除' AFTER `location`;
-- ALTER TABLE `iot_device` ADD INDEX `idx_room` (`room`);
-- ALTER TABLE `iot_device` ADD INDEX `idx_protocol` (`protocol`);

-- ============================================================
-- 3. 动作执行日志表 (action_log)
-- 记录每条规则触发的设备动作执行结果，用于审计和问题排查
-- ============================================================
DROP TABLE IF EXISTS `action_log`;
CREATE TABLE `action_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `rule_id` BIGINT COMMENT '触发的规则ID',
  `rule_name` VARCHAR(200) COMMENT '规则名称冗余',
  `action_type` VARCHAR(50) NOT NULL COMMENT '动作类型',
  `action_params` JSON COMMENT '动作参数JSON',
  `device_id` VARCHAR(100) COMMENT '目标设备ID',
  `result` TINYINT DEFAULT 1 COMMENT '1成功 0失败',
  `retry_count` INT DEFAULT 0 COMMENT '重试次数',
  `error_msg` VARCHAR(500) COMMENT '失败原因',
  `execute_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  INDEX `idx_rule_id` (`rule_id`),
  INDEX `idx_device_id` (`device_id`),
  INDEX `idx_result` (`result`),
  INDEX `idx_execute_time` (`execute_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动作执行日志';

-- ============================================================
-- 示例数据插入
-- ============================================================

-- 设备示例数据（扩展版）
INSERT INTO `iot_device` (`device_id`, `name`, `type`, `room`, `protocol`, `actions`, `status`, `online`, `last_online_time`, `location`) VALUES
('sensor_temp_001', '客厅温度传感器', 'sensor_temp', '客厅', 'MQTT',
  '["report_temperature", "report_humidity"]',
  '{"temperature": 28, "humidity": 55}',
  1, NOW(), '客厅-吊顶'),
('sensor_presence_001', '客厅人体传感器', 'sensor_presence', '客厅', 'MQTT',
  '["report_presence"]',
  '{"presence": false}',
  1, NOW(), '客厅-门口'),
('aircon_living_001', '客厅空调', 'aircon', '客厅', 'MQTT',
  '["turn_on", "turn_off", "set_temperature", "set_mode"]',
  '{"power": "off", "temperature": 26, "mode": "cool"}',
  1, NOW(), '客厅-墙面'),
('light_living_001', '客厅主灯', 'light', '客厅', 'MQTT',
  '["turn_on", "turn_off", "set_brightness"]',
  '{"power": "on", "brightness": 80}',
  1, NOW(), '客厅-吊顶'),
('light_bedroom_001', '卧室主灯', 'light', '卧室', 'MQTT',
  '["turn_on", "turn_off", "set_brightness"]',
  '{"power": "off", "brightness": 100}',
  1, NOW(), '卧室-吊顶'),
('sensor_presence_002', '卧室人体传感器', 'sensor_presence', '卧室', 'MQTT',
  '["report_presence"]',
  '{"presence": true}',
  1, NOW(), '卧室-门口'),
('sensor_temp_002', '卧室温度传感器', 'sensor_temp', '卧室', 'MQTT',
  '["report_temperature", "report_humidity"]',
  '{"temperature": 26, "humidity": 50}',
  1, NOW(), '卧室-墙面'),
('curtain_living_001', '客厅窗帘', 'curtain', '客厅', 'MQTT',
  '["open", "close", "set_position"]',
  '{"position": 50}',
  1, NOW(), '客厅-窗户');

-- 规则示例数据（1条：温度>30度且房间无人时自动开空调）
INSERT INTO `rule` (`name`, `description`, `rule_json`, `drl_content`, `status`, `priority`, `mutex_group`) VALUES
('高温无人自动开空调', '当客厅温度超过30度且检测到无人时，自动开启空调制冷模式，温度设定26度',

-- rule_json: 前端React Flow画布定义
'{
  "nodes": [
    {"id": "start", "type": "startNode", "position": {"x": 50, "y": 150}, "data": {"label": "触发开始"}},
    {"id": "cond_temp", "type": "conditionNode", "position": {"x": 250, "y": 80}, "data": {"label": "温度>30°C", "deviceId": "sensor_temp_001", "field": "temperature", "operator": ">", "value": 30}},
    {"id": "cond_presence", "type": "conditionNode", "position": {"x": 250, "y": 220}, "data": {"label": "无人", "deviceId": "sensor_presence_001", "field": "presence", "operator": "==", "value": false}},
    {"id": "logic_and", "type": "logicNode", "position": {"x": 450, "y": 150}, "data": {"label": "AND", "type": "AND"}},
    {"id": "action_aircon", "type": "actionNode", "position": {"x": 650, "y": 150}, "data": {"label": "开空调26°C制冷", "deviceId": "aircon_living_001", "action": "turn_on", "params": {"temperature": 26, "mode": "cool"}}}
  ],
  "edges": [
    {"id": "e1", "source": "start", "target": "logic_and", "sourceHandle": "out"},
    {"id": "e2", "source": "cond_temp", "target": "logic_and", "sourceHandle": "out", "targetHandle": "in1"},
    {"id": "e3", "source": "cond_presence", "target": "logic_and", "sourceHandle": "out", "targetHandle": "in2"},
    {"id": "e4", "source": "logic_and", "target": "action_aircon", "sourceHandle": "out"}
  ]
}',

-- drl_content: 编译后的Drools DRL规则
'package com.iot.rule.engine;

import com.iot.rule.engine.domain.TelemetryEvent;
import com.iot.rule.engine.domain.ActionCommand;
import java.util.Map;
import java.util.HashMap;

global com.iot.rule.engine.service.ActionExecutor actionExecutor;

rule "高温无人自动开空调"
    salience 100
    no-loop true
    when
        $tempEvent : TelemetryEvent(
            deviceId == "sensor_temp_001",
            data["temperature"] != null,
            ((Number)data["temperature"]).doubleValue() > 30
        )
        $presenceEvent : TelemetryEvent(
            deviceId == "sensor_presence_001",
            data["presence"] != null,
            data["presence"] == Boolean.FALSE
        )
    then
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 26);
        params.put("mode", "cool");
        ActionCommand cmd = new ActionCommand();
        cmd.setRuleId(1L);
        cmd.setRuleName("高温无人自动开空调");
        cmd.setDeviceId("aircon_living_001");
        cmd.setActionType("turn_on");
        cmd.setActionParams(params);
        actionExecutor.execute(cmd);
end',

1, 10, 'climate_control');

-- 动作执行日志示例数据（含大量手动操作，用于AI推荐分析）
INSERT INTO `action_log` (`rule_id`, `rule_name`, `action_type`, `action_params`, `device_id`, `result`, `retry_count`, `error_msg`, `execute_time`) VALUES
-- 规则触发的动作
(1, '高温无人自动开空调', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(1, '高温无人自动开空调', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(1, '高温无人自动开空调', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 0, 2, '设备响应超时，MQTT未收到ACK', DATE_SUB(NOW(), INTERVAL 30 MINUTE)),

-- 手动操作：频繁手动关灯（客厅） - 用于推荐"无人时自动关灯"
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY + INTERVAL 2 HOUR)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY + INTERVAL 3 HOUR)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY + INTERVAL 1 HOUR)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(NULL, '手动触发', 'turn_off', '{}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY)),

-- 手动操作：频繁手动关灯（卧室） - 用于推荐"无人时自动关灯"
(NULL, '手动触发', 'turn_off', '{}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY + INTERVAL 1 HOUR)),
(NULL, '手动触发', 'turn_off', '{}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY + INTERVAL 1 HOUR)),
(NULL, '手动触发', 'turn_off', '{}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY + INTERVAL 2 HOUR)),
(NULL, '手动触发', 'turn_off', '{}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY + INTERVAL 1 HOUR)),
(NULL, '手动触发', 'turn_off', '{}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY + INTERVAL 2 HOUR)),
(NULL, '手动触发', 'turn_off', '{}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY + INTERVAL 1 HOUR)),

-- 手动操作：频繁手动开灯（客厅） - 用于推荐"有人时自动开灯"
(NULL, '手动触发', 'turn_on', '{"brightness": 80}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY + INTERVAL 12 HOUR)),
(NULL, '手动触发', 'turn_on', '{"brightness": 80}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY + INTERVAL 12 HOUR)),
(NULL, '手动触发', 'turn_on', '{"brightness": 80}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY + INTERVAL 12 HOUR)),
(NULL, '手动触发', 'turn_on', '{"brightness": 80}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY + INTERVAL 12 HOUR)),
(NULL, '手动触发', 'turn_on', '{"brightness": 80}', 'light_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY + INTERVAL 12 HOUR)),

-- 手动操作：频繁手动开空调 - 用于推荐"高温自动开空调"
(NULL, '手动触发', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY + INTERVAL 6 HOUR)),
(NULL, '手动触发', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY + INTERVAL 6 HOUR)),
(NULL, '手动触发', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY + INTERVAL 6 HOUR)),
(NULL, '手动触发', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY + INTERVAL 6 HOUR)),
(NULL, '手动触发', 'set_temperature', '{"temperature": 24}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 10 MINUTE)),

-- 手动操作：频繁手动关窗帘 - 用于推荐"夜间自动关窗帘"
(NULL, '手动触发', 'close', '{}', 'curtain_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY + INTERVAL 20 HOUR)),
(NULL, '手动触发', 'close', '{}', 'curtain_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY + INTERVAL 20 HOUR)),
(NULL, '手动触发', 'close', '{}', 'curtain_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY + INTERVAL 20 HOUR)),
(NULL, '手动触发', 'close', '{}', 'curtain_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY + INTERVAL 20 HOUR)),
(NULL, '手动触发', 'close', '{}', 'curtain_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY + INTERVAL 20 HOUR)),
(NULL, '手动触发', 'close', '{}', 'curtain_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY + INTERVAL 20 HOUR)),

-- 手动操作：联动操作（开灯和开空调高度相关）- 用于协同过滤推荐
(NULL, '手动触发', 'turn_on', '{"brightness": 100}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY + INTERVAL 8 HOUR)),
(NULL, '手动触发', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY + INTERVAL 8 HOUR + INTERVAL 5 MINUTE)),
(NULL, '手动触发', 'turn_on', '{"brightness": 100}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY + INTERVAL 8 HOUR)),
(NULL, '手动触发', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY + INTERVAL 8 HOUR + INTERVAL 5 MINUTE)),
(NULL, '手动触发', 'turn_on', '{"brightness": 100}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY + INTERVAL 8 HOUR)),
(NULL, '手动触发', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY + INTERVAL 8 HOUR + INTERVAL 5 MINUTE)),
(NULL, '手动触发', 'turn_on', '{"brightness": 100}', 'light_bedroom_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY + INTERVAL 8 HOUR)),
(NULL, '手动触发', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY + INTERVAL 8 HOUR + INTERVAL 5 MINUTE));

-- ============================================================
-- 4. 场景模板表 (rule_template)
-- 存储场景模板定义，包括内置模板和用户自定义模板
-- ============================================================
DROP TABLE IF EXISTS `rule_template`;
CREATE TABLE `rule_template` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `name` VARCHAR(200) NOT NULL COMMENT '模板名称',
  `description` VARCHAR(500) COMMENT '模板描述',
  `icon` VARCHAR(50) COMMENT '模板图标标识',
  `category` VARCHAR(50) NOT NULL COMMENT '模板分类: energy_saving/away/security/custom',
  `rule_json` MEDIUMTEXT COMMENT 'JSON格式规则定义（前端画布）',
  `rule_config` MEDIUMTEXT COMMENT '规则配置快照JSON（含条件、动作、窗口等完整配置）',
  `scope` VARCHAR(20) DEFAULT 'public' COMMENT '可见范围: public/team/private',
  `source_type` VARCHAR(20) DEFAULT 'system' COMMENT '来源类型: system(系统内置)/user(用户自建)',
  `source_rule_id` BIGINT COMMENT '来源规则ID（用户从已有规则保存为模板时记录）',
  `team_id` VARCHAR(100) COMMENT '团队ID',
  `author_id` VARCHAR(100) COMMENT '创建者ID',
  `author_name` VARCHAR(100) COMMENT '创建者名称',
  `version` VARCHAR(30) DEFAULT '1.0.0' COMMENT '版本标记',
  `review_status` TINYINT DEFAULT 1 COMMENT '审核状态: 0待审核 1已通过 2已拒绝',
  `reviewer_id` VARCHAR(100) COMMENT '审核人ID',
  `review_time` DATETIME COMMENT '审核时间',
  `review_remark` VARCHAR(500) COMMENT '审核备注',
  `apply_count` INT DEFAULT 0 COMMENT '应用次数',
  `status` TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_category` (`category`),
  INDEX `idx_scope` (`scope`),
  INDEX `idx_source_type` (`source_type`),
  INDEX `idx_review_status` (`review_status`),
  INDEX `idx_status` (`status`),
  INDEX `idx_team_id` (`team_id`),
  INDEX `idx_author_id` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景模板表';

-- 预置场景模板数据
INSERT INTO `rule_template` (`name`, `description`, `icon`, `category`, `rule_json`, `rule_config`, `scope`, `source_type`, `version`, `review_status`, `apply_count`, `status`) VALUES
('节能模式', '智能节能场景：温度超标自动空调制冷、无人时自动关灯关空调，实现自动节能', '🔋', 'energy_saving',
'{"nodes":[{"id":"start","type":"startNode","position":{"x":50,"y":150},"data":{"label":"触发开始"}},{"id":"cond_temp","type":"conditionNode","position":{"x":250,"y":80},"data":{"label":"温度>30°C","deviceId":"sensor_temp_001","field":"temperature","operator":">","value":30}},{"id":"cond_presence","type":"conditionNode","position":{"x":250,"y":220},"data":{"label":"无人","deviceId":"sensor_presence_001","field":"presence","operator":"==","value":false}},{"id":"logic_and","type":"logicNode","position":{"x":450,"y":150},"data":{"label":"AND","type":"AND"}},{"id":"action_aircon","type":"actionNode","position":{"x":650,"y":150},"data":{"label":"开空调26°C制冷","deviceId":"aircon_living_001","action":"turn_on","params":{"temperature":26,"mode":"cool"}}},{"id":"action_light_off","type":"actionNode","position":{"x":650,"y":300},"data":{"label":"关灯","deviceId":"light_living_001","action":"turn_off","params":{}}}],"edges":[{"id":"e1","source":"start","target":"logic_and","sourceHandle":"out"},{"id":"e2","source":"cond_temp","target":"logic_and","sourceHandle":"out","targetHandle":"in1"},{"id":"e3","source":"cond_presence","target":"logic_and","sourceHandle":"out","targetHandle":"in2"},{"id":"e4","source":"logic_and","target":"action_aircon","sourceHandle":"out"},{"id":"e5","source":"cond_presence","target":"action_light_off","sourceHandle":"out"}]}',
'{"conditions":[{"deviceId":"sensor_temp_001","field":"temperature","operator":">","value":30,"label":"温度>30°C"},{"deviceId":"sensor_presence_001","field":"presence","operator":"==","value":false,"label":"无人"}],"actions":[{"deviceId":"aircon_living_001","action":"turn_on","params":{"temperature":26,"mode":"cool"},"label":"开空调26°C制冷"},{"deviceId":"light_living_001","action":"turn_off","params":{},"label":"关灯"}],"logic":"AND"}',
'public', 'system', '1.0.0', 1, 128, 1),

('离家模式', '离家场景：检测到无人后自动关闭所有灯光、空调，关闭窗帘，保障节能与安全', '🏠', 'away',
'{"nodes":[{"id":"start","type":"startNode","position":{"x":50,"y":150},"data":{"label":"触发开始"}},{"id":"cond_no_presence","type":"conditionNode","position":{"x":250,"y":150},"data":{"label":"无人","deviceId":"sensor_presence_001","field":"presence","operator":"==","value":false}},{"id":"action_light_off","type":"actionNode","position":{"x":500,"y":50},"data":{"label":"关灯","deviceId":"light_living_001","action":"turn_off","params":{}}},{"id":"action_aircon_off","type":"actionNode","position":{"x":500,"y":150},"data":{"label":"关空调","deviceId":"aircon_living_001","action":"turn_off","params":{}}},{"id":"action_curtain_close","type":"actionNode","position":{"x":500,"y":250},"data":{"label":"关窗帘","deviceId":"curtain_living_001","action":"close","params":{}}}],"edges":[{"id":"e1","source":"start","target":"cond_no_presence","sourceHandle":"out"},{"id":"e2","source":"cond_no_presence","target":"action_light_off","sourceHandle":"out"},{"id":"e3","source":"cond_no_presence","target":"action_aircon_off","sourceHandle":"out"},{"id":"e4","source":"cond_no_presence","target":"action_curtain_close","sourceHandle":"out"}]}',
'{"conditions":[{"deviceId":"sensor_presence_001","field":"presence","operator":"==","value":false,"label":"无人"}],"actions":[{"deviceId":"light_living_001","action":"turn_off","params":{},"label":"关灯"},{"deviceId":"aircon_living_001","action":"turn_off","params":{},"label":"关空调"},{"deviceId":"curtain_living_001","action":"close","params":{},"label":"关窗帘"}],"logic":"AND"}',
'public', 'system', '1.0.0', 1, 86, 1),

('安防模式', '安防场景：检测到异常人员活动时自动开灯、发送告警，同时关闭窗帘保护隐私', '🛡️', 'security',
'{"nodes":[{"id":"start","type":"startNode","position":{"x":50,"y":150},"data":{"label":"触发开始"}},{"id":"cond_presence","type":"conditionNode","position":{"x":250,"y":150},"data":{"label":"有人活动","deviceId":"sensor_presence_001","field":"presence","operator":"==","value":true}},{"id":"cond_night","type":"conditionNode","position":{"x":250,"y":280},"data":{"label":"夜间时段","deviceId":"sensor_temp_001","field":"time","operator":">=","value":"22:00"}},{"id":"logic_and","type":"logicNode","position":{"x":450,"y":200},"data":{"label":"AND","type":"AND"}},{"id":"action_light_on","type":"actionNode","position":{"x":650,"y":120},"data":{"label":"开灯","deviceId":"light_living_001","action":"turn_on","params":{"brightness":100}}},{"id":"action_curtain_close","type":"actionNode","position":{"x":650,"y":250},"data":{"label":"关窗帘","deviceId":"curtain_living_001","action":"close","params":{}}}],"edges":[{"id":"e1","source":"start","target":"logic_and","sourceHandle":"out"},{"id":"e2","source":"cond_presence","target":"logic_and","sourceHandle":"out","targetHandle":"in1"},{"id":"e3","source":"cond_night","target":"logic_and","sourceHandle":"out","targetHandle":"in2"},{"id":"e4","source":"logic_and","target":"action_light_on","sourceHandle":"out"},{"id":"e5","source":"logic_and","target":"action_curtain_close","sourceHandle":"out"}]}',
'{"conditions":[{"deviceId":"sensor_presence_001","field":"presence","operator":"==","value":true,"label":"有人活动"},{"deviceId":"sensor_temp_001","field":"time","operator":">=","value":"22:00","label":"夜间时段"}],"actions":[{"deviceId":"light_living_001","action":"turn_on","params":{"brightness":100},"label":"开灯"},{"deviceId":"curtain_living_001","action":"close","params":{},"label":"关窗帘"}],"logic":"AND"}',
'public', 'system', '1.0.0', 1, 65, 1);

-- ============================================================
-- 5. 告警记录表 (alert_record)
-- 汇总所有规则触发的告警消息，支持按规则、设备、时间筛选
-- ============================================================
DROP TABLE IF EXISTS `alert_record`;
CREATE TABLE `alert_record` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `rule_id` BIGINT COMMENT '触发的规则ID',
  `rule_name` VARCHAR(200) COMMENT '规则名称(冗余)',
  `device_id` VARCHAR(100) COMMENT '关联设备ID',
  `level` VARCHAR(20) NOT NULL DEFAULT 'info' COMMENT '告警级别: info/warning/critical',
  `message` VARCHAR(500) NOT NULL COMMENT '告警消息内容',
  `detail` MEDIUMTEXT COMMENT '告警详情JSON',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '告警状态: pending/acknowledged/cleared',
  `acknowledged_by` VARCHAR(100) COMMENT '确认人',
  `acknowledged_time` DATETIME COMMENT '确认时间',
  `cleared_by` VARCHAR(100) COMMENT '清除人',
  `cleared_time` DATETIME COMMENT '清除时间',
  `notify_channels` VARCHAR(200) COMMENT '已发送的通知渠道(逗号分隔): dingtalk,wecom,email',
  `notify_status` TINYINT DEFAULT 0 COMMENT '通知发送状态: 0未发送 1部分成功 2全部成功 3全部失败',
  `tenant_id` BIGINT COMMENT '租户ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_rule_id` (`rule_id`),
  INDEX `idx_device_id` (`device_id`),
  INDEX `idx_level` (`level`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';

-- 向后兼容：老库升级ALTER语句
-- ALTER TABLE alert_record MODIFY COLUMN `level` VARCHAR(20) NOT NULL DEFAULT 'info' COMMENT '告警级别: info/warning/critical';
-- ALTER TABLE alert_record ADD INDEX `idx_rule_id` (`rule_id`);
-- ALTER TABLE alert_record ADD INDEX `idx_device_id` (`device_id`);
-- ALTER TABLE alert_record ADD INDEX `idx_status` (`status`);

-- ============================================================
-- 6. 告警通知配置表 (alert_notify_config)
-- 配置告警通知渠道：钉钉、企业微信、邮件，支持按级别过滤
-- ============================================================
DROP TABLE IF EXISTS `alert_notify_config`;
CREATE TABLE `alert_notify_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `name` VARCHAR(200) NOT NULL COMMENT '配置名称',
  `channel` VARCHAR(30) NOT NULL COMMENT '通知渠道: dingtalk/wecom/email',
  `config` MEDIUMTEXT NOT NULL COMMENT '渠道配置JSON(如webhook地址、邮件服务器等)',
  `enabled_levels` VARCHAR(100) DEFAULT 'warning,critical' COMMENT '启用通知的告警级别(逗号分隔)',
  `enabled` TINYINT DEFAULT 1 COMMENT '是否启用: 1启用 0禁用',
  `tenant_id` BIGINT COMMENT '租户ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_channel` (`channel`),
  INDEX `idx_enabled` (`enabled`),
  INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警通知配置表';

-- 告警通知配置默认数据
INSERT INTO `alert_notify_config` (`name`, `channel`, `config`, `enabled_levels`, `enabled`) VALUES
('钉钉告警通知', 'dingtalk', '{"webhookUrl":"","secret":"","messageType":"markdown"}', 'warning,critical', 1),
('企业微信告警通知', 'wecom', '{"webhookUrl":""}', 'warning,critical', 1),
('邮件告警通知', 'email', '{"host":"","port":465,"username":"","password":"","from":"","to":""}', 'critical', 0);

-- 告警记录示例数据（便于前端展示、调试）
INSERT INTO `alert_record` (`rule_id`, `rule_name`, `device_id`, `level`, `message`, `detail`, `status`, `notify_channels`, `notify_status`, `create_time`) VALUES
(1, '高温无人自动开空调', 'sensor_temp_001', 'warning', '客厅温度超过30°C', '{"temperature":32.5,"threshold":30,"room":"客厅"}', 'pending', 'dingtalk', 1, DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
(1, '高温无人自动开空调', 'sensor_temp_001', 'critical', '客厅温度异常升高至35°C', '{"temperature":35.2,"threshold":30,"room":"客厅"}', 'pending', 'dingtalk,email', 1, DATE_SUB(NOW(), INTERVAL 15 MINUTE)),
(NULL, NULL, 'aircon_living_001', 'info', '客厅空调设备响应超时', '{"action":"turn_on","retryCount":2}', 'acknowledged', 'dingtalk', 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(NULL, NULL, 'sensor_presence_001', 'warning', '客厅人体传感器长时间无数据上报', '{"lastReport":"2小时前"}', 'pending', 'dingtalk', 1, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(1, '高温无人自动开空调', 'sensor_temp_002', 'info', '卧室温度达到警戒线29°C', '{"temperature":29.1,"threshold":30,"room":"卧室"}', 'cleared', 'dingtalk', 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(NULL, NULL, 'light_living_001', 'info', '客厅主灯设备离线', '{"online":false,"lastOnlineTime":"1小时前"}', 'pending', 'dingtalk', 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(1, '高温无人自动开空调', 'sensor_temp_001', 'critical', '客厅温度持续升高至38°C', '{"temperature":38.1,"threshold":30,"room":"客厅"}', 'pending', 'dingtalk,wecom,email', 2, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(NULL, NULL, 'sensor_humidity_001', 'warning', '湿度传感器数据异常', '{"humidity":99,"normalRange":[30,80]}', 'acknowledged', 'dingtalk', 1, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(NULL, NULL, 'curtain_living_001', 'info', '客厅窗帘设备响应超时', '{"action":"close","retryCount":1}', 'cleared', '', 0, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
(NULL, NULL, 'sensor_temp_001', 'warning', '客厅温度传感器数据波动异常', '{"readings":[28,35,27,36],"avgDelta":4.25}', 'pending', 'dingtalk', 1, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ============================================================
-- MQTT 主题约定说明
-- ============================================================
--
-- 1. 设备上报遥测数据 (Device -> Platform)
--    主题: iot/device/{deviceId}/telemetry
--    QoS: 1
--    Payload 示例:
--    {
--      "ts": 1718000000000,
--      "temperature": 25.5,
--      "humidity": 60,
--      "voltage": 220.5
--    }
--
-- 2. 设备上报状态变化 (Device -> Platform)
--    主题: iot/device/{deviceId}/status
--    QoS: 1
--    Payload 示例:
--    {
--      "ts": 1718000000000,
--      "online": true,
--      "power": "on",
--      "temperature": 26
--    }
--
-- 3. 设备上线/离线遗嘱 (Device -> Broker -> Platform)
--    主题: iot/device/{deviceId}/lifecycle
--    QoS: 1
--    Payload 示例 (上线):
--    {
--      "ts": 1718000000000,
--      "event": "connected",
--      "ip": "192.168.1.100"
--    }
--    Payload 示例 (离线遗嘱):
--    {
--      "ts": 1718000000000,
--      "event": "disconnected",
--      "reason": "keepalive_timeout"
--    }
--
-- 4. 平台下发设备指令 (Platform -> Device)
--    主题: iot/device/{deviceId}/command
--    QoS: 1
--    Payload 示例:
--    {
--      "cmdId": "cmd_20240611_001",
--      "action": "turn_on",
--      "params": {
--        "temperature": 26,
--        "mode": "cool"
--      },
--      "ts": 1718000000000
--    }
--
-- 5. 设备指令执行结果回执 (Device -> Platform)
--    主题: iot/device/{deviceId}/command/ack
--    QoS: 1
--    Payload 示例 (成功):
--    {
--      "cmdId": "cmd_20240611_001",
--      "result": "success",
--      "ts": 1718000000000
--    }
--    Payload 示例 (失败):
--    {
--      "cmdId": "cmd_20240611_001",
--      "result": "failed",
--      "errorCode": "DEVICE_BUSY",
--      "errorMsg": "设备正忙，请稍后重试",
--      "ts": 1718000000000
--    }
--
-- ============================================================
