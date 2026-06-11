package com.iot.ruleengine.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.DeviceDTO;
import com.iot.ruleengine.entity.Device;

import java.util.List;
import java.util.Map;

public interface DeviceService {

    Device saveDevice(DeviceDTO deviceDTO);

    Device updateDevice(DeviceDTO deviceDTO);

    void deleteDevice(Long id);

    Device getById(Long id);

    Page<Device> listDevices(Page<Device> page, Map<String, Object> params);

    void updateStatus(String deviceId, Map<String, Object> status);

    void controlDevice(String deviceId, String action, Map<String, Object> params);

    List<Device> listOnlineDevices();
}
