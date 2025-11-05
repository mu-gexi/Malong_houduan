package org.example.weiwang.Config;

import org.example.weiwang.mysql.DB4;
import org.example.weiwang.mysql.DB5;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 每月自动插入【Config.waizu中15个设备 + 当月所有日期】到waizu表
 */
@Component
public class foundDate {

    // 定时规则：每月1日00:10执行（提前生成当月设备-日期基础记录）
    @Scheduled(cron = "1 0 0 1 * ?")
    public void insertWaizuDeviceDates() {
        try {
            // 1. 生成当月所有日期（格式：yyyy-MM-dd，如2025-10-01）
            LocalDate today = LocalDate.now();
            LocalDate firstDay = today.withDayOfMonth(1);
            LocalDate lastDay = today.withDayOfMonth(firstDay.lengthOfMonth());
            List<String> monthDates = generateAllDates(firstDay, lastDay);

            // 2. 读取Config.waizu中的15个设备编号（直接复用现有配置）
            String[] waizuDevices = Config.waizu;
            // 校验：确保Config.waizu有值（避免空指针）
            if (waizuDevices == null || waizuDevices.length == 0) {
                System.out.println("Config.waizu未配置设备编号，终止任务");
                return;
            }

            // 3. 循环插入：15个设备 × 当月日期数 = 总记录数
            DB4 db = new DB4();
            int successCount = 0;
            for (String deviceCode : waizuDevices) {
                // 过滤无效设备编号（避免空值插入）
                if (deviceCode == null || deviceCode.trim().isEmpty()) {
                    System.out.println("跳过空设备编号，继续下一个");
                    continue;
                }
                // 为当前设备插入当月所有日期
                for (String date : monthDates) {
                    db.insertWaizuDeviceDate(deviceCode.trim(), date);
                    successCount++;
                }
            }

            // 4. 关闭连接+打印结果（清晰展示插入情况）
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("插入失败：" + e.getMessage());
        }
    }

    /**
     * 生成从start到end的所有日期（含首尾）
     */
    private List<String> generateAllDates(LocalDate start, LocalDate end) {
        List<String> dateList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate current = start;
        while (!current.isAfter(end)) {
            dateList.add(current.format(formatter));
            current = current.plusDays(1);
        }
        return dateList;
    }

    //自动创建每个表号的每年的12个月份-------------------------------------------
    // 每年1月1日凌晨00:00:01点执行
    @Scheduled(cron = "1 0 0 1 1 *")
    public void insertWaizuDeviceDatesYearly() {
        int year = LocalDate.now().getYear();
        try {
            DB5 db = new DB5();
            for (String device : Config.waizu) {
                if (device == null || device.trim().isEmpty()) continue;
                for (int m = 1; m <= 12; m++) {
                    String ym = String.format("%04d-%02d", year, m);
                    db.insertWaizuDeviceDate(device.trim(), ym);
                }
            }
            db.close();
            System.out.println("外租设备月份记录生成完成：" + year);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}