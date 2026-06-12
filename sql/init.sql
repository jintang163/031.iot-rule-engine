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

-- 设备示例数据（3条）
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
  1, NOW(), '客厅-墙面');

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

-- 动作执行日志示例数据（几条）
INSERT INTO `action_log` (`rule_id`, `rule_name`, `action_type`, `action_params`, `device_id`, `result`, `retry_count`, `error_msg`, `execute_time`) VALUES
(1, '高温无人自动开空调', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(1, '高温无人自动开空调', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(1, '高温无人自动开空调', 'turn_on', '{"temperature": 26, "mode": "cool"}', 'aircon_living_001', 0, 2, '设备响应超时，MQTT未收到ACK', DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(NULL, '手动触发', 'set_temperature', '{"temperature": 24}', 'aircon_living_001', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 10 MINUTE));

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
