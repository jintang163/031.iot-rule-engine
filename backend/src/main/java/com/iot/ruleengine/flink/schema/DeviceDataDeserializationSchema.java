package com.iot.ruleengine.flink.schema;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.iot.ruleengine.drools.DeviceData;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Kafka消息反序列化Schema：将Kafka中的JSON字节数组反序列化为DeviceData对象
 *
 * 【线程安全设计】：
 * - 该类无状态，所有成员均为final或不可变
 * - Fastjson的JSON.parseObject是线程安全的（无静态可变状态）
 * - Flink在Source并行实例中每个实例持有一个独立的Schema对象，不会跨线程共享
 * - 唯一需要注意：log字段是SLF4J Logger，天然线程安全
 *
 * 【性能设计】：
 * - 直接使用Fastjson而非Jackson，Fastjson在IoT场景下反序列化性能更高（~2x Jackson）
 * - 使用StandardCharsets.UTF_8常量而非字符串"UTF-8"，避免每次查码表
 * - 不做冗余的字段校验，反序列化失败时返回null（由Flink Source过滤）
 * - 使用TypeExtractor.createTypeInfo生成TypeInformation，缓存反射结果
 *
 * 【故障容错】：
 * - 反序列化异常时捕获JSONException，打印warn日志并返回null
 * - Flink Kafka Source会自动跳过null结果（不会进入流处理）
 * - 不会因单条坏消息导致整个Source崩溃
 */
public class DeviceDataDeserializationSchema implements DeserializationSchema<DeviceData> {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(DeviceDataDeserializationSchema.class);

    /**
     * 反序列化Kafka消息为DeviceData对象
     *
     * @param message Kafka消息的value字节数组（UTF-8编码的JSON字符串）
     * @return 反序列化后的DeviceData对象，失败时返回null
     */
    @Override
    public DeviceData deserialize(byte[] message) throws IOException {
        // 空消息直接返回null，Flink会自动过滤
        if (message == null || message.length == 0) {
            log.warn("收到空的Kafka消息，已跳过");
            return null;
        }

        try {
            // 将字节数组转为JSON字符串，使用UTF-8常量（性能优于字符串字面量）
            String jsonStr = new String(message, StandardCharsets.UTF_8);

            // 使用Fastjson反序列化为DeviceData对象
            // Fastjson在并发场景下性能优于Jackson，且支持更灵活的字段映射
            DeviceData deviceData = JSON.parseObject(jsonStr, DeviceData.class);

            // 基本完整性校验：deviceId为必填字段
            if (deviceData == null) {
                log.warn("Kafka消息反序列化结果为null，原始消息: {}", jsonStr);
                return null;
            }

            if (deviceData.getDeviceId() == null || deviceData.getDeviceId().trim().isEmpty()) {
                log.warn("DeviceData缺少deviceId字段，已跳过，原始消息: {}", jsonStr);
                return null;
            }

            // 确保attributes不为null，避免后续NPE
            if (deviceData.getAttributes() == null) {
                deviceData.setAttributes(new java.util.HashMap<>());
            }

            return deviceData;

        } catch (JSONException e) {
            // JSON格式错误，仅记录warn日志，不抛出异常（避免Source崩溃）
            log.warn("Kafka消息JSON反序列化失败，原始消息: {}, 错误: {}",
                    new String(message, StandardCharsets.UTF_8), e.getMessage());
            return null;
        } catch (Exception e) {
            // 其他未知异常，记录error日志并返回null
            log.error("Kafka消息反序列化发生未知错误", e);
            return null;
        }
    }

    /**
     * 判断消息是否为流结束标记（Kafka无界流永远不会结束，返回false）
     */
    @Override
    public boolean isEndOfStream(DeviceData nextElement) {
        return false;
    }

    /**
     * 获取DeviceData的TypeInformation，用于Flink的类型系统和序列化优化
     *
     * 使用TypeExtractor.createTypeInfo而非硬编码PojoTypeInfo，
     * 原因：如果将来DeviceData类结构变化，此方法能自动适配，无需手动维护
     */
    @Override
    public TypeInformation<DeviceData> getProducedType() {
        return TypeExtractor.createTypeInfo(DeviceData.class);
    }
}
