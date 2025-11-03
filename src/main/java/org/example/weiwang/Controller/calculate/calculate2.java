package org.example.weiwang.Controller.calculate;

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

@RestController
public class calculate2 {

    @Scheduled(cron="0 10 0 * * *")
    public void calculate2(){
        for(String devices: Config.calculate){
            run(devices);
        }
    }



    //运行calculate2方法，计算每5分钟的电量
    public void run(String devices) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();// 当天00:00:00
            // 昨天的开始时间：今天00:00:00 减去 1 天
            LocalDateTime yesterdayStart = todayStart.minusDays(1);
            // 昨天的结束时间：昨天23:59:59
            LocalDateTime yesterdayEnd = yesterdayStart.plusDays(1).minusSeconds(1);

            DB3 db = new DB3();
            JSONArray obj = db.calculate2(devices, yesterdayStart.toString(),yesterdayEnd.toString());

            if (obj == null || obj.isEmpty()) {
                System.out.println("未查到任何数据，跳过处理");
                db.close();
                return;
            }

            List<TimeRangeFactor> factors = new ArrayList<>();
//            factors.add(new TimeRangeFactor(LocalTime.of(0, 0), LocalTime.of(8, 0),  0.28576875));
//            factors.add(new TimeRangeFactor(LocalTime.of(8, 0), LocalTime.of(10, 0),  0.70696875));
//            factors.add(new TimeRangeFactor(LocalTime.of(10, 0), LocalTime.of(11, 0), 1.18246875));
//            factors.add(new TimeRangeFactor(LocalTime.of(11,0), LocalTime.of(12, 0),  1.47126875));
//            factors.add(new TimeRangeFactor(LocalTime.of(12,0), LocalTime.of(14, 0),  0.70696875));
//            factors.add(new TimeRangeFactor(LocalTime.of(14,0), LocalTime.of(15, 0), 1.18246875));
//            factors.add(new TimeRangeFactor(LocalTime.of(15,0), LocalTime.of(17, 0),  1.47126875));
//            factors.add(new TimeRangeFactor(LocalTime.of(17,0), LocalTime.of(19, 0), 1.18246875));
//            factors.add(new TimeRangeFactor(LocalTime.of(17,0), LocalTime.of(23, 59),  0.70696875));

            factors.add(new TimeRangeFactor(LocalTime.of(0, 0), LocalTime.of(8, 0),  0.28576875));
            factors.add(new TimeRangeFactor(LocalTime.of(8, 0), LocalTime.of(10, 0),  0.70676875));
            factors.add(new TimeRangeFactor(LocalTime.of(10, 0), LocalTime.of(12, 0),  1.18216875));
            factors.add(new TimeRangeFactor(LocalTime.of(12, 0), LocalTime.of(14, 0),  0.70676875));
            factors.add(new TimeRangeFactor(LocalTime.of(14, 0), LocalTime.of(19, 0),  1.18216875));
            factors.add(new TimeRangeFactor(LocalTime.of(19, 0), LocalTime.of(23, 59),  0.70676875));






            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Map<String, DeviceStats> statsMap = new HashMap<>();

            for (int i = 0; i < obj.size(); i++) {
                JSONObject row = obj.getJSONObject(i);
                String equipmentCode = row.getString("equipmentCode");
                String collectTime = row.getString("collectTime");
                Double recharge = row.getDouble("recharge");
                Double disrecharge = row.getDouble("disrecharge");

                LocalTime time = LocalDateTime.parse(collectTime, fmt).toLocalTime();
                double factor = 1.0;
                double earnings = 0.0;//收益
                double cost = 0.0;//成本
                double energy = 0.0;//上网电量
                double energyCost = 0.0;//上网收益

                DeviceStats stats = statsMap.computeIfAbsent(equipmentCode, k -> new DeviceStats());

                for (TimeRangeFactor f : factors) {
                    if (!time.isBefore(f.start) && time.isBefore(f.end)) {
                        factor = f.factor;

                        // 光伏设备：不计算放电
                        if (Arrays.asList(Config.guang).contains(equipmentCode)) {
                            disrecharge = 0.0;
                        }

                        // 变压器类设备负功率（上网电量统计）
                        // 在此之前加上
                        if (Arrays.asList(Config.bian2).contains(equipmentCode) && disrecharge < 0) {
                            disrecharge = -disrecharge; // 取正数
                            stats.energySum += disrecharge;
                            stats.energyCostSum += disrecharge * 0.45;
                        }


                        // 公共计算（无论设备类型，只计算一次）
                        earnings = recharge * factor;
                        cost = disrecharge * factor;

                        stats.value1Sum += recharge;
                        stats.value2Sum += disrecharge;
                        stats.earningsSum += earnings;
                        stats.costSum += cost;
                    }
                }


            }

            // 批量写入数据库
            for (Map.Entry<String, DeviceStats> entry : statsMap.entrySet()) {
                String code = entry.getKey();
                DeviceStats stats = entry.getValue();

                db.deposit2(
                        code,
                        yesterday.toString(),
                        String.valueOf(stats.value1Sum),
                        String.valueOf(stats.value2Sum),
                        String.valueOf(stats.earningsSum),
                        String.valueOf(stats.costSum),
                        String.valueOf(stats.energySum),
                        String.valueOf(stats.energyCostSum)
                );
            }

            db.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("运行过程中发生错误：" + e.getMessage());
        }
    }

    private static class TimeRangeFactor {
        LocalTime start;
        LocalTime end;
        double factor;
        TimeRangeFactor(LocalTime s, LocalTime e, double f){
            this.start = s;
            this.end = e;
            this.factor = f;
        }
    }
    private static class DeviceStats {
        double value1Sum = 0.0;
        double value2Sum = 0.0;
        double earningsSum = 0.0;
        double costSum = 0.0;
        double energySum = 0.0;
        double energyCostSum = 0.0;
    }


    @GetMapping("/historyCount")
    public String manualCalculate(@RequestParam String start, @RequestParam String end) {
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);

            if (startDate.isAfter(endDate)) {
                return "❌ 开始日期不能晚于结束日期";
            }


            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                for (String device : Config.calculate) {
                    LocalDateTime dayStart = date.atStartOfDay();             // 当天 00:00:00
                    LocalDateTime dayEnd = date.atTime(23, 59, 59);           // 当天 23:59:59
                    run1(device, dayStart, dayEnd);
                }
            }
            return "✅ 指定时间段计算完成：" + start + " 至 " + end;
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 执行出错：" + e.getMessage();
        }
    }




    public void run1(String devices, LocalDateTime daystart,LocalDateTime dayend) {
        try {
            DB3 db = new DB3();
            JSONArray obj = db.calculate2(devices, daystart.toString(),dayend.toString());  // 使用输入日期

            if (obj == null || obj.isEmpty()) {
                System.out.println("未查到任何数据，跳过处理：" + daystart);
                db.close();
                return;
            }

            // 你的原逻辑完全不动 ↓
            List<TimeRangeFactor> factors = new ArrayList<>();
            factors.add(new TimeRangeFactor(LocalTime.of(0, 0), LocalTime.of(8, 0), 0.28576875));
            factors.add(new TimeRangeFactor(LocalTime.of(8, 0), LocalTime.of(10, 0), 0.70676875));
            factors.add(new TimeRangeFactor(LocalTime.of(10, 0), LocalTime.of(12, 0), 1.18216875));
            factors.add(new TimeRangeFactor(LocalTime.of(12, 0), LocalTime.of(14, 0), 0.70676875));
            factors.add(new TimeRangeFactor(LocalTime.of(14, 0), LocalTime.of(19, 0), 1.18216875));
            factors.add(new TimeRangeFactor(LocalTime.of(19, 0), LocalTime.of(23, 59), 0.70676875));

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Map<String, DeviceStats> statsMap = new HashMap<>();

            for (int i = 0; i < obj.size(); i++) {
                JSONObject row = obj.getJSONObject(i);
                String equipmentCode = row.getString("equipmentCode");
                String collectTime = row.getString("collectTime");
                Double recharge = row.getDouble("recharge");
                Double disrecharge = row.getDouble("disrecharge");

                LocalTime time = LocalDateTime.parse(collectTime, fmt).toLocalTime();
                double factor = 1.0;
                double earnings = 0.0;
                double cost = 0.0;



                DeviceStats stats = statsMap.computeIfAbsent(equipmentCode, k -> new DeviceStats());

                for (TimeRangeFactor f : factors) {
                    if (!time.isBefore(f.start) && time.isBefore(f.end)) {
                        factor = f.factor;

                        // 光伏设备：不计算放电
                        if (Arrays.asList(Config.guang).contains(equipmentCode)) {
                            disrecharge = 0.0;
                        }

                        // 变压器类设备负功率（上网电量统计）
                        // 在此之前加上
                        if (Arrays.asList(Config.bian2).contains(equipmentCode) && disrecharge < 0) {
                            disrecharge = -disrecharge; // 取正数
                            stats.energySum += disrecharge;
                            stats.energyCostSum += disrecharge * 0.45;
                        }


                        // 公共计算（无论设备类型，只计算一次）
                        earnings = recharge * factor;
                        cost = disrecharge * factor;

                        stats.value1Sum += recharge;
                        stats.value2Sum += disrecharge;
                        stats.earningsSum += earnings;
                        stats.costSum += cost;
                    }
                }
            }

            // 写入数据库
            for (Map.Entry<String, DeviceStats> entry : statsMap.entrySet()) {
                String code = entry.getKey();
                DeviceStats stats = entry.getValue();


                db.deposit2(
                        code,
                        daystart.toString(),
                        String.valueOf(stats.value1Sum),
                        String.valueOf(stats.value2Sum),
                        String.valueOf(stats.earningsSum),
                        String.valueOf(stats.costSum),
                        String.valueOf(stats.energySum),
                        String.valueOf(stats.energyCostSum)
                );
            }

            db.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ 运行过程中发生错误：" + e.getMessage());
        }
    }



}
