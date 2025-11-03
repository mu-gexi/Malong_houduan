package org.example.weiwang.Controller.nub5m;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.weiwang.Config.Config;
import org.example.weiwang.mysql.DB3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
public class calculate1 {
    private static final Logger logger = LoggerFactory.getLogger(calculate1.class);

    /** 改为昨天 00:00 起算 */
    private LocalDateTime lastExecutionTime = LocalDate.now().minusDays(1).atStartOfDay();


    @Scheduled(cron="0 5 0 * * *")
    public void calculate(){
        // 昨天的起止时间
        LocalDate yesterday = LocalDate.now().minusDays(1);//昨天的日期
        LocalDateTime dayStart = yesterday.atStartOfDay();//昨的00:00
        LocalDateTime dayEnd = yesterday.plusDays(1).atStartOfDay();//昨的23:59:59


        // 对 lastExecutionTime 做 5 分钟对齐
        LocalDateTime runStart = alignToFiveMinutesFloor(
                lastExecutionTime.isBefore(dayStart) ? dayStart : lastExecutionTime
        );
        LocalDateTime runEnd = alignToFiveMinutesFloor(
                LocalDateTime.now().isAfter(dayEnd) ? dayEnd : LocalDateTime.now()
        );

        if (!runEnd.isAfter(runStart)) {
            logger.info("昨天无新时间段需要处理: {} → {}", runStart, runEnd);
            return;
        }

        for (String device : Config.mixed) {
            try {
                // 传入昨天日期范围
                run(device, runStart, runEnd);
                logger.info("昨天数据定时任务：{} 完成（{} → {}）", device, runStart, runEnd);
            } catch (Exception e) {
                logger.error("设备 {} 处理异常: {}", device, e.getMessage(), e);
            }
        }

        for(String device :Config.bian2){
            try {
                // 输入昨天的起止时间
                run1(device, yesterday.toString());

                logger.info("昨天数据定时任务：{} 完成（{} → {}）", device, yesterday);
            } catch (Exception e) {
                logger.error("设备 {} 处理异常: {}", device, e.getMessage(), e);
            }
        }
        lastExecutionTime = runEnd;
    }

    // 运行calculate方法，计算每5分钟的电量
    public void run(String devices, LocalDateTime startTime, LocalDateTime endTime) {
        DB3 db = new DB3();
        JSONArray data = db.calculate(devices, startTime, endTime);
        List<JSONObject> rows = new ArrayList<>();
        for (Object obj : data)
            if (obj instanceof JSONObject) rows.add((JSONObject) obj);
        if (rows.isEmpty()) {
            logger.warn("未找到设备 {} 数据", devices);
            return;
        }

        Map<LocalDateTime, Double> posMap = new HashMap<>();
        Map<LocalDateTime, Double> revMap = new HashMap<>();

        for (JSONObject row : rows) {
            LocalDateTime ct = parseToLocalDateTime(row.get("collectTime"));
            if (ct == null || ct.isBefore(startTime) || ct.isAfter(endTime)) continue;
            Double p = safeGetDouble(row, "positiveActive");
            Double r = safeGetDouble(row, "reverseActive");
            if (p != null) posMap.put(ct, p);
            if (r != null) revMap.put(ct, r);
        }

        fillMissing(startTime, endTime, posMap);
        fillMissing(startTime, endTime, revMap);
        replaceZerosWithNeighborAverage(posMap);
        replaceZerosWithNeighborAverage(revMap);

        // === 对齐所有时间并排序 ===
        Set<LocalDateTime> allTimes = new HashSet<>(posMap.keySet());//获取所有时间
        allTimes.addAll(revMap.keySet());//添加所有时间
        List<LocalDateTime> timeList = new ArrayList<>(allTimes);
        Collections.sort(timeList);

        List<Map.Entry<LocalDateTime, Double>> posList = posMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();//获取所有时间对应的正向数据
        List<Map.Entry<LocalDateTime, Double>> revList = revMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();

        for (int i = 1; i < timeList.size(); i++) {
            LocalDateTime prevTime = timeList.get(i - 1);
            LocalDateTime currTime = timeList.get(i);

            Double prevPos = posMap.getOrDefault(prevTime, 0.0);
            Double currPos = posMap.getOrDefault(currTime, 0.0);
            Double prevRev = revMap.getOrDefault(prevTime, 0.0);
            Double currRev = revMap.getOrDefault(currTime, 0.0);

            double diff = currPos >= prevPos ? currPos - prevPos : 0.0;
            double diff1 = currRev >= prevRev ? currRev - prevRev : 0.0;

            if (diff > getMaxAllowedDiff(posList, i)) {
                logger.warn("设备 {} 时间 {} 出现异常正向差值 {}", devices, currTime, diff);
                diff = 0.0;
            }
            if (diff1 > getMaxAllowedDiff(revList, i)) {
                logger.warn("设备 {} 时间 {} 出现异常反向差值 {}", devices, currTime, diff1);
                diff1 = 0.0;
            }

            if (Arrays.asList(Config.guang).contains(devices)) {
                db.deposit(devices, currTime.toString(), diff, diff1);
            } else {
                db.deposit(devices, currTime.toString(), diff1, diff);
            }
        }
    }

    // 计算最大允许差值（3倍于前5个正常差值的平均值）
    private double getMaxAllowedDiff(List<Map.Entry<LocalDateTime, Double>> list, int index) {
        int lookBack = Math.min(5, index);
        double sum = 0;
        int count = 0;

        for (int i = index - 1; i >= index - lookBack; i--) {
            if (i - 1 < 0) break;
            double prev = list.get(i - 1).getValue();
            double curr = list.get(i).getValue();
            double diff = curr - prev;
            if (diff >= 0 && diff < 1000.0) {
                sum += diff;
                count++;
            }
        }

        if (count < 2) return 1000.0;
        return Math.max((sum / count) * 3, 1000.0);
    }


    /** 0 值修正 */
    private void replaceZerosWithNeighborAverage(Map<LocalDateTime, Double> map){
        if (map.isEmpty()) return;

        List<LocalDateTime> times = new ArrayList<>(map.keySet());
        times.sort(Comparator.naturalOrder());

        for (int i = 0; i < times.size(); i++) {
            LocalDateTime key = times.get(i);
            Double value = map.get(key);

            if (value == null || value != 0.0) continue;

            // 找出从当前位置开始的连续零值区间
            int startIdx = i;
            while (i + 1 < times.size() && map.get(times.get(i + 1)) != null && map.get(times.get(i + 1)) == 0.0) {
                i++;
            }

            // 区间结束索引
            int endIdx = i;

            // 查找区间的左有效值
            Double left = null;
            for (int l = startIdx - 1; l >= 0; l--) {
                Double v = map.get(times.get(l));
                if (v != null && v != 0.0) {
                    left = v;
                    break;
                }
            }

            Double right = null;
            for (int r = endIdx + 1; r < times.size(); r++) {
                Double v = map.get(times.get(r));
                if (v != null && v != 0.0) {
                    right = v;
                    break;
                }
            }

            // 如果两边都有有效值，则对整个区间进行线性插值
            if (left != null && right != null) {
                LocalDateTime tStart = times.get(startIdx);
                LocalDateTime tEnd = times.get(endIdx);
                long totalSeconds = Duration.between(tStart, tEnd).getSeconds();// 总时长
                long durationBetween = Duration.between(tStart, tEnd).getSeconds();// 区间时长

                for (int j = startIdx; j <= endIdx; j++) {
                    LocalDateTime tj = times.get(j);
                    long posSeconds = Duration.between(tStart, tj).getSeconds();//
                    double w = (double) posSeconds / totalSeconds;
                    double interpolated = left + (right - left) * w;
                    map.put(tj, interpolated);
                }
            } else {
                // 如果只有一边有值，则填充为该值
                Double fillValue = left != null ? left : (right != null ? right : null);
                if (fillValue != null) {
                    for (int j = startIdx; j <= endIdx; j++) {
                        map.put(times.get(j), fillValue);
                    }
                }
            }
        }
    }

    /** 补齐缺失时间点 */
    private void fillMissing(LocalDateTime start, LocalDateTime end, Map<LocalDateTime, Double> map) {
        // 确保起始时间是5分钟对齐的
        LocalDateTime t = alignToFiveMinutesFloor(start);

        while (t.isBefore(end) || t.isEqual(end)) {  // 改为包含end时间点
            if (!map.containsKey(t)) {
                Double value = interpolateMissingValue(t, map);
                if (value == null || Double.isNaN(value) || value < 0) {
                    value = getLastValidValue(t, map);  // 使用最后一个有效值而非0
                }
                map.put(t, value);
            }
            t = t.plusMinutes(5);
        }
    }
    // 获取最后一个有效值（不包括0）
    private Double getLastValidValue(LocalDateTime t, Map<LocalDateTime, Double> map) {
        List<LocalDateTime> keys = new ArrayList<>(map.keySet());
        keys.sort(Comparator.naturalOrder());

        for (int i = keys.size() - 1; i >= 0; i--) {
            LocalDateTime key = keys.get(i);
            if (key.isBefore(t)) {
                Double val = map.get(key);
                if (val != null && val >= 0) {
                    return val;
                }
            }
        }
        return 0.0;  // 如果没有找到，返回0作为最后的备选
    }

    /** 插值计算 */
    private Double interpolateMissingValue(LocalDateTime m, Map<LocalDateTime, Double> map) {
        List<LocalDateTime> keys = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Double> e : map.entrySet()) {
            Double v = e.getValue();
            if (v != null && !Double.isNaN(v) && v >= 0.0) {  // 只考虑非负值
                keys.add(e.getKey());
            }
        }
        if (keys.size() < 2) {
            return getLastValidValue(m, map);  // 使用最后有效值而非0
        }

        keys.sort(Comparator.naturalOrder());

        for (int i = 0; i < keys.size() - 1; i++) {
            LocalDateTime a = keys.get(i);
            LocalDateTime b = keys.get(i + 1);
            if (m.isAfter(a) && m.isBefore(b)) {
                double v1 = map.get(a);
                double v2 = map.get(b);

                // 确保值有效
                if (Double.isNaN(v1) || Double.isNaN(v2)) {
                    return getLastValidValue(m, map);
                }

                long tot = Duration.between(a, b).getSeconds();
                if (tot == 0) return v1;

                double w = (double) Duration.between(a, m).getSeconds() / tot;//
                double result = v1 + (v2 - v1) * w;
                return Math.max(result, 0.0);  // 确保结果非负
            }
        }

        // 处理边界情况
        LocalDateTime before = null, after = null;
        for (int i = keys.size() - 1; i >= 0; i--) {
            if (keys.get(i).isBefore(m)) {
                before = keys.get(i);
                break;
            }
        }
        for (LocalDateTime k : keys) {
            if (k.isAfter(m)) {
                after = k;
                break;
            }
        }

        Double vBefore = before == null ? null : map.get(before);
        Double vAfter  = after  == null ? null : map.get(after);

        Double result = null;
        if (vBefore != null && !Double.isNaN(vBefore) && vAfter != null && !Double.isNaN(vAfter)) {
            result = (vBefore + vAfter) / 2.0;
        } else if (vBefore != null && !Double.isNaN(vBefore)) {
            result = vBefore;
        } else if (vAfter  != null && !Double.isNaN(vAfter)) {
            result = vAfter;
        } else {
            result = 0.0;
        }

        return Math.max(result, 0.0);  // 确保结果非负
    }
    // 将任意对象转换为 LocalDateTime 类型
    private static LocalDateTime parseToLocalDateTime(Object o){
        if(o==null) return null;
        try{
            if(o instanceof LocalDateTime) return (LocalDateTime)o;
            if(o instanceof Timestamp)    return ((Timestamp)o).toLocalDateTime();
            if(o instanceof Long)         return LocalDateTime.ofInstant(Instant.ofEpochMilli((Long)o),ZoneId.systemDefault());
            if(o instanceof String){
                String s=((String)o).trim();
                try{return LocalDateTime.parse(s);}
                catch(DateTimeParseException e){
                    DateTimeFormatter f=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return LocalDateTime.parse(s,f);
                }
            }
        }catch(Exception ignored){}
        return null;
    }

    // 安全获取 Double 值
    private static Double safeGetDouble(JSONObject row, String key){
        try{
            Object o=row.get(key);
            if(o==null) return null;
            return (o instanceof Number)? ((Number)o).doubleValue():Double.parseDouble(o.toString());
        }catch(Exception e){return null;}
    }

    // 对齐到 5 分钟
    static LocalDateTime alignToFiveMinutesFloor(LocalDateTime t){
        int aligned = (t.getMinute() / 5) * 5;
        return t.withMinute(aligned).withSecond(0).withNano(0);
    }

    //以下为对变压器数据计算------------------------------------------------------------------------
    public void run1(String devices,String yesterday){
        DB3 db3=new DB3();
        JSONArray data=db3.calculate3(devices,yesterday);
        List<JSONObject> list=new ArrayList<>();
        for(Object obj:data) if(obj instanceof JSONObject) list.add((JSONObject) obj);
        if(list.isEmpty()){
            logger.warn("未找到设备 {} 昨日数据", devices);
            return;
        }
       Map<LocalDateTime,Double> map=new HashMap<>();
        for(JSONObject row:list){
            String equipmentCode=row.getString("equipmentCode");
            String collectTime=row.getString("collectTime");
            double recharge=row.getDouble("activePower")/12;
            db3.deposit3(equipmentCode,collectTime,recharge);
        }
    }

    //对历史数据进行5分钟差值计算----------------------------------------------
    @GetMapping("/historyAll")
    public String manualCalculateRange(@RequestParam String start, @RequestParam String end) {
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);


            logger.info("手动计算接口启动：时间范围 {} 至 {}", start, end);

            // 遍历日期范围
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                String dateStr = date.toString();
                logger.info("开始计算日期：{}", dateStr);

                // 光伏类设备计算
                for (String device : Config.mixed) {
                    try {
                        LocalDate targetDate = LocalDate.parse(dateStr);
                        LocalDateTime startTime = targetDate.atStartOfDay();
                        LocalDateTime endTime = targetDate.plusDays(1).atStartOfDay().minusSeconds(1);
                        run(device, startTime, endTime);
                        logger.info("设备 {} 光伏计算完成，日期 {}", device, dateStr);
                    } catch (Exception e) {
                        logger.error("设备 {} 光伏计算异常：{}", device, e.getMessage(), e);
                    }
                }
            }

            return "计算完成，范围：" + start + " 至 " + end;

        } catch (Exception e) {
            logger.error("手动计算接口异常: {}", e.getMessage(), e);
            return "❌ 日期格式错误，应为 yyyy-MM-dd";
        }
    }

    @GetMapping("/historyAll1")
    public String manualCalculateRange1(@RequestParam String start, @RequestParam String end) {
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);


            logger.info("手动计算接口启动：时间范围 {} 至 {}", start, end);

            // 遍历日期范围
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                String dateStr = date.toString();

                // 变压器类设备计算
                for (String device : Config.bian2) {
                    try {
                        run2(device, dateStr);
                    } catch (Exception e) {
                        logger.error("设备 {} 变压器计算异常：{}", device, e.getMessage(), e);
                    }
                }
            }

            return "计算完成，范围：" + start + " 至 " + end;

        } catch (Exception e) {
            logger.error("手动计算接口异常: {}", e.getMessage(), e);
            return "❌ 日期格式错误，应为 yyyy-MM-dd";
        }
    }



    public void run2(String devices, String date) {
        DB3 db3 = new DB3();
        try {
            JSONArray data = db3.calculate3(devices, date); // 查询指定日期的数据
            List<JSONObject> list = new ArrayList<>();
            for (Object obj : data) if (obj instanceof JSONObject) list.add((JSONObject) obj);

            if (list.isEmpty()) {
                logger.warn("未找到设备 {} 的 {} 数据", devices, date);
                return;
            }

            for (JSONObject row : list) {
                String equipmentCode = row.getString("equipmentCode");
                String collectTime = row.getString("collectTime");
                double recharge = row.getDouble("activePower") / 12;
                db3.deposit3(equipmentCode, collectTime, recharge);
            }

            logger.info("设备 {} 的 {} 数据计算完成（变压器）", devices, date);

        } catch (Exception e) {
            logger.error("run2 执行异常：设备 {} 日期 {}，错误 {}", devices, date, e.getMessage(), e);
        } finally {
            db3.close(); // 确保数据库连接释放
        }
    }







}
