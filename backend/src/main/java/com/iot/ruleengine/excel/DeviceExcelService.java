package com.iot.ruleengine.excel;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.ruleengine.entity.Device;
import com.iot.ruleengine.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DeviceExcelService {

    private final DeviceRepository deviceRepository;

    @Autowired
    public DeviceExcelService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public void exportDevices(HttpServletResponse response) throws IOException {
        List<Device> devices = deviceRepository.selectList(
                new QueryWrapper<Device>().orderByDesc("create_time")
        );

        List<DeviceExcelVO> voList = new ArrayList<>();
        for (Device device : devices) {
            DeviceExcelVO vo = new DeviceExcelVO();
            vo.setDeviceId(device.getDeviceId());
            vo.setName(device.getName());
            vo.setType(device.getType());
            vo.setRoom(device.getRoom());
            vo.setProtocol(device.getProtocol());
            vo.setLocation(device.getLocation());
            vo.setOnlineStatus(device.getOnline() != null && device.getOnline() == 1 ? "在线" : "离线");
            voList.add(vo);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("设备列表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), DeviceExcelVO.class)
                .sheet("设备列表")
                .doWrite(voList);

        log.info("设备列表导出成功，共{}条", voList.size());
    }

    public Map<String, Object> importDevices(MultipartFile file) throws IOException {
        DeviceExcelListener listener = new DeviceExcelListener(deviceRepository);

        EasyExcel.read(file.getInputStream(), DeviceExcelVO.class, listener)
                .sheet()
                .headRowNumber(1)
                .doRead();

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", listener.getSuccessCount());
        result.put("failCount", listener.getFailCount());
        result.put("errors", listener.getErrors());

        log.info("设备列表导入完成，成功{}条，失败{}条",
                listener.getSuccessCount(), listener.getFailCount());

        return result;
    }

    public void exportTemplate(HttpServletResponse response) throws IOException {
        List<DeviceExcelVO> templateData = new ArrayList<>();

        DeviceExcelVO sample1 = new DeviceExcelVO();
        sample1.setDeviceId("sensor_temp_001");
        sample1.setName("客厅温度传感器");
        sample1.setType("sensor_temp");
        sample1.setRoom("客厅");
        sample1.setProtocol("MQTT");
        sample1.setLocation("客厅-吊顶");
        sample1.setOnlineStatus("在线");
        templateData.add(sample1);

        DeviceExcelVO sample2 = new DeviceExcelVO();
        sample2.setDeviceId("aircon_living_001");
        sample2.setName("客厅空调");
        sample2.setType("aircon");
        sample2.setRoom("客厅");
        sample2.setProtocol("MQTT");
        sample2.setLocation("客厅-墙面");
        sample2.setOnlineStatus("离线");
        templateData.add(sample2);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("设备导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), DeviceExcelVO.class)
                .sheet("设备列表")
                .doWrite(templateData);
    }
}
