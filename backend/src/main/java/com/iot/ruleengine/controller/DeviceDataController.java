package com.iot.ruleengine.controller;

import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.mqtt.DeviceDataHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/device/data")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DeviceDataController {

    private final DeviceDataHandler deviceDataHandler;

    @Autowired
    public DeviceDataController(DeviceDataHandler deviceDataHandler) {
        this.deviceDataHandler = deviceDataHandler;
    }

    @PostMapping("/{deviceId}/telemetry")
    public Result<Map<String, Object>> receiveTelemetry(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> payload) {
        try {
            log.info("收到HTTP遥测数据上报, deviceId={}, payload={}", deviceId, payload);
            String jsonPayload = com.alibaba.fastjson.JSON.toJSONString(payload);
            deviceDataHandler.processTelemetry(deviceId, jsonPayload);
            return Result.success(payload, "遥测数据上报成功");
        } catch (Exception e) {
            log.error("处理HTTP遥测数据失败, deviceId={}", deviceId, e);
            return Result.error("处理遥测数据失败: " + e.getMessage());
        }
    }

    @PostMapping("/{deviceId}/status")
    public Result<Map<String, Object>> receiveStatus(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> payload) {
        try {
            log.info("收到HTTP状态数据上报, deviceId={}, payload={}", deviceId, payload);
            String jsonPayload = com.alibaba.fastjson.JSON.toJSONString(payload);
            deviceDataHandler.processStatus(deviceId, jsonPayload);
            return Result.success(payload, "状态数据上报成功");
        } catch (Exception e) {
            log.error("处理HTTP状态数据失败, deviceId={}", deviceId, e);
            return Result.error("处理状态数据失败: " + e.getMessage());
        }
    }
}
