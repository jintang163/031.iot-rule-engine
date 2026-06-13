package com.iot.ruleengine.controller;

import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.history.RuleHistoryService;
import com.iot.ruleengine.history.RuleTriggerHistory;
import com.iot.ruleengine.security.RequirePermission;
import com.iot.ruleengine.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/rule-history")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequirePermission("rule:view")
public class RuleHistoryController {

    private final RuleHistoryService ruleHistoryService;

    @Autowired
    public RuleHistoryController(RuleHistoryService ruleHistoryService) {
        this.ruleHistoryService = ruleHistoryService;
    }

    @GetMapping("/{ruleId}/list")
    public Result<PageResult<RuleTriggerHistory>> listHistory(
            @PathVariable Long ruleId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }

        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }

        PageResult<RuleTriggerHistory> result = ruleHistoryService.searchByRuleId(
                ruleId, tenantId, startTime, endTime, page, size
        );

        return Result.success(result);
    }

    @GetMapping("/{historyId}/snapshot")
    public Result<RuleTriggerHistory> getSnapshot(@PathVariable String historyId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }

        RuleTriggerHistory history = ruleHistoryService.getSnapshot(historyId, tenantId);
        if (history == null) {
            return Result.fail(404, "历史记录不存在或已过期");
        }

        return Result.success(history);
    }

    @GetMapping("/enabled")
    public Result<Map<String, Object>> isHistoryEnabled() {
        Map<String, Object> info = new HashMap<>();
        info.put("enabled", ruleHistoryService.isEnabled());
        info.put("retentionDays", 90);
        return Result.success(info);
    }
}
