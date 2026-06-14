package com.iot.ruleengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.entity.AlertRecord;
import com.iot.ruleengine.exception.BusinessException;
import com.iot.ruleengine.repository.AlertRecordRepository;
import com.iot.ruleengine.service.AlertService;
import com.iot.ruleengine.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlertServiceImpl implements AlertService {

    private final AlertRecordRepository alertRecordRepository;

    @Autowired
    public AlertServiceImpl(AlertRecordRepository alertRecordRepository) {
        this.alertRecordRepository = alertRecordRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRecord createAlert(AlertRecord alertRecord) {
        if (alertRecord == null) {
            throw new BusinessException("告警记录不能为空");
        }
        if (!StringUtils.hasText(alertRecord.getLevel())) {
            alertRecord.setLevel("info");
        }
        if (!StringUtils.hasText(alertRecord.getStatus())) {
            alertRecord.setStatus("pending");
        }
        if (alertRecord.getNotifyStatus() == null) {
            alertRecord.setNotifyStatus(0);
        }
        alertRecord.setCreateTime(LocalDateTime.now());
        alertRecordRepository.insert(alertRecord);
        log.info("创建告警记录, id={}, level={}, message={}", alertRecord.getId(), alertRecord.getLevel(), alertRecord.getMessage());
        return alertRecord;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRecord createAlert(Long ruleId, String ruleName, String deviceId,
                                   String level, String message, String detail) {
        AlertRecord record = new AlertRecord();
        record.setRuleId(ruleId);
        record.setRuleName(ruleName);
        record.setDeviceId(deviceId);
        record.setLevel(level);
        record.setMessage(message);
        record.setDetail(detail);
        record.setStatus("pending");
        record.setNotifyStatus(0);
        record.setCreateTime(LocalDateTime.now());
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            record.setTenantId(tenantId);
        }
        alertRecordRepository.insert(record);
        log.info("创建告警记录, id={}, ruleId={}, level={}, message={}", record.getId(), ruleId, level, message);
        return record;
    }

    @Override
    public Page<AlertRecord> listAlerts(Page<AlertRecord> page, Map<String, Object> params) {
        QueryWrapper<AlertRecord> queryWrapper = new QueryWrapper<>();
        if (params != null) {
            if (params.containsKey("ruleId") && params.get("ruleId") != null) {
                queryWrapper.eq("rule_id", params.get("ruleId"));
            }
            if (params.containsKey("deviceId") && StringUtils.hasText((String) params.get("deviceId"))) {
                queryWrapper.eq("device_id", params.get("deviceId"));
            }
            if (params.containsKey("level") && StringUtils.hasText((String) params.get("level"))) {
                queryWrapper.eq("level", params.get("level"));
            }
            if (params.containsKey("status") && StringUtils.hasText((String) params.get("status"))) {
                queryWrapper.eq("status", params.get("status"));
            }
            if (params.containsKey("startTime") && params.get("startTime") != null) {
                queryWrapper.ge("create_time", params.get("startTime"));
            }
            if (params.containsKey("endTime") && params.get("endTime") != null) {
                queryWrapper.le("create_time", params.get("endTime"));
            }
            if (params.containsKey("keyword") && StringUtils.hasText((String) params.get("keyword"))) {
                String keyword = (String) params.get("keyword");
                queryWrapper.and(w -> w.like("message", keyword).or().like("rule_name", keyword));
            }
        }
        queryWrapper.orderByDesc("create_time");
        return alertRecordRepository.selectPage(page, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acknowledgeAlert(Long id, String acknowledgedBy) {
        AlertRecord record = alertRecordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException("告警记录不存在");
        }
        if ("cleared".equals(record.getStatus())) {
            throw new BusinessException("已清除的告警不能确认");
        }
        UpdateWrapper<AlertRecord> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id)
                .set("status", "acknowledged")
                .set("acknowledged_by", acknowledgedBy)
                .set("acknowledged_time", LocalDateTime.now());
        alertRecordRepository.update(null, updateWrapper);
        log.info("告警已确认, id={}, acknowledgedBy={}", id, acknowledgedBy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearAlert(Long id, String clearedBy) {
        AlertRecord record = alertRecordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException("告警记录不存在");
        }
        UpdateWrapper<AlertRecord> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id)
                .set("status", "cleared")
                .set("cleared_by", clearedBy)
                .set("cleared_time", LocalDateTime.now());
        if (record.getAcknowledgedBy() == null) {
            updateWrapper.set("acknowledged_by", clearedBy)
                    .set("acknowledged_time", LocalDateTime.now());
        }
        alertRecordRepository.update(null, updateWrapper);
        log.info("告警已清除, id={}, clearedBy={}", id, clearedBy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAcknowledge(List<Long> ids, String acknowledgedBy) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            try {
                acknowledgeAlert(id, acknowledgedBy);
            } catch (Exception e) {
                log.warn("批量确认告警失败, id={}, error={}", id, e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchClear(List<Long> ids, String clearedBy) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            try {
                clearAlert(id, clearedBy);
            } catch (Exception e) {
                log.warn("批量清除告警失败, id={}, error={}", id, e.getMessage());
            }
        }
    }

    @Override
    public Map<String, Object> getStatistics(Map<String, Object> params) {
        Map<String, Object> statistics = new HashMap<>();

        QueryWrapper<AlertRecord> baseWrapper = buildStatsWrapper(params);

        Long totalCount = alertRecordRepository.selectCount(baseWrapper);
        statistics.put("totalCount", totalCount);

        QueryWrapper<AlertRecord> pendingWrapper = buildStatsWrapper(params);
        pendingWrapper.eq("status", "pending");
        statistics.put("pendingCount", alertRecordRepository.selectCount(pendingWrapper));

        QueryWrapper<AlertRecord> acknowledgedWrapper = buildStatsWrapper(params);
        acknowledgedWrapper.eq("status", "acknowledged");
        statistics.put("acknowledgedCount", alertRecordRepository.selectCount(acknowledgedWrapper));

        QueryWrapper<AlertRecord> clearedWrapper = buildStatsWrapper(params);
        clearedWrapper.eq("status", "cleared");
        statistics.put("clearedCount", alertRecordRepository.selectCount(clearedWrapper));

        QueryWrapper<AlertRecord> criticalWrapper = buildStatsWrapper(params);
        criticalWrapper.eq("level", "critical");
        statistics.put("criticalCount", alertRecordRepository.selectCount(criticalWrapper));

        QueryWrapper<AlertRecord> warningWrapper = buildStatsWrapper(params);
        warningWrapper.eq("level", "warning");
        statistics.put("warningCount", alertRecordRepository.selectCount(warningWrapper));

        QueryWrapper<AlertRecord> infoWrapper = buildStatsWrapper(params);
        infoWrapper.eq("level", "info");
        statistics.put("infoCount", alertRecordRepository.selectCount(infoWrapper));

        QueryWrapper<AlertRecord> todayWrapper = new QueryWrapper<>();
        todayWrapper.ge("create_time", LocalDateTime.now().toLocalDate().atStartOfDay());
        if (params != null && params.containsKey("deviceId") && StringUtils.hasText((String) params.get("deviceId"))) {
            todayWrapper.eq("device_id", params.get("deviceId"));
        }
        statistics.put("todayCount", alertRecordRepository.selectCount(todayWrapper));

        return statistics;
    }

    @Override
    public AlertRecord getById(Long id) {
        return alertRecordRepository.selectById(id);
    }

    private QueryWrapper<AlertRecord> buildStatsWrapper(Map<String, Object> params) {
        QueryWrapper<AlertRecord> wrapper = new QueryWrapper<>();
        if (params != null) {
            if (params.containsKey("deviceId") && StringUtils.hasText((String) params.get("deviceId"))) {
                wrapper.eq("device_id", params.get("deviceId"));
            }
            if (params.containsKey("startTime") && params.get("startTime") != null) {
                wrapper.ge("create_time", params.get("startTime"));
            }
            if (params.containsKey("endTime") && params.get("endTime") != null) {
                wrapper.le("create_time", params.get("endTime"));
            }
        }
        return wrapper;
    }
}
