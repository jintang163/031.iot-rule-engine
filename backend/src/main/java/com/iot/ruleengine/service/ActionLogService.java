package com.iot.ruleengine.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.entity.ActionLog;

import java.util.List;
import java.util.Map;

public interface ActionLogService {

    void saveLog(ActionLog actionLog);

    Page<ActionLog> listLogs(Page<ActionLog> page, Map<String, Object> params);

    List<ActionLog> listFailedLogsForRetry();
}
