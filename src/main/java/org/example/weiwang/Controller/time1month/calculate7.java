package org.example.weiwang.Controller.time1month;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.weiwang.Config.Config;
import org.example.weiwang.mysql.DB3;
import org.example.weiwang.mysql.DB4;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 每日定时计算任务：
 * 按设备编号（equipmentCode）统计尖峰平谷功率、总功率及对应费用
 */
@RestController
public class calculate7 {

    // 每日00:15执行当天统计（统计前一天数据）
    @Scheduled(cron = "0 15 0 * * *")
    public void calculateDaily() {
//        LocalDate today = LocalDate.now();
//        LocalDate yesterday = today.minusDays(1);
//        run(yesterday);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();// 当天00:00:00
        // 昨天的开始时间：今天00:00:00 减去 1 天
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        // 昨天的结束时间：昨天23:59:59
        LocalDateTime yesterdayEnd = yesterdayStart.plusDays(1).minusSeconds(1);
        run(yesterdayStart, yesterdayEnd);
    }

    // 历史数据补算接口
    @GetMapping("/calculate7/history")
    public void calculateHistory(@RequestParam String start, @RequestParam String end) {
        // 定义日期格式化器（仅日期）
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 解析输入的起止日期字符串为 LocalDate
        LocalDate startDate = LocalDate.parse(start, dateFormatter);
        LocalDate endDate = LocalDate.parse(end, dateFormatter);

        // 如果开始日期晚于结束日期，则直接返回错误
        if (startDate.isAfter(endDate)) {
            System.out.println("开始日期不能晚于结束日期");
            return;
        }

        // 逐天处理
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDateTime dayStart = date.atStartOfDay();             // 当天 00:00:00
            LocalDateTime dayEnd = date.atTime(23, 59, 59);           // 当天 23:59:59

            System.out.println("正在计算日期：" + date);
            run(dayStart, dayEnd);
        }
    }



    public void run(LocalDateTime start, LocalDateTime end) {
        try {
            DB3 db = new DB3();
            DB4 calculate = new DB4();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDate targetDate = start.toLocalDate(); // 目标日期（统计日期）


            // 尖峰平谷时段及电价系数定义（尖、峰、平、谷）
            List<TimeRangeFactor> factors = Arrays.asList(
                    new TimeRangeFactor(LocalTime.of(0, 0), LocalTime.of(8, 0), 0.28576875, "谷"),
                    new TimeRangeFactor(LocalTime.of(8, 0), LocalTime.of(10, 0), 0.70676875, "平"),
                    new TimeRangeFactor(LocalTime.of(10, 0), LocalTime.of(12, 0), 1.18216875, "峰"),
                    new TimeRangeFactor(LocalTime.of(12, 0), LocalTime.of(14, 0), 0.70676875, "平"),
                    new TimeRangeFactor(LocalTime.of(14, 0), LocalTime.of(19, 0), 1.18216875, "峰"),
                    new TimeRangeFactor(LocalTime.of(19, 0), LocalTime.of(23, 59), 0.70676875, "平")
            );

            // 存储每个设备的统计数据（key: equipmentCode, value: 设备统计信息）
            Map<String, DeviceStats> deviceStatsMap = new HashMap<>();

            // 遍历所有设备类型
            for (String deviceType : Config.waizu) {
                JSONArray dataList = db.calculate2(deviceType,start.toString(),end.toString());
                if (dataList == null || dataList.isEmpty()) {
                    System.out.println("【设备类型：" + deviceType + "】无数据，跳过。");
                    continue;
                }

                // 处理每条设备数据
                for (int i = 0; i < dataList.size(); i++) {
                    JSONObject row = dataList.getJSONObject(i);
                    String equipmentCode = row.getString("equipmentCode");
                    String collectTime = row.getString("collectTime");
                    // 计算实际功率（用getDoubleValue避免null，返回0.0而非null）
                    double recharge = row.getDoubleValue("recharge");
                    double power = row.getDoubleValue("disrecharge");
//                    double power = recharge - disrecharge;

                    // 解析采集时间的“时分”部分，用于匹配时段
                    LocalTime time = LocalDateTime.parse(collectTime, fmt).toLocalTime();

                    // 匹配当前功率所属的尖峰平谷时段
                    for (TimeRangeFactor factor : factors) {
                        if (!time.isBefore(factor.start) && time.isBefore(factor.end)) {
                            // 初始化或获取该设备的统计对象
                            DeviceStats stats = deviceStatsMap.getOrDefault(equipmentCode, new DeviceStats());
                            deviceStatsMap.put(equipmentCode, stats);

                            // 功率取绝对值（避免正负抵消，根据业务确认是否需要）
//                            double powerAbs = Math.abs(power);
                            // 计算当前时段的费用（功率 × 电价系数）
                            double periodCost = power * factor.factor;

                            // 按时段累加功率和费用（移除小数保留逻辑）
                            switch (factor.type) {
                                case "尖":
                                    stats.sharp_peakPower += power;
                                    stats.sharp_peakCost += periodCost;
                                    break;
                                case "峰":
                                    stats.peakPower += power;
                                    stats.peakCost += periodCost;
                                    break;
                                case "平":
                                    stats.flatPower += power;
                                    stats.flatCost += periodCost;
                                    break;
                                case "谷":
                                    stats.valleyPower += power;
                                    stats.valleyCost += periodCost;
                                    break;
                            }

                            // 累加总功率和总费用
                            stats.totalPower += power;
                            stats.totalCost += periodCost;
                            break; // 匹配到时段后跳出循环，避免重复统计
                        }
                    }
                }
            }

            // 批量写入数据库（每个设备一条记录）
            for (Map.Entry<String, DeviceStats> entry : deviceStatsMap.entrySet()) {
                String equipmentCode = entry.getKey();
                DeviceStats stats = entry.getValue();
                // 注意：collectTime传入“目标日期”（如2025-10-23），与SQL的WHERE条件匹配
                writeToDB(calculate, equipmentCode, targetDate.toString(), stats);
            }

            // 关闭数据库连接
            db.close();
            calculate.close(); // 补充DB4连接关闭，避免资源泄漏
            System.out.println("【" + targetDate + "】设备统计完成，共" + deviceStatsMap.size() + "个设备");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("计算出错：" + e.getMessage());
        }
    }

    /**
     * 写入数据库：参数顺序完全对齐waizu方法和SQL
     */
    private void writeToDB(DB4 db, String equipmentCode, String collectTime, DeviceStats stats) {
        db.waizu(
                equipmentCode,          // 1. 设备编号
                collectTime,            // 2. 统计日期
                stats.sharp_peakPower,  // 3. 尖时段功率
                stats.peakPower,        // 4. 峰时段功率
                stats.flatPower,        // 5. 平时段功率
                stats.valleyPower,      // 6. 谷时段功率
                stats.totalPower,       // 7. 总功率
                stats.sharp_peakCost,   // 8. 尖时段费用
                stats.peakCost,         // 9. 峰时段费用
                stats.flatCost,         // 10. 平时段费用
                stats.totalCost,        // 11. 总费用
                stats.valleyCost        // 12. 谷时段费用
        );
    }

    /**
     * 电价时段定义类：封装时段起止时间、电价系数、时段类型
     */
    private static class TimeRangeFactor {
        LocalTime start;  // 时段开始时间
        LocalTime end;    // 时段结束时间
        double factor;    // 电价系数
        String type;      // 时段类型（尖/峰/平/谷）

        TimeRangeFactor(LocalTime start, LocalTime end, double factor, String type) {
            this.start = start;
            this.end = end;
            this.factor = factor;
            this.type = type;
        }
    }

    /**
     * 设备统计结构体：存储尖峰平谷功率和费用
     */
    private static class DeviceStats {
        // 功率字段（单位：kWh）
        double sharp_peakPower = 0.0; // 尖时段功率
        double peakPower = 0.0;       // 峰时段功率
        double flatPower = 0.0;       // 平时段功率
        double valleyPower = 0.0;     // 谷时段功率
        double totalPower = 0.0;      // 总功率

        // 费用字段（单位：元）
        double sharp_peakCost = 0.0;  // 尖时段费用
        double peakCost = 0.0;        // 峰时段费用
        double flatCost = 0.0;        // 平时段费用
        double valleyCost = 0.0;      // 谷时段费用
        double totalCost = 0.0;       // 总费用
    }
}