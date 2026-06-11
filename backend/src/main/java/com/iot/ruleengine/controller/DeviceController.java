package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.DeviceDTO;
import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/device")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DeviceController {

    private final DeviceService deviceService;

    @Autowired
    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public Result<Device> saveDevice(@Valid @RequestBody DeviceDTO deviceDTO) {
        Device device = deviceService.saveDevice(deviceDTO);
        return Result.success(device);
    }

    @PutMapping
    public Result<Device> updateDevice(@Valid @RequestBody DeviceDTO deviceDTO) {
        Device device = deviceService.updateDevice(deviceDTO);
        return Result.success(device);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteDevice(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Device> getById(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        return Result.success(device);
    }

    @GetMapping("/list")
    public Result<PageResult<Device>> listDevices(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer online) {
        Page<Device> page = new Page<>(pageNum, pageSize);
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", deviceId);
        params.put("name", name);
        params.put("type", type);
        params.put("online", online);
        Page<Device> result = deviceService.listDevices(page, params);
        return Result.success(PageResult.of(result));
    }

    @PutMapping("/{deviceId}/control")
    public Result<Map<String, Object>> controlDevice(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> body) {
        String action = (String) body.get("action");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        if (params == null) {
            params = new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("action", action);
        result.put("params", params);

        try {
            deviceService.controlDevice(deviceId, action, params);
            result.put("success", true);
            result.put("message", "指令发送成功");
            return Result.success(result, "指令发送成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "指令发送失败: " + e.getMessage());
            return Result.fail("指令发送失败: " + e.getMessage());
        }
    }

    @GetMapping("/online")
    public Result<List<Device>> listOnlineDevices() {
        List<Device> devices = deviceService.listOnlineDevices();
        return Result.success(devices);
    }
}
