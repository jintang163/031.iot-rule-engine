package com.iot.ruleengine.service;

import com.iot.ruleengine.entity.AlertNotifyConfig;

import java.util.List;

public interface AlertNotifyConfigService {

    List<AlertNotifyConfig> listEnabledConfigs();

    List<AlertNotifyConfig> listAllConfigs();

    AlertNotifyConfig getById(Long id);

    AlertNotifyConfig save(AlertNotifyConfig config);

    AlertNotifyConfig update(AlertNotifyConfig config);

    void delete(Long id);
}
