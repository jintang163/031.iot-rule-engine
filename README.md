<div align="center">

# 🏠 物联网规则引擎（IoT Rule Engine）

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7+-green.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18+-blue.svg)](https://react.dev/)
[![Drools](https://img.shields.io/badge/Drools-7.74-red.svg)](https://www.drools.org/)
[![EMQX](https://img.shields.io/badge/EMQX-5.0+-orange.svg)](https://www.emqx.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](#license)

**基于 React Flow 可视化编排 + Spring Boot + Drools 的物联网设备联动规则引擎系统**

[快速开始](#快速开始) · [功能特性](#功能特性) · [技术栈](#技术栈) · [API文档](#api接口列表) · [MQTT约定](#mqtt主题约定)

</div>

---

## 📋 项目简介

IoT Rule Engine 是一套面向智能家居、工业物联网场景的设备联动自动化平台。用户通过**拖拽式可视化画布**（React Flow）编排触发条件、逻辑门和执行动作，系统自动将编排结果编译为 Drools DRL 规则，配合实时 MQTT 数据流实现毫秒级的设备联动响应。

### 🏗️ 架构图

```
┌───────────────────────────────────────────────────────────────────┐
│                         前端 (Frontend)                           │
│   React 18 + React Flow + Ant Design + WebSocket Client          │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │  规则画布编排 │  │  设备管理面板 │  │  实时日志/监控看板      │  │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬────────────┘  │
└─────────┼─────────────────┼───────────────────────┼───────────────┘
          │ REST API        │ REST API              │ WebSocket
          ▼                 ▼                       ▼
┌───────────────────────────────────────────────────────────────────┐
│                       后端 (Spring Boot)                         │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    Controller Layer                         │ │
│  │    RuleController / DeviceController / ActionLogController  │ │
│  │               WebSocketHandler (实时推送)                    │ │
│  └─────────────────────────────┬───────────────────────────────┘ │
│  ┌─────────────────────────────┼───────────────────────────────┐ │
│  │                  Service & Business Layer                   │ │
│  │  RuleService  │  DeviceService  │  ActionExecutorService    │ │
│  │  DroolsRuleCompiler  │  RuleTestSimulator                   │ │
│  └──────────┬──────────────────┬───────────────────┬───────────┘ │
│             │                  │                   │             │
│  ┌──────────▼────────┐ ┌───────▼────────┐ ┌───────▼───────────┐ │
│  │  Drools 规则引擎   │ │  MQTT Client   │ │  Redis 缓存层     │ │
│  │  (KieSession)     │ │  (Eclipse Paho)│ │  (规则/状态缓存)   │ │
│  └──────────┬────────┘ └───────┬────────┘ └───────┬───────────┘ │
└─────────────┼──────────────────┼───────────────────┼─────────────┘
              │                  │                   │
     ┌────────▼───────┐  ┌──────▼───────┐   ┌───────▼───────┐
     │   MySQL 8.0    │  │   EMQX 5.0   │   │   Redis 7.0   │
     │  (数据持久化)   │  │  (MQTT Broker)│   │   (数据缓存)   │
     └────────────────┘  └──────┬───────┘   └───────────────┘
                                │  MQTT Pub/Sub
                       ┌────────▼────────┐
                       │  IoT Devices    │
                       │ 传感器/执行器   │
                       └─────────────────┘
```

---

## ✨ 功能特性

| 模块 | 功能说明 |
|:-----|:---------|
| 🎨 **可视化规则编排** | 基于 React Flow 的拖拽画布，支持节点连线、条件配置、一键保存 |
| 🔀 **多条件逻辑门** | 支持 AND / OR / NOT / XOR 逻辑组合，任意层级嵌套 |
| ⚙️ **动态 DRL 编译** | 画布定义自动编译为 Drools DRL，热加载无需重启服务 |
| 📡 **实时 MQTT 数据流** | 订阅设备遥测主题，毫秒级规则匹配触发 |
| 📱 **设备在线状态管理** | LWT 遗嘱感知设备上下线，自动更新在线状态 |
| 🔄 **动作失败重试** | 指数退避重试策略，默认最多3次，可配置 |
| 🔌 **WebSocket 推送** | 规则触发、日志、设备状态变化实时推送到前端 |
| 🧪 **规则测试模拟** | 内置模拟发送遥测数据，离线验证规则逻辑正确性 |
| 📊 **执行日志审计** | 完整的动作执行日志，支持按规则/设备/时间检索 |
| 🔒 **规则互斥分组** | 同互斥组内只有高优先级规则可触发，避免冲突 |

---

## 🛠️ 技术栈

### 前端（Frontend）

| 技术 | 版本 | 用途 |
|:-----|:-----|:-----|
| [React](https://react.dev/) | 18+ | 核心 UI 框架 |
| [React Flow](https://reactflow.dev/) | 11+ | 可视化规则画布 |
| [Ant Design](https://ant.design/) | 5.x | UI 组件库 |
| [TypeScript](https://www.typescriptlang.org/) | 5.x | 类型安全 |
| [Vite](https://vitejs.dev/) | 5.x | 构建工具 |
| [Axios](https://axios-http.com/) | 1.x | HTTP 客户端 |
| [React Query](https://tanstack.com/query) | 5.x | 数据状态管理 |
| [Zustand](https://zustand-demo.pmnd.rs/) | 4.x | 全局状态管理 |
| [@stomp/stompjs](https://stomp-js.github.io/) | 7.x | WebSocket 客户端 |

### 后端（Backend）

| 技术 | 版本 | 用途 |
|:-----|:-----|:-----|
| [Spring Boot](https://spring.io/projects/spring-boot) | 2.7+ | 核心服务框架 |
| [Drools](https://www.drools.org/) | 7.74.Final | 规则引擎 |
| [MyBatis-Plus](https://baomidou.com/) | 3.5.x | ORM 持久层 |
| [MySQL](https://www.mysql.com/) | 8.0+ | 关系数据库 |
| [Redis](https://redis.io/) | 7.0+ | 缓存 / 规则会话存储 |
| [Spring Integration MQTT](https://spring.io/projects/spring-integration) | 5.5.x | MQTT 客户端 |
| [Spring WebSocket](https://spring.io/guides/gs/messaging-stomp-websocket/) | - | STOMP WebSocket 服务 |
| [Lombok](https://projectlombok.org/) | 1.18.x | 代码简化 |
| [Hutool](https://hutool.cn/) | 5.8.x | 工具类库 |
| [Jackson](https://github.com/FasterXML/jackson) | - | JSON 序列化 |

---

## 🚀 快速开始

### 1️⃣ 环境要求

| 依赖 | 最低版本 | 说明 |
|:-----|:---------|:-----|
| **JDK** | 11+ | 推荐 JDK 17 LTS |
| **Node.js** | 18+ | 推荐 Node.js 20 LTS |
| **MySQL** | 8.0+ | 需支持 JSON 类型 |
| **Redis** | 6.0+ | 用于规则会话缓存 |
| **EMQX** | 5.0+ | MQTT Broker（或兼容 Mosquitto） |
| **Maven** | 3.6+ | 后端构建工具 |
| **npm / pnpm** | 8+ | 前端包管理器 |

### 2️⃣ 启动基础服务（使用 Docker）

```bash
# 在项目根目录执行
docker-compose up -d
```

等待服务启动完成：
- MySQL：`localhost:3306`  `root / iot_rule_engine_2024`
- Redis：`localhost:6379`  无密码
- EMQX 控制台：`http://localhost:18083`  `admin / public`

### 3️⃣ 初始化数据库

```bash
# 方式一：命令行导入
mysql -h localhost -u root -p < sql/init.sql

# 方式二：使用 MySQL 客户端执行 sql/init.sql 文件内容
```

### 4️⃣ 配置后端

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  # 数据库配置
  datasource:
    url: jdbc:mysql://localhost:3306/iot_rule_engine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: iot_rule_engine_2024

  # Redis 配置
  data:
    redis:
      host: localhost
      port: 6379
      password:

# MQTT 配置
mqtt:
  broker-url: tcp://localhost:1883
  username: admin
  password: public
  client-id: rule-engine-server
```

### 5️⃣ 启动后端

```bash
cd backend

# 方式一：Maven 启动
mvn spring-boot:run

# 方式二：IDEA 直接运行主类
# 入口类：com.iot.rule.engine.IotRuleEngineApplication
```

后端启动成功：`http://localhost:8080`

### 6️⃣ 启动前端

```bash
cd frontend

# 安装依赖
npm install
# 或使用 pnpm install（推荐）

# 启动开发服务器
npm run dev
```

前端启动成功：`http://localhost:3000`

### 7️⃣ 验证运行

打开浏览器访问 **http://localhost:3000**，即可看到系统首页。系统已内置3台示例设备和1条示例规则，可直接在 **规则测试** 页面模拟发送遥测数据验证规则触发。

---

## 📖 API 接口列表

### 规则管理（Rule）

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| `GET` | `/api/rules` | 分页查询规则列表（支持按名称/状态筛选） |
| `GET` | `/api/rules/{id}` | 根据ID获取规则详情（含 rule_json 和 drl_content） |
| `POST` | `/api/rules` | 新建规则（自动编译DRL，加载到引擎） |
| `PUT` | `/api/rules/{id}` | 更新规则（自动重新编译DRL，热加载） |
| `DELETE` | `/api/rules/{id}` | 删除规则（同步从引擎卸载） |
| `PUT` | `/api/rules/{id}/status` | 启用/禁用规则 |
| `POST` | `/api/rules/compile` | 仅编译规则（返回DRL，不保存） |
| `POST` | `/api/rules/{id}/test` | 模拟执行规则测试 |

### 设备管理（Device）

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| `GET` | `/api/devices` | 分页查询设备列表（按类型/在线状态筛选） |
| `GET` | `/api/devices/{id}` | 获取设备详情（含最新状态） |
| `GET` | `/api/devices/deviceId/{deviceId}` | 根据 MQTT ClientID 查询设备 |
| `POST` | `/api/devices` | 注册新设备 |
| `PUT` | `/api/devices/{id}` | 更新设备信息 |
| `DELETE` | `/api/devices/{id}` | 删除设备 |
| `POST` | `/api/devices/{id}/command` | 手动下发设备指令 |

### 动作执行日志（ActionLog）

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| `GET` | `/api/action-logs` | 分页查询执行日志（按规则/设备/结果/时间筛选） |
| `GET` | `/api/action-logs/{id}` | 获取单条日志详情 |
| `POST` | `/api/action-logs/{id}/retry` | 重试失败的动作 |
| `GET` | `/api/action-logs/statistics` | 执行统计（近7天成功/失败趋势） |

### 系统接口

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| `GET` | `/api/system/info` | 系统概览信息（设备数/规则数/在线数等） |
| `GET` | `/api/system/health` | 健康检查（返回200表示正常） |
| `POST` | `/api/system/reload-rules` | 强制重新加载全部规则到引擎 |

---

## 🔌 MQTT 主题约定

### 主题速查表

| 方向 | 主题模板 | QoS | 说明 |
|:-----|:---------|:----|:-----|
| Device → Platform | `iot/device/{deviceId}/telemetry` | 1 | 设备上报遥测数据（温湿度、传感器值等） |
| Device → Platform | `iot/device/{deviceId}/status` | 1 | 设备上报状态变化（开关、模式等） |
| Device → Platform | `iot/device/{deviceId}/lifecycle` | 1 | 设备上下线生命周期事件（连接/遗嘱） |
| Platform → Device | `iot/device/{deviceId}/command` | 1 | 平台下发设备控制指令 |
| Device → Platform | `iot/device/{deviceId}/command/ack` | 1 | 设备回执指令执行结果 |

### 1. 设备上报遥测数据

**主题**：`iot/device/{deviceId}/telemetry`

```json
{
  "ts": 1718000000000,
  "temperature": 25.5,
  "humidity": 60,
  "voltage": 220.5
}
```

### 2. 设备上报状态变化

**主题**：`iot/device/{deviceId}/status`

```json
{
  "ts": 1718000000000,
  "online": true,
  "power": "on",
  "temperature": 26,
  "mode": "cool"
}
```

### 3. 平台下发设备指令

**主题**：`iot/device/{deviceId}/command`

```json
{
  "cmdId": "cmd_20240611_001",
  "action": "turn_on",
  "params": {
    "temperature": 26,
    "mode": "cool"
  },
  "ts": 1718000000000
}
```

### 4. 设备指令执行回执

**主题**：`iot/device/{deviceId}/command/ack`

```json
{
  "cmdId": "cmd_20240611_001",
  "result": "success",
  "ts": 1718000000100
}
```

> **提示**：详细的 MQTT 主题定义和 Payload 规范见 `sql/init.sql` 文件末尾的注释说明。

---

## 🎮 使用示例：新建"温度>30且无人开空调"规则

### 步骤概览

```
① 进入规则管理 → ② 点击"新建规则" → ③ 拖拽节点编排 → ④ 配置参数 → ⑤ 保存并启用
```

### 详细步骤

#### Step 1：打开规则画布

1. 左侧菜单点击 **【规则管理】**
2. 点击右上角 **【+ 新建规则】** 按钮
3. 填写基础信息：
   - **规则名称**：`高温无人自动开空调`
   - **规则描述**：`当客厅温度超过30度且无人时，自动开启空调制冷`
   - **优先级**：`10`（最高）
   - **互斥组**：`climate_control`（空调类规则互斥，避免反复开关）

#### Step 2：添加触发条件节点

从左侧节点面板拖拽：

| 节点类型 | 配置内容 |
|:---------|:---------|
| **条件节点** | 设备：`客厅温度传感器`，字段：`temperature`，运算符：`>`，值：`30` |
| **条件节点** | 设备：`客厅人体传感器`，字段：`presence`，运算符：`==`，值：`false` |
| **逻辑门节点** | 类型：`AND`（两个条件同时满足） |

连线：两个条件节点分别连接到 AND 门的 `in1` 和 `in2` 输入口。

#### Step 3：添加执行动作节点

| 节点类型 | 配置内容 |
|:---------|:---------|
| **动作节点** | 设备：`客厅空调`，动作：`turn_on`，参数：`temperature=26, mode=cool` |

连线：AND 门的 `out` 输出口连接到动作节点的输入口。

#### Step 4：保存并启用

1. 点击画布顶部 **【编译预览】**，确认生成的 DRL 语法正确
2. 点击 **【保存】**，系统自动编译并加载到 Drools 引擎
3. 返回规则列表，确认规则状态为 **🟢 已启用**

#### Step 5：验证规则（可选）

1. 进入 **【规则测试】** 页面
2. 选择设备：`客厅温度传感器`，发送遥测数据：`{"temperature": 32}`
3. 选择设备：`客厅人体传感器`，发送遥测数据：`{"presence": false}`
4. 查看 **实时日志面板**，应看到规则被触发，空调指令已下发
5. 进入 **【动作日志】** 页面，确认有新的执行记录

---

## 📁 目录结构说明

```
iot-rule-engine/
├── backend/                          # 后端 Spring Boot 项目
│   ├── src/
│   │   └── main/
│   │       ├── java/com/iot/rule/engine/
│   │       │   ├── controller/       # REST API 控制层
│   │       │   ├── service/          # 业务服务层
│   │       │   │   ├── impl/         # 服务实现
│   │       │   │   └── drools/       # Drools 引擎相关服务
│   │       │   ├── mapper/           # MyBatis Mapper
│   │       │   ├── entity/           # 数据库实体
│   │       │   ├── dto/              # 请求/响应 DTO
│   │       │   ├── domain/           # Drools Fact 领域对象
│   │       │   ├── config/           # 配置类（Drools/Redis/MQTT/WebSocket）
│   │       │   ├── mqtt/             # MQTT 消息处理器
│   │       │   ├── websocket/        # WebSocket 推送服务
│   │       │   ├── compiler/         # 规则 JSON → DRL 编译器
│   │       │   └── IotRuleEngineApplication.java
│   │       └── resources/
│   │           ├── mapper/           # MyBatis XML
│   │           ├── application.yml   # 主配置
│   │           └── application-dev.yml
│   └── pom.xml
│
├── frontend/                         # 前端 React 项目
│   ├── src/
│   │   ├── api/                      # API 请求封装
│   │   ├── components/               # 公共组件
│   │   │   └── flow/                 # 规则画布节点/边组件
│   │   ├── pages/                    # 页面组件
│   │   │   ├── RuleList/
│   │   │   ├── RuleEditor/           # 规则编排画布页
│   │   │   ├── DeviceList/
│   │   │   ├── ActionLog/
│   │   │   ├── RuleTest/             # 规则测试模拟页
│   │   │   └── Dashboard/
│   │   ├── store/                    # Zustand 全局状态
│   │   ├── hooks/                    # 自定义 Hooks
│   │   ├── types/                    # TypeScript 类型定义
│   │   ├── utils/                    # 工具函数
│   │   ├── websocket/                # WebSocket 封装
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
│
├── sql/
│   └── init.sql                      # 数据库初始化脚本（含示例数据）
│
├── docker-compose.yml                # MySQL/Redis/EMQX 编排文件
│
└── README.md                         # 项目说明文档
```

---

## ❓ 常见问题 FAQ

### Q1：规则保存后为什么没有触发？

排查清单：
1. 确认规则状态为 **已启用**（列表中显示绿色）
2. 检查设备是否 **在线**，MQTT Broker 连接正常
3. 确认遥测数据的字段名和条件中配置的字段名 **完全一致**（区分大小写）
4. 查看后端日志是否有 DRL 编译错误：`RuleCompiler 编译失败`
5. 使用 **规则测试** 页面发送模拟数据，确认逻辑正确性

### Q2：EMQX 连接失败怎么办？

```bash
# 1. 确认 EMQX 容器正常运行
docker ps | grep emqx

# 2. 查看日志
docker logs iot-emqx

# 3. 默认账号密码：admin / public
#    访问 http://localhost:18083 控制台验证
```

若端口冲突，修改 `docker-compose.yml` 中端口映射，同步修改后端 `application.yml`。

### Q3：修改 rule_json 字段的结构，DRL 编译器需要改哪里？

DRL 编译逻辑集中在以下模块：

```
backend/src/main/java/com/iot/rule/engine/compiler/
├── RuleJsonToDrlCompiler.java      # 主编译器入口
├── ConditionNodeCompiler.java      # 条件节点编译
├── LogicNodeCompiler.java          # 逻辑门节点编译
├── ActionNodeCompiler.java         # 动作节点编译
└── model/                          # 画布节点 POJO 定义
```

扩展节点类型需实现 `NodeCompiler` 接口并在 `RuleJsonToDrlCompiler` 中注册。

### Q4：如何接入非 MQTT 协议的设备？

实现 `com.iot.rule.engine.mqtt.DeviceDataAdapter` 接口，将第三方协议（HTTP/Modbus/OPC-UA）转换为标准的 `TelemetryEvent`，然后调用 `RuleEngineService.evaluate()` 手动触发规则评估。

### Q5：规则数量很多（>1000）时性能如何？

- 单 KieSession 建议不超过 **500 条** 规则，超过后建议按设备分组拆分为多个 KieSession
- Redis 中缓存规则和会话状态，避免频繁查询数据库
- 启用 `drools.phreak` 模式（默认），匹配算法为 Rete-OO，复杂度接近 O(1)

### Q6：如何开启 Debug 模式查看规则命中细节？

在 `application.yml` 中配置：

```yaml
logging:
  level:
    com.iot.rule.engine.drools: DEBUG
    org.drools.core: INFO
```

日志会输出每条规则的匹配过程、Fact 对象插入/撤回事件。

---

## 📄 License

```
MIT License

Copyright (c) 2024 IoT Rule Engine Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 📬 联系作者

| 方式 | 信息 |
|:-----|:-----|
| 🌏 项目主页 | `https://github.com/your-org/iot-rule-engine` |
| 📧 邮箱 | `dev@iot-rule-engine.com` |
| 💬 讨论群 | 微信扫码入群（详见项目主页） |
| 🐛 Issue | 欢迎提交 Issue 和 PR |

---

<div align="center">

**如果这个项目对你有帮助，请给一个 ⭐ Star 支持我们！**

Made with ❤️ by IoT Rule Engine Team

</div>
