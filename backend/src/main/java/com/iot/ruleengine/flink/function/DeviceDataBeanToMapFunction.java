package com.iot.ruleengine.flink.function;

import com.iot.ruleengine.drools.DeviceData;

import java.util.HashMap;
import java.util.Map;

/**
 * DeviceData Bean 转 Aviator 执行环境 Map 的工具函数
 *
 * 【线程安全设计】：
 * - 该类为无状态工具类，所有方法均为static，不持有任何可变成员变量
 * - 每次调用beanToMap都会创建新的HashMap实例，不存在共享状态问题
 * - 可安全地在Flink多线程算子实例中并发调用
 *
 * 【性能设计】：
 * - 使用原生HashMap而非ConcurrentHashMap，因为在Flink算子的单线程执行上下文中不需要线程安全Map
 * - 初始容量设为32（考虑到attributes可能有较多键），减少resize开销
 * - attributes的putAll操作使用JDK优化后的批量复制，比逐个put效率更高
 * - 平铺策略：attributes中的key优先级高于bean字段，若冲突则以后者为准（符合需求）
 */
public class DeviceDataBeanToMapFunction {

    /**
     * HashMap初始容量，预估字段: deviceId, temperature, humidity, presence, time
     * 加上attributes中可能的扩展字段，32是比较合理的初始值
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 32;

    /**
     * 私有构造函数，禁止实例化
     */
    private DeviceDataBeanToMapFunction() {
    }

    /**
     * 将DeviceData对象转换为Aviator表达式执行所需的Map环境
     *
     * 转换规则：
     * 1. 先将DeviceData的顶级字段（deviceId, temperature, humidity, presence, time）放入Map
     * 2. 再将attributes中的所有key-value展开平铺到Map
     * 3. 如果attributes中的key与顶级字段名冲突，以attributes为准（后者覆盖前者）
     *
     * @param data DeviceData对象，允许为null（返回空Map）
     * @return Aviator可用的环境Map，永远不会返回null
     */
    public static Map<String, Object> beanToMap(DeviceData data) {
        Map<String, Object> env = new HashMap<>(DEFAULT_INITIAL_CAPACITY);

        if (data == null) {
            return env;
        }

        // ========== 步骤1：平铺Bean的顶级字段 ==========
        // 设备ID：字符串，用于规则中按设备筛选
        env.put("deviceId", data.getDeviceId());
        // 温度：Double，可能为null，Aviator表达式中需用 temperature != nil 判断
        env.put("temperature", data.getTemperature());
        // 湿度：Double，可能为null
        env.put("humidity", data.getHumidity());
        // 人体存在：Boolean，可能为null
        env.put("presence", data.getPresence());
        // 时间字符串：如 "14:30"，用于时间段判断
        env.put("time", data.getTime());

        // ========== 步骤2：展开attributes中的所有key（如果存在） ==========
        // 注意：putAll会覆盖已存在的同名key，符合需求"key冲突以后者为准"
        Map<String, Object> attributes = data.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            env.putAll(attributes);
        }

        return env;
    }
}
