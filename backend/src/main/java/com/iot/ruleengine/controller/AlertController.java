package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.AlertNotifyConfig;
import com.iot.ruleengine.entity.AlertRecord;
import com.iot.ruleengine.service.AlertNotifyConfigService;
import com.iot.ruleengine.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alert")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AlertController {

    private final AlertService alertService;
    private final AlertNotifyConfigService alertNotifyConfigService;

    @Autowired
    public AlertController(AlertService alertService, AlertNotifyConfigService alertNotifyConfigService) {
        this.alertService = alertService;
        this.alertNotifyConfigService = alertNotifyConfigService;
    }

    @GetMapping("/list")
    public Result<PageResult<AlertRecord>> listAlerts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        Page<AlertRecord> page = new Page<>(pageNum, pageSize);
        Map<String, Object> params = new HashMap<>();
        params.put("ruleId", ruleId);
        params.put("deviceId", deviceId);
        params.put("level", level);
        params.put("status", status);
        params.put("keyword", keyword);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        Page<AlertRecord> resultPage = alertService.listAlerts(page, params);
        return Result.success(PageResult.of(resultPage));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        Map<String, Object> params = new HashMap<>();
        params.put("ruleId", ruleId);
        params.put("deviceId", deviceId);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        return Result.success(alertService.getStatistics(params));
    }

    @GetMapping("/{id}")
    public Result<AlertRecord> getAlert(@PathVariable Long id) {
        AlertRecord record = alertService.getById(id);
        if (record == null) {
            return Result.fail("告警记录不存在");
        }
        return Result.success(record);
    }

    @PutMapping("/{id}/acknowledge")
    public Result<Void> acknowledgeAlert(
            @PathVariable Long id,
            @RequestParam(defaultValue = "system") String acknowledgedBy) {
        alertService.acknowledgeAlert(id, acknowledgedBy);
        return Result.success();
    }

    @PutMapping("/{id}/clear")
    public Result<Void> clearAlert(
            @PathVariable Long id,
            @RequestParam(defaultValue = "system") String clearedBy) {
        alertService.clearAlert(id, clearedBy);
        return Result.success();
    }

    @PutMapping("/batch-acknowledge")
    public Result<Void> batchAcknowledge(
            @RequestBody List<Long> ids,
            @RequestParam(defaultValue = "system") String acknowledgedBy) {
        alertService.batchAcknowledge(ids, acknowledgedBy);
        return Result.success();
    }

    @PutMapping("/batch-clear")
    public Result<Void> batchClear(
            @RequestBody List<Long> ids,
            @RequestParam(defaultValue = "system") String clearedBy) {
        alertService.batchClear(ids, clearedBy);
        return Result.success();
    }

    @GetMapping("/notify-config/list")
    public Result<List<AlertNotifyConfig>> listNotifyConfigs() {
        return Result.success(alertNotifyConfigService.listAllConfigs());
    }

    @GetMapping("/notify-config/{id}")
    public Result<AlertNotifyConfig> getNotifyConfig(@PathVariable Long id) {
        AlertNotifyConfig config = alertNotifyConfigService.getById(id);
        if (config == null) {
            return Result.fail("通知配置不存在");
        }
        return Result.success(config);
    }

    @PostMapping("/notify-config")
    public Result<AlertNotifyConfig> saveNotifyConfig(@RequestBody AlertNotifyConfig config) {
        return Result.success(alertNotifyConfigService.save(config));
    }

    @PutMapping("/notify-config")
    public Result<AlertNotifyConfig> updateNotifyConfig(@RequestBody AlertNotifyConfig config) {
        return Result.success(alertNotifyConfigService.update(config));
    }

    @DeleteMapping("/notify-config/{id}")
    public Result<Void> deleteNotifyConfig(@PathVariable Long id) {
        alertNotifyConfigService.delete(id);
        return Result.success();
    }

    @PostMapping("/internal/create")
    public Result<AlertRecord> internalCreateAlert(@RequestBody java.util.Map<String, Object> payload) {
        try {
            Long ruleId = payload.get("ruleId") != null ? Long.valueOf(String.valueOf(payload.get("ruleId"))) : null;
            String ruleName = payload.get("ruleName") != null ? String.valueOf(payload.get("ruleName")) : null;
            String deviceId = payload.get("deviceId") != null ? String.valueOf(payload.get("deviceId")) : null;
            String level = payload.get("level") != null ? String.valueOf(payload.get("level")) : "warning";
            String message = payload.get("message") != null ? String.valueOf(payload.get("message")) : "设备异常告警";
            String detail = payload.get("detail") != null ? String.valueOf(payload.get("detail")) : null;
            if (detail == null && payload.containsKey("params") && payload.get("params") != null) {
                try {
                    detail = com.alibaba.fastjson.JSON.toJSONString(payload.get("params"));
                } catch (Exception ignore) {
                }
            }
            AlertRecord record = alertService.createAlert(ruleId, ruleName, deviceId, level, message, detail);
            return Result.success(record);
        } catch (Exception e) {
            return Result.fail("创建告警失败: " + e.getMessage());
        }
    }
}
