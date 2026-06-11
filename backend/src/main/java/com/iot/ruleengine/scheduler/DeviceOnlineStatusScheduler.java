package com.iot.ruleengine.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.repository.DeviceRepository;
import com.iot.ruleengine.websocket.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
public class DeviceOnlineStatusScheduler {

    private static final long OFFLINE_THRESHOLD_MINUTES = 5;

    private final DeviceRepository deviceRepository;
    private final WebSocketService webSocketService;

    @Autowired
    public DeviceOnlineStatusScheduler(DeviceRepository deviceRepository, WebSocketService webSocketService) {
        this.deviceRepository = deviceRepository;
        this.webSocketService = webSocketService;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void checkDeviceOnlineStatus() {
        log.debug("开始检查设备在线状态");

        try {
            List<Device> devices = deviceRepository.selectList(
                    new QueryWrapper<Device>()
                            .select("id", "device_id", "device_name", "online_status", "last_report_time")
                            .eq("deleted", 0));

            if (devices == null || devices.isEmpty()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            int offlineCount = 0;
            int onlineCount = 0;
            int statusChangedCount = 0;

            for (Device device : devices) {
                LocalDateTime lastReportTime = device.getLastReportTime();
                Integer currentStatus = device.getOnlineStatus();
                boolean shouldBeOffline = lastReportTime == null ||
                        ChronoUnit.MINUTES.between(lastReportTime, now) > OFFLINE_THRESHOLD_MINUTES;

                Integer newStatus = shouldBeOffline ? 0 : 1;
                if (!newStatus.equals(currentStatus)) {
                    deviceRepository.updateOnlineStatus(device.getDeviceId(), newStatus, lastReportTime);
                    webSocketService.sendDeviceStatus(device.getDeviceId(), newStatus);
                    statusChangedCount++;
                    log.info("设备状态变更, deviceId: {}, deviceName: {}, 状态: {} -> {}",
                            device.getDeviceId(), device.getDeviceName(), currentStatus, newStatus);
                }

                if (newStatus == 1) {
                    onlineCount++;
                } else {
                    offlineCount++;
                }
            }

            log.info("设备在线状态检查完成, 总数: {}, 在线: {}, 离线: {}, 状态变更: {}",
                    devices.size(), onlineCount, offlineCount, statusChangedCount);
        } catch (Exception e) {
            log.error("设备在线状态检查异常", e);
        }
    }
}
