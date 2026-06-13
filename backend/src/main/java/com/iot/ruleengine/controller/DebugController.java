package com.iot.ruleengine.controller;

import com.iot.ruleengine.debug.RuleDebugService;
import com.iot.ruleengine.dto.DebugRequest;
import com.iot.ruleengine.dto.DebugSessionStatus;
import com.iot.ruleengine.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/debug")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DebugController {

    private final RuleDebugService ruleDebugService;

    @Autowired
    public DebugController(RuleDebugService ruleDebugService) {
        this.ruleDebugService = ruleDebugService;
    }

    @PostMapping("/start")
    public Result<DebugSessionStatus> startDebugSession(@Valid @RequestBody DebugRequest request) {
        log.info("收到调试启动请求: ruleId={}, singleStep={}, sensorData={}",
                request.getRuleId(), request.isSingleStepMode(), request.getSensorData());
        DebugSessionStatus status = ruleDebugService.startDebugSession(request);
        return Result.success(status);
    }

    @GetMapping("/{sessionId}/status")
    public Result<DebugSessionStatus> getSessionStatus(@PathVariable String sessionId) {
        DebugSessionStatus status = ruleDebugService.getSessionStatus(sessionId);
        return Result.success(status);
    }

    @PostMapping("/{sessionId}/step")
    public Result<DebugSessionStatus> stepNext(@PathVariable String sessionId) {
        DebugSessionStatus status = ruleDebugService.stepNext(sessionId);
        return Result.success(status);
    }

    @PostMapping("/{sessionId}/resume")
    public Result<DebugSessionStatus> resume(@PathVariable String sessionId) {
        DebugSessionStatus status = ruleDebugService.resume(sessionId);
        return Result.success(status);
    }

    @PostMapping("/{sessionId}/pause")
    public Result<DebugSessionStatus> pause(@PathVariable String sessionId) {
        DebugSessionStatus status = ruleDebugService.pause(sessionId);
        return Result.success(status);
    }

    @PostMapping("/{sessionId}/stop")
    public Result<DebugSessionStatus> stopSession(@PathVariable String sessionId) {
        DebugSessionStatus status = ruleDebugService.stopSession(sessionId);
        return Result.success(status);
    }
}
