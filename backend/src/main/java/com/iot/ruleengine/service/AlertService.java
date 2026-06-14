package com.iot.ruleengine.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.entity.AlertRecord;

import java.util.Map;

public interface AlertService {

    AlertRecord createAlert(AlertRecord alertRecord);

    AlertRecord createAlert(Long ruleId, String ruleName, String deviceId,
                            String level, String message, String detail);

    Page<AlertRecord> listAlerts(Page<AlertRecord> page, Map<String, Object> params);

    void acknowledgeAlert(Long id, String acknowledgedBy);

    void clearAlert(Long id, String clearedBy);

    void batchAcknowledge(java.util.List<Long> ids, String acknowledgedBy);

    void batchClear(java.util.List<Long> ids, String clearedBy);

    Map<String, Object> getStatistics(Map<String, Object> params);

    AlertRecord getById(Long id);
}
