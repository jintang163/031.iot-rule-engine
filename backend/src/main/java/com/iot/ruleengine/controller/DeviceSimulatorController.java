package com.iot.ruleengine.controller;

import com.iot.ruleengine.dto.DeviceSimulatorConfig;
import com.iot.ruleengine.dto.DeviceSimulatorStatus;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.simulator.DeviceSimulatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/device/simulator")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DeviceSimulatorController {

    private final DeviceSimulatorService deviceSimulatorService;

    @Autowired
    public DeviceSimulatorController(DeviceSimulatorService deviceSimulatorService) {
        this.deviceSimulatorService = deviceSimulatorService;
    }

    @PostMapping("/start")
    public Result<DeviceSimulatorStatus> startSimulator(@RequestBody DeviceSimulatorConfig config) {
        DeviceSimulatorStatus status = deviceSimulatorService.startSimulator(config);
        return Result.success(status, "模拟器已启动");
    }

    @PostMapping("/stop/{deviceId}")
    public Result<DeviceSimulatorStatus> stopSimulator(@PathVariable String deviceId) {
        DeviceSimulatorStatus status = deviceSimulatorService.stopSimulator(deviceId);
        return Result.success(status, "模拟器已停止");
    }

    @GetMapping("/status/{deviceId}")
    public Result<DeviceSimulatorStatus> getStatus(@PathVariable String deviceId) {
        DeviceSimulatorStatus status = deviceSimulatorService.getStatus(deviceId);
        return Result.success(status);
    }

    @GetMapping("/status")
    public Result<Map<String, DeviceSimulatorStatus>> getAllStatus() {
        Map<String, DeviceSimulatorStatus> statuses = deviceSimulatorService.getAllStatus();
        return Result.success(statuses);
    }

    @GetMapping("/config/{deviceId}")
    public Result<DeviceSimulatorConfig> getConfig(@PathVariable String deviceId) {
        DeviceSimulatorConfig config = deviceSimulatorService.getConfig(deviceId);
        return Result.success(config);
    }
}
