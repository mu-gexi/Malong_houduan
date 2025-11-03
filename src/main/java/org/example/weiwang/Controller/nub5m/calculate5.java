package org.example.weiwang.Controller.nub5m;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.weiwang.Config.Config;
import org.example.weiwang.mysql.DB4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 每日定时计算（对齐时间间隔、预留计算方法）
 */
@RestController
public class calculate5 {
    private static final Logger logger = LoggerFactory.getLogger(calculate5.class);

    @Scheduled(cron = "0 20 0 * * *")
    public void scheduleTask() {
//        LocalDate yesterday = LocalDate.now().minusDays(1);
//        run(yesterday.toString());
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();// 当天00:00:00
        // 昨天的开始时间：今天00:00:00 减去 1 天
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        // 昨天的结束时间：昨天23:59:59
        LocalDateTime yesterdayEnd = yesterdayStart.plusDays(1).minusSeconds(1);
        run(yesterdayStart, yesterdayEnd);
    }

    @GetMapping("/calculate5/history")
    public void calculateHistory(@RequestParam String start, @RequestParam String end) {
//          LocalDate date = LocalDate.parse(dateStr);
//          run(String.valueOf(date));
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


    public void run(LocalDateTime start, LocalDateTime end) {
        DB4 db = new DB4();
        JSONArray data = db.queryLoad(start.toString(),end.toString());


        Map<String, List<JSONObject>> grouped = new HashMap<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject obj = data.getJSONObject(i);
            String time = obj.getString("collectTime");
            grouped.computeIfAbsent(time, k -> new ArrayList<>()).add(obj);
        }


        for (String time : grouped.keySet()) {
            List<JSONObject> list = grouped.get(time);
            double Grecharge = 0; // 光伏发电
            double Crecharge = 0; // 储能充电
            double Cdisrecharge = 0; // 储能放电
            double Bdisrecharge = 0; // 变压器下网
            double load = 0;
            for (JSONObject obj : list) {
                String code = obj.getString("equipmentCode");

                if(Arrays.asList(Config.zhujian1).contains(code)){
                    if(code.equals("08102100002956"))
                      Grecharge += obj.getDoubleValue("recharge");
                    if(code.equals("08102100002922")) {
                        Crecharge += obj.getDoubleValue("recharge");
                        Cdisrecharge += obj.getDoubleValue("disrecharge");
                    }
                    if(code.equals("000000001")) {
                        Bdisrecharge += obj.getDoubleValue("disrecharge");
                        if (Bdisrecharge < 0) {
                            Bdisrecharge = 0;
                        }
                    }
                }

            }
            load = Grecharge + Crecharge + Bdisrecharge - Cdisrecharge;
            db.insertLoad("9999999999", time, load);//插入负载编号、时间、数据

        }
    }





}
