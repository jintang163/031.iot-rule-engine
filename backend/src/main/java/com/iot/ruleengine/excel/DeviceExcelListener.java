package com.iot.ruleengine.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DeviceExcelListener extends AnalysisEventListener<DeviceExcelVO> {

    private static final int BATCH_COUNT = 100;
    private final List<Device> cachedDataList = new ArrayList<>();
    private final DeviceRepository deviceRepository;
    private int successCount = 0;
    private int failCount = 0;
    private final List<String> errors = new ArrayList<>();

    public DeviceExcelListener(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public void invoke(DeviceExcelVO data, AnalysisContext context) {
        if (data == null || !StringUtils.hasText(data.getDeviceId())) {
            failCount++;
            errors.add("第 " + context.readRowHolder().getRowIndex() + " 行：设备ID不能为空");
            return;
        }

        try {
            Device device = new Device();
            device.setDeviceId(data.getDeviceId());
            device.setName(data.getName() != null ? data.getName() : data.getDeviceId());
            device.setType(data.getType() != null ? data.getType() : "sensor_temp");
            device.setRoom(data.getRoom());
            device.setProtocol(data.getProtocol() != null ? data.getProtocol() : "MQTT");
            device.setLocation(data.getLocation());
            device.setOnline("在线".equals(data.getOnlineStatus()) ? 1 : 0);

            cachedDataList.add(device);

            if (cachedDataList.size() >= BATCH_COUNT) {
                saveData();
                cachedDataList.clear();
            }
        } catch (Exception e) {
            failCount++;
            errors.add("第 " + context.readRowHolder().getRowIndex() + " 行：" + e.getMessage());
            log.error("解析Excel行失败", e);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!cachedDataList.isEmpty()) {
            saveData();
        }
        log.info("Excel解析完成，成功{}条，失败{}条", successCount, failCount);
    }

    private void saveData() {
        for (Device device : cachedDataList) {
            try {
                deviceRepository.insert(device);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add("设备ID " + device.getDeviceId() + "：" + e.getMessage());
                log.error("保存设备失败: deviceId={}", device.getDeviceId(), e);
            }
        }
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public List<String> getErrors() {
        return errors;
    }
}
