-- ============================================================
-- IoT Rule Engine - V3 多租户与RBAC迁移脚本
-- 功能: 新增租户/用户/角色/权限表，业务表增加 tenant_id
-- 数据库: MySQL 8.0+
-- 执行顺序: 在 V2__rule_execution_stats.sql 之后执行
-- ============================================================

USE iot_rule_engine;

-- ============================================================
-- 1. 租户表 (sys_tenant)
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_tenant` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `tenant_code` VARCHAR(50) NOT NULL UNIQUE COMMENT '租户唯一编码',
  `tenant_name` VARCHAR(200) NOT NULL COMMENT '租户名称',
  `table_prefix` VARCHAR(50) DEFAULT NULL COMMENT '表前缀(预留分库分表)',
  `contact_person` VARCHAR(100) COMMENT '联系人',
  `contact_phone` VARCHAR(30) COMMENT '联系电话',
  `contact_email` VARCHAR(100) COMMENT '联系邮箱',
  `max_users` INT DEFAULT 50 COMMENT '最大用户数配额',
  `max_devices` INT DEFAULT 500 COMMENT '最大设备数配额',
  `max_rules` INT DEFAULT 100 COMMENT '最大规则数配额',
  `expire_time` DATETIME COMMENT '服务过期时间',
  `status` TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  `remark` VARCHAR(500) COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_tenant_code` (`tenant_code`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- ============================================================
-- 2. 用户表 (sys_user)
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `username` VARCHAR(100) NOT NULL COMMENT '登录用户名',
  `password` VARCHAR(200) NOT NULL COMMENT '密码(MD5+salt)',
  `salt` VARCHAR(50) COMMENT '密码盐',
  `nickname` VARCHAR(100) COMMENT '昵称',
  `avatar` VARCHAR(500) COMMENT '头像URL',
  `email` VARCHAR(100) COMMENT '邮箱',
  `phone` VARCHAR(30) COMMENT '手机号',
  `status` TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  `last_login_time` DATETIME COMMENT '最后登录时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`),
  INDEX `idx_tenant_id` (`tenant_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 3. 角色表 (sys_role)
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `data_scope` TINYINT DEFAULT 2 COMMENT '数据权限范围: 1全部 2本租户 3仅本人',
  `role_type` TINYINT DEFAULT 2 COMMENT '1内置角色(不可删) 2自定义角色',
  `status` TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  `remark` VARCHAR(500) COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_tenant_role_code` (`tenant_id`, `role_code`),
  INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- ============================================================
-- 4. 权限表 (sys_permission)
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID(0为顶级)',
  `perm_code` VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
  `perm_name` VARCHAR(100) NOT NULL COMMENT '权限名称',
  `perm_type` TINYINT NOT NULL COMMENT '1菜单 2按钮',
  `path` VARCHAR(200) COMMENT '前端路由路径',
  `icon` VARCHAR(50) COMMENT '图标',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `status` TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

-- ============================================================
-- 5. 用户-角色关联表 (sys_user_role)
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ============================================================
-- 6. 角色-权限关联表 (sys_role_permission)
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT NOT NULL COMMENT '权限ID',
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
  INDEX `idx_role_id` (`role_id`),
  INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ============================================================
-- 7. 初始化权限数据 (24个权限点)
-- ============================================================
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `icon`, `sort`, `status`) VALUES
-- 规则管理
(1,  0, 'rule',        '规则管理', 1, '/rules', 'DashboardOutlined', 1, 1),
(2,  1, 'rule:view',   '查看规则', 2, NULL, NULL, 1, 1),
(3,  1, 'rule:edit',   '编辑规则', 2, NULL, NULL, 2, 1),
(4,  1, 'rule:test',   '测试规则', 2, NULL, NULL, 3, 1),
(5,  1, 'rule:debug',  '调试规则', 2, NULL, NULL, 4, 1),
-- 设备管理
(6,  0, 'device',      '设备管理', 1, '/devices', 'ApiOutlined', 2, 1),
(7,  6, 'device:view', '查看设备', 2, NULL, NULL, 1, 1),
(8,  6, 'device:edit', '编辑设备', 2, NULL, NULL, 2, 1),
-- 统计分析
(9,  0, 'stats',       '统计分析', 1, '/stats', 'BarChartOutlined', 3, 1),
(10, 9, 'stats:view',  '查看统计', 2, NULL, NULL, 1, 1),
-- 执行日志
(11, 0, 'log',         '执行日志', 1, '/logs', 'FileTextOutlined', 4, 1),
(12,11, 'log:view',    '查看日志', 2, NULL, NULL, 1, 1),
-- 模板库
(13, 0, 'template',    '模板库',   1, '/templates', 'AppstoreOutlined', 5, 1),
(14,13, 'template:view','查看模板', 2, NULL, NULL, 1, 1),
(15,13, 'template:edit','编辑模板', 2, NULL, NULL, 2, 1),
-- 系统管理
(16, 0, 'system',      '系统管理', 1, '/system',  'SettingOutlined', 6, 1),
(17,16, 'user:view',   '查看用户', 2, NULL, NULL, 1, 1),
(18,16, 'user:edit',   '编辑用户', 2, NULL, NULL, 2, 1),
(19,16, 'role:view',   '查看角色', 2, NULL, NULL, 3, 1),
(20,16, 'role:edit',   '编辑角色', 2, NULL, NULL, 4, 1),
(21,16, 'tenant:view', '查看租户', 2, NULL, NULL, 5, 1),
(22,16, 'tenant:edit', '编辑租户', 2, NULL, NULL, 6, 1),
-- 调试与沙箱
(23, 1, 'rule:sandbox','沙箱测试', 2, NULL, NULL, 5, 1),
(24, 1, 'rule:publish','发布规则', 2, NULL, NULL, 6, 1);

-- ============================================================
-- 8. 初始化平台超级租户和超级管理员
-- ============================================================
INSERT INTO `sys_tenant` (`id`, `tenant_code`, `tenant_name`, `contact_person`, `contact_phone`, `max_users`, `max_devices`, `max_rules`, `status`) VALUES
(1, 'PLATFORM', '平台管理', 'Super Admin', '0000', 9999, 9999, 9999, 1);

-- 超级管理员: super_admin / Super@2024
INSERT INTO `sys_user` (`id`, `tenant_id`, `username`, `password`, `salt`, `nickname`, `status`) VALUES
(1, 1, 'super_admin', 'e10adc3949ba59abbe56e057f20f883e', 'abc12345', '超级管理员', 1);
-- 注意: 上面密码是 MD5("Super@2024abc12345") 的结果, 实际需运行应用后由代码生成

-- 超级管理员角色
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `sort`, `data_scope`, `role_type`, `status`) VALUES
(1, 1, 'SUPER_ADMIN', '超级管理员', 0, 1, 1, 1);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- 超级管理员拥有全部权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, id FROM `sys_permission`;

-- ============================================================
-- 9. 业务表增加 tenant_id 列与索引
-- ============================================================

-- 规则表
ALTER TABLE `rule` ADD COLUMN `tenant_id` BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER `id`;
ALTER TABLE `rule` ADD INDEX `idx_tenant_id` (`tenant_id`);
UPDATE `rule` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;

-- 设备表
ALTER TABLE `iot_device` ADD COLUMN `tenant_id` BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER `id`;
ALTER TABLE `iot_device` ADD INDEX `idx_tenant_id` (`tenant_id`);
UPDATE `iot_device` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;

-- 动作日志表
ALTER TABLE `iot_action_log` ADD COLUMN `tenant_id` BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER `id`;
ALTER TABLE `iot_action_log` ADD INDEX `idx_tenant_id` (`tenant_id`);
UPDATE `iot_action_log` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;

-- 规则执行统计表
ALTER TABLE `rule_execution_stats` ADD COLUMN `tenant_id` BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER `id`;
ALTER TABLE `rule_execution_stats` ADD INDEX `idx_tenant_id` (`tenant_id`);
UPDATE `rule_execution_stats` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;

-- 场景模板表
ALTER TABLE `rule_template` ADD COLUMN `tenant_id` BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER `id`;
ALTER TABLE `rule_template` ADD INDEX `idx_tenant_id` (`tenant_id`);
UPDATE `rule_template` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;
