package com.iot.ruleengine.flink.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 动作执行命令模型
 *
 * 【线程安全设计】：
 * - 该类为不可变数据载体（使用Builder模式构建），一旦构造完成即只读，天然线程安全
 * - 所有字段类型均为Java原生不可变类型或Serializable安全类型
 * - 在Flink中作为流元素在算子间网络传输，必须实现Serializable
 *
 * 【性能设计】：
 * - 使用Lombok @Builder减少重复样板代码
 * - 不包含任何业务逻辑，仅为纯数据对象，减少GC压力
 * - Map类型使用原生HashMap（非同步），在单线程Flink算子上下文内使用无需加锁
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionExecuteCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 触发的规则ID
     */
    private Long ruleId;

    /**
     * 触发的规则名称
     */
    private String ruleName;

    /**
     * 触发规则的源设备ID（产生数据的设备）
     */
    private String deviceId;

    /**
     * 动作类型，如：ALARM(告警)、NOTIFY(通知)、CONTROL(控制)、SWITCH(开关)等
     */
    private String actionType;

    /**
     * 动作参数Map，包含执行动作所需的所有参数
     * 例如：{"level": "warning", "message": "温度过高", "duration": 300}
     */
    private Map<String, Object> params;

    /**
     * 规则触发时间戳（毫秒）
     */
    private long triggerTime;

    /**
     * 目标设备ID（要执行命令的设备）
     * 如果为null，则默认使用deviceId（即产生数据的设备自身）
     */
    private String targetDeviceId;
}
