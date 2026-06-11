package com.iot.ruleengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.DeviceDTO;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.exception.BusinessException;
import com.iot.ruleengine.mqtt.DeviceCommandService;
import com.iot.ruleengine.repository.DeviceRepository;
import com.iot.ruleengine.service.DeviceService;
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
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceCommandService deviceCommandService;

    @Autowired
    public DeviceServiceImpl(DeviceRepository deviceRepository, DeviceCommandService deviceCommandService) {
        this.deviceRepository = deviceRepository;
        this.deviceCommandService = deviceCommandService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device saveDevice(DeviceDTO deviceDTO) {
        if (!StringUtils.hasText(deviceDTO.getDeviceId())) {
            throw new BusinessException("设备ID不能为空");
        }

        QueryWrapper<Device> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("device_id", deviceDTO.getDeviceId());
        Device existDevice = deviceRepository.selectOne(queryWrapper);
        if (existDevice != null) {
            throw new BusinessException("设备ID已存在");
        }

        Device device = new Device();
        device.setDeviceId(deviceDTO.getDeviceId());
        device.setDeviceName(deviceDTO.getName());
        device.setDeviceType(deviceDTO.getType());
        device.setOnlineStatus(deviceDTO.getOnline() != null ? deviceDTO.getOnline() : 0);

        deviceRepository.insert(device);
        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device updateDevice(DeviceDTO deviceDTO) {
        Device existDevice = deviceRepository.selectById(deviceDTO.getId());
        if (existDevice == null) {
            throw new BusinessException("设备不存在");
        }

        if (StringUtils.hasText(deviceDTO.getName())) {
            existDevice.setDeviceName(deviceDTO.getName());
        }
        if (StringUtils.hasText(deviceDTO.getType())) {
            existDevice.setDeviceType(deviceDTO.getType());
        }
        if (deviceDTO.getOnline() != null) {
            existDevice.setOnlineStatus(deviceDTO.getOnline());
        }

        deviceRepository.updateById(existDevice);
        return existDevice;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(Long id) {
        Device existDevice = deviceRepository.selectById(id);
        if (existDevice == null) {
            throw new BusinessException("设备不存在");
        }
        deviceRepository.deleteById(id);
    }

    @Override
    public Device getById(Long id) {
        Device device = deviceRepository.selectById(id);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    @Override
    public Page<Device> listDevices(Page<Device> page, Map<String, Object> params) {
        QueryWrapper<Device> queryWrapper = new QueryWrapper<>();

        if (params != null) {
            if (params.containsKey("deviceId") && StringUtils.hasText((String) params.get("deviceId"))) {
                queryWrapper.like("device_id", params.get("deviceId"));
            }
            if (params.containsKey("name") && StringUtils.hasText((String) params.get("name"))) {
                queryWrapper.like("device_name", params.get("name"));
            }
            if (params.containsKey("type") && StringUtils.hasText((String) params.get("type"))) {
                queryWrapper.eq("device_type", params.get("type"));
            }
            if (params.containsKey("online") && params.get("online") != null) {
                queryWrapper.eq("online_status", params.get("online"));
            }
        }

        queryWrapper.orderByDesc("create_time");
        return deviceRepository.selectPage(page, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String deviceId, Map<String, Object> status) {
        if (!StringUtils.hasText(deviceId)) {
            throw new BusinessException("设备ID不能为空");
        }

        UpdateWrapper<Device> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("device_id", deviceId);

        if (status != null) {
            if (status.containsKey("onlineStatus") && status.get("onlineStatus") != null) {
                updateWrapper.set("online_status", status.get("onlineStatus"));
            }
            if (status.containsKey("lastReportTime")) {
                updateWrapper.set("last_report_time", LocalDateTime.now());
            }
            if (status.containsKey("firmwareVersion") && status.get("firmwareVersion") != null) {
                updateWrapper.set("firmware_version", status.get("firmwareVersion"));
            }
            if (status.containsKey("ipAddress") && status.get("ipAddress") != null) {
                updateWrapper.set("ip_address", status.get("ipAddress"));
            }
        }

        int rows = deviceRepository.update(null, updateWrapper);
        if (rows == 0) {
            throw new BusinessException("设备不存在或更新失败");
        }
    }

    @Override
    public void controlDevice(String deviceId, String action, Map<String, Object> params) {
        if (!StringUtils.hasText(deviceId)) {
            throw new BusinessException("设备ID不能为空");
        }
        if (!StringUtils.hasText(action)) {
            throw new BusinessException("动作不能为空");
        }

        QueryWrapper<Device> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("device_id", deviceId);
        Device existDevice = deviceRepository.selectOne(queryWrapper);
        if (existDevice == null) {
            throw new BusinessException("设备不存在");
        }

        deviceCommandService.sendCommand(deviceId, action, params);
    }

    @Override
    public List<Device> listOnlineDevices() {
        QueryWrapper<Device> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("online_status", 1);
        queryWrapper.orderByDesc("last_report_time");
        return deviceRepository.selectList(queryWrapper);
    }
}
