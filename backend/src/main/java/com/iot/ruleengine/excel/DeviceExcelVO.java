package com.iot.ruleengine.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

@Data
@HeadRowHeight(20)
@ContentRowHeight(18)
public class DeviceExcelVO {

    @ExcelProperty(index = 0, value = "设备ID")
    @ColumnWidth(20)
    private String deviceId;

    @ExcelProperty(index = 1, value = "设备名称")
    @ColumnWidth(25)
    private String name;

    @ExcelProperty(index = 2, value = "设备类型")
    @ColumnWidth(15)
    private String type;

    @ExcelProperty(index = 3, value = "所属房间")
    @ColumnWidth(20)
    private String room;

    @ExcelProperty(index = 4, value = "通信协议")
    @ColumnWidth(15)
    private String protocol;

    @ExcelProperty(index = 5, value = "安装位置")
    @ColumnWidth(25)
    private String location;

    @ExcelProperty(index = 6, value = "在线状态")
    @ColumnWidth(12)
    private String onlineStatus;
}
