package com.iot.ruleengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.entity.ActionLog;
import com.iot.ruleengine.exception.BusinessException;
import com.iot.ruleengine.repository.ActionLogRepository;
import com.iot.ruleengine.service.ActionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ActionLogServiceImpl implements ActionLogService {

    private final ActionLogRepository actionLogRepository;

    @Autowired
    public ActionLogServiceImpl(ActionLogRepository actionLogRepository) {
        this.actionLogRepository = actionLogRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLog(ActionLog actionLog) {
        if (actionLog == null) {
            throw new BusinessException("日志对象不能为空");
        }
        actionLogRepository.insert(actionLog);
    }

    @Override
    public Page<ActionLog> listLogs(Page<ActionLog> page, Map<String, Object> params) {
        QueryWrapper<ActionLog> queryWrapper = new QueryWrapper<>();

        if (params != null) {
            if (params.containsKey("deviceId") && StringUtils.hasText((String) params.get("deviceId"))) {
                queryWrapper.eq("device_id", params.get("deviceId"));
            }
            if (params.containsKey("actionType") && StringUtils.hasText((String) params.get("actionType"))) {
                queryWrapper.eq("action_type", params.get("actionType"));
            }
            if (params.containsKey("executeStatus") && params.get("executeStatus") != null) {
                queryWrapper.eq("execute_status", params.get("executeStatus"));
            }
            if (params.containsKey("startTime") && params.get("startTime") != null) {
                queryWrapper.ge("execute_time", params.get("startTime"));
            }
            if (params.containsKey("endTime") && params.get("endTime") != null) {
                queryWrapper.le("execute_time", params.get("endTime"));
            }
        }

        queryWrapper.orderByDesc("execute_time");
        queryWrapper.orderByDesc("create_time");
        return actionLogRepository.selectPage(page, queryWrapper);
    }

    @Override
    public List<ActionLog> listFailedLogsForRetry() {
        QueryWrapper<ActionLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("execute_status", 0)
                .lt("retry_count", 3);
        queryWrapper.orderByAsc("create_time");
        return actionLogRepository.selectList(queryWrapper);
    }
}
