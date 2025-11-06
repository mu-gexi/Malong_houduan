package org.example.weiwang.Controller.counter;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.weiwang.Config.Config;
import org.example.weiwang.mysql.DB3;
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
 * 汇总光伏、储能、变压器的充放电量、电费及尖峰平谷分时段电量
 * 汇总结果以设备编号形式（非中文名称）写入数据库
 */
@RestController
public class calculate3 {

    @Scheduled(cron = "0 15 0 * * *")
    public void calculate2() {
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

    // 手动计算历史日期数据
    @GetMapping("/calculate3/history")
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
            run(dayStart, dayEnd);
        }
    }

    public void run(LocalDateTime start, LocalDateTime end ) {
        try {
            DB3 db = new DB3();

            // 三类设备汇总对象
            DeviceStats guangStats = new DeviceStats(); // 光伏
            DeviceStats chuStats = new DeviceStats();   // 储能
            DeviceStats bianStats = new DeviceStats();  // 变压器

            // 电价时段定义（尖峰平谷）
            List<TimeRangeFactor> factors = Arrays.asList(
                    new TimeRangeFactor(LocalTime.of(0, 0), LocalTime.of(8, 0), 0.28576875, "谷"),
                    new TimeRangeFactor(LocalTime.of(8, 0), LocalTime.of(10, 0), 0.70676875, "平"),
                    new TimeRangeFactor(LocalTime.of(10, 0), LocalTime.of(12, 0), 1.18216875, "峰"),
                    new TimeRangeFactor(LocalTime.of(12, 0), LocalTime.of(14, 0), 0.70676875, "平"),
                    new TimeRangeFactor(LocalTime.of(14, 0), LocalTime.of(19, 0), 1.18216875, "峰"),
                    new TimeRangeFactor(LocalTime.of(19, 0), LocalTime.of(23, 59), 0.70676875, "平")
                    // 当前无尖时段，但保留结构
            );

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            // 遍历所有设备类型
            for (String devices : Config.calculate) {
                JSONArray obj = db.calculate2(devices, start.toString(), end.toString());
                if (obj == null || obj.isEmpty()) {
                    System.out.println("【" + devices + "】无数据，跳过。");
                    continue;
                }

                for (int i = 0; i < obj.size(); i++) {
                    JSONObject row = obj.getJSONObject(i);
                    String equipmentCode = row.getString("equipmentCode");
                    String collectTime = row.getString("collectTime");
                    Double recharge = row.getDouble("recharge");       // 充电量
                    Double disrecharge = row.getDouble("disrecharge"); // 放电量
                    LocalTime time = LocalDateTime.parse(collectTime, fmt).toLocalTime();

                    // 匹配电价时段
                    for (TimeRangeFactor f : factors) {
                        if (!time.isBefore(f.start) && time.isBefore(f.end)) {

                            // 光伏：无放电
                            if (Arrays.asList(Config.guang).contains(equipmentCode)) {
                                disrecharge = 0.0;
                            }

                            // 变压器：负功率视为上网电量（取正）
                            if (Arrays.asList(Config.bian2).contains(equipmentCode) && disrecharge < 0) {
                                disrecharge = -disrecharge;
                            }

                            // 计算收益与成本
                            double earnings = recharge * f.factor;
                            double cost = disrecharge * f.factor;

                            // 按类型归类
                            DeviceStats stats;
                            if (Arrays.asList(Config.guang).contains(equipmentCode)) stats = guangStats;// 光伏
                            else if (Arrays.asList(Config.chuneng).contains(equipmentCode)) stats = chuStats;// 储能
                            else if (Arrays.asList(Config.bian2).contains(equipmentCode)) stats = bianStats;//市电
                            else continue;

                            // 累加总量
                            stats.recharge += recharge;// 充电量
                            stats.disrecharge += disrecharge;// 放电量
                            stats.earnings += earnings;// 收益
                            stats.cost += cost;// 成本

                            // 尖峰平谷电量累计（充电量）
                            switch (f.type) {
                                case "尖": stats.peak += recharge; stats.peak1 += disrecharge; break;
                                case "峰": stats.height += recharge; stats.height1 += disrecharge; break;
                                case "平": stats.flat += recharge; stats.flat1 += disrecharge; break;
                                case "谷": stats.valley += recharge; stats.valley1 += disrecharge; break;
                            }
                            break;
                        }
                    }
                }
            }

            // 写入数据库（三条记录）
            writeToDB(db, "0000000001", start.toString(), guangStats);// 光伏
            writeToDB(db, "0000000002", start.toString(), chuStats);// 储能
            writeToDB(db, "0000000000", start.toString(), bianStats);// 变压器

            db.close();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("计算出错：" + e.getMessage());
        }
    }

    /**
     * 封装写入数据库
     */
    private void writeToDB(DB3 db, String equipmentCode, String date, DeviceStats stats) {
        db.deposit4(equipmentCode, date,
                String.valueOf(stats.recharge),
                String.valueOf(stats.disrecharge),
                String.valueOf(stats.earnings),
                String.valueOf(stats.cost),
                String.valueOf(stats.peak),
                String.valueOf(stats.height),
                String.valueOf(stats.flat),
                String.valueOf(stats.valley),
                String.valueOf(stats.peak1),
                String.valueOf(stats.height1),
                String.valueOf(stats.flat1),
                String.valueOf(stats.valley1)
        );
    }

    /**
     * 电价时段定义类
     */
    private static class TimeRangeFactor {
        LocalTime start;
        LocalTime end;
        double factor;
        String type; // 尖/峰/平/谷

        TimeRangeFactor(LocalTime s, LocalTime e, double f, String t) {
            this.start = s;
            this.end = e;
            this.factor = f;
            this.type = t;
        }
    }

    /**
     * 统计汇总结构体
     */
    private static class DeviceStats {
        double recharge = 0.0;     // 充电量
        double disrecharge = 0.0;  // 放电量
        double earnings = 0.0;     // 收益
        double cost = 0.0;         // 成本
        double peak = 0.0;    // 充电尖
        double height = 0.0; // 充电峰
        double flat = 0.0;   // 充电平
        double valley = 0.0; // 充电谷
        double peak1 = 0.0;    // 放电尖
        double height1 = 0.0;  // 放电峰
        double flat1 = 0.0;    // 放电平
        double valley1 = 0.0;  // 放电谷
    }
}
