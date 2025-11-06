package org.example.weiwang.Controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.weiwang.Config.Config;
import org.example.weiwang.mysql.DB5;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * 每月电表电量、电费汇总控制器
 * 汇总尖峰平谷与总电量、电费，并写入数据库
 */
@RestController
public class calculate8 {

    /**
     * 手动计算历史月份数据
     * @param date 月份字符串（格式：yyyy-MM）
     * @return 所有设备的月汇总结果
     */
    @GetMapping("/calculate8/history")
    public JSONArray calculateHistory(@RequestParam String date) {
        return run(date);
    }

    /**
     * 定时任务（可开启）
     */
    @Scheduled(cron = "1 0 0 * * ?")
    public void calculate() {
        YearMonth currentMonth = YearMonth.now();
        run(currentMonth.toString());
    }

    /**
     * 按月计算所有设备的尖峰平谷电量与电费汇总
     * @param date yyyy-MM
     * @return JSONArray 汇总结果
     */
    public JSONArray run(String date) {
        DB5 db = new DB5();
        JSONArray resultArray = new JSONArray();

        // 遍历配置的所有设备编号
        for (String deviceCode : Config.waizu) {
            try {
                JSONArray data = db.monthdata(deviceCode, date);
                if (data == null || data.isEmpty()) {
                    continue;
                }

                double sharpPeakPowerSum = 0.0;//累计尖电量
                double peakPowerSum = 0.0;//累计峰电量
                double flatPowerSum = 0.0;//累计平电量
                double valleyPowerSum = 0.0;//累计谷电量
                double totalPowerSum = 0.0;//累计总电量

                double sharpPeakCostSum = 0.0;//累计尖电费
                double peakCostSum = 0.0;//累计峰电费
                double flatCostSum = 0.0;//累计平电费
                double valleyCostSum = 0.0;//累计谷电费
                double totalCostSum = 0.0;//累计总电费

                // 遍历所有记录累加
                for (int i = 0; i < data.size(); i++) {
                    JSONObject obj = data.getJSONObject(i);
                    sharpPeakPowerSum += obj.getDoubleValue("sharp_peakPower");
                    peakPowerSum += obj.getDoubleValue("peakPower");
                    flatPowerSum += obj.getDoubleValue("flatPower");
                    valleyPowerSum += obj.getDoubleValue("valleyPower");
                    totalPowerSum += obj.getDoubleValue("totalPower");

                    sharpPeakCostSum += obj.getDoubleValue("sharp_peakCost");
                    peakCostSum += obj.getDoubleValue("peakCost");
                    flatCostSum += obj.getDoubleValue("flatCost");
                    valleyCostSum += obj.getDoubleValue("valleyCost");
                    totalCostSum += obj.getDoubleValue("totalCost");
                }

                // 汇总对象
                JSONObject summary = new JSONObject();
                summary.put("equipmentCode", deviceCode);
                summary.put("month", date);
                summary.put("sharp_peakPower", sharpPeakPowerSum);
                summary.put("peakPower", peakPowerSum);
                summary.put("flatPower", flatPowerSum);
                summary.put("valleyPower", valleyPowerSum);
                summary.put("totalPower", totalPowerSum);
                summary.put("sharp_peakCost", sharpPeakCostSum);
                summary.put("peakCost", peakCostSum);
                summary.put("flatCost", flatCostSum);
                summary.put("valleyCost", valleyCostSum);
                summary.put("totalCost", totalCostSum);

                // 插入数据库
                db.insertMonth(deviceCode, date,
                        sharpPeakPowerSum, peakPowerSum, flatPowerSum, valleyPowerSum, totalPowerSum,
                        sharpPeakCostSum, peakCostSum, flatCostSum, valleyCostSum, totalCostSum);

                resultArray.add(summary);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return resultArray;
    }
}
