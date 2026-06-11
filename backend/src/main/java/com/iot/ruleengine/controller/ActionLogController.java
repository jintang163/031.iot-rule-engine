package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.ActionLog;
import com.iot.ruleengine.repository.ActionLogRepository;
import com.iot.ruleengine.service.ActionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/action-log")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ActionLogController {

    private final ActionLogService actionLogService;
    private final ActionLogRepository actionLogRepository;

    @Autowired
    public ActionLogController(ActionLogService actionLogService, ActionLogRepository actionLogRepository) {
        this.actionLogService = actionLogService;
        this.actionLogRepository = actionLogRepository;
    }

    @GetMapping("/list")
    public Result<Page<ActionLog>> listLogs(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Integer executeStatus,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        Page<ActionLog> page = new Page<>(pageNum, pageSize);
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", deviceId);
        params.put("actionType", actionType);
        params.put("executeStatus", executeStatus);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        Page<ActionLog> result = actionLogService.listLogs(page, params);
        return Result.success(result);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        QueryWrapper<ActionLog> successWrapper = new QueryWrapper<>();
        successWrapper.eq("execute_status", 1);
        if (deviceId != null) {
            successWrapper.eq("device_id", deviceId);
        }
        if (startTime != null) {
            successWrapper.ge("execute_time", startTime);
        }
        if (endTime != null) {
            successWrapper.le("execute_time", endTime);
        }
        Long successCount = actionLogRepository.selectCount(successWrapper);

        QueryWrapper<ActionLog> failWrapper = new QueryWrapper<>();
        failWrapper.eq("execute_status", 0);
        if (deviceId != null) {
            failWrapper.eq("device_id", deviceId);
        }
        if (startTime != null) {
            failWrapper.ge("execute_time", startTime);
        }
        if (endTime != null) {
            failWrapper.le("execute_time", endTime);
        }
        Long failCount = actionLogRepository.selectCount(failWrapper);

        QueryWrapper<ActionLog> totalWrapper = new QueryWrapper<>();
        if (deviceId != null) {
            totalWrapper.eq("device_id", deviceId);
        }
        if (startTime != null) {
            totalWrapper.ge("execute_time", startTime);
        }
        if (endTime != null) {
            totalWrapper.le("execute_time", endTime);
        }
        Long totalCount = actionLogRepository.selectCount(totalWrapper);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("successCount", successCount);
        statistics.put("failCount", failCount);
        statistics.put("totalCount", totalCount);
        statistics.put("successRate", totalCount > 0 ? (successCount * 100.0 / totalCount) : 0);

        return Result.success(statistics);
    }
}
