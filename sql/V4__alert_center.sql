USE iot_rule_engine;

CREATE TABLE IF NOT EXISTS `alert_record` (
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

CREATE TABLE IF NOT EXISTS `alert_notify_config` (
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

INSERT INTO `alert_notify_config` (`name`, `channel`, `config`, `enabled_levels`, `enabled`) VALUES
('钉钉告警通知', 'dingtalk', '{"webhookUrl":"","secret":"","messageType":"markdown"}', 'warning,critical', 1),
('企业微信告警通知', 'wecom', '{"webhookUrl":""}', 'warning,critical', 1),
('邮件告警通知', 'email', '{"host":"","port":465,"username":"","password":"","from":"","to":""}', 'critical', 0);

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
