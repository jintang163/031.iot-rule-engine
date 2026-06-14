package com.iot.ruleengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.ruleengine.entity.AlertNotifyConfig;
import com.iot.ruleengine.repository.AlertNotifyConfigRepository;
import com.iot.ruleengine.service.AlertNotifyConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AlertNotifyConfigServiceImpl implements AlertNotifyConfigService {

    private final AlertNotifyConfigRepository alertNotifyConfigRepository;

    @Autowired
    public AlertNotifyConfigServiceImpl(AlertNotifyConfigRepository alertNotifyConfigRepository) {
        this.alertNotifyConfigRepository = alertNotifyConfigRepository;
    }

    @Override
    public List<AlertNotifyConfig> listEnabledConfigs() {
        QueryWrapper<AlertNotifyConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("enabled", 1);
        return alertNotifyConfigRepository.selectList(queryWrapper);
    }

    @Override
    public List<AlertNotifyConfig> listAllConfigs() {
        return alertNotifyConfigRepository.selectList(null);
    }

    @Override
    public AlertNotifyConfig getById(Long id) {
        return alertNotifyConfigRepository.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertNotifyConfig save(AlertNotifyConfig config) {
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        alertNotifyConfigRepository.insert(config);
        log.info("创建告警通知配置, id={}, channel={}", config.getId(), config.getChannel());
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertNotifyConfig update(AlertNotifyConfig config) {
        alertNotifyConfigRepository.updateById(config);
        log.info("更新告警通知配置, id={}, channel={}", config.getId(), config.getChannel());
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        alertNotifyConfigRepository.deleteById(id);
        log.info("删除告警通知配置, id={}", id);
    }
}
