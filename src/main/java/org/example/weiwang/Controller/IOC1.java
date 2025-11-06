package org.example.weiwang.Controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.weiwang.Config.CompleteLoginClient;
import org.example.weiwang.Config.CompleteLoginClient1;
import org.example.weiwang.Config.Config;
import org.example.weiwang.mysql.DB1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//实时获取微电网数据--------------------------------------------------------------------------------------------------

@RestController
public class IOC1 {

    private static final Logger logger = LoggerFactory.getLogger(IOC1.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final RestTemplate restTemplate = new RestTemplate();

    // 时间戳Map：key=设备点，支持动态设备点数量
    private static final Map<String, LocalDateTime> lastProcessedTimeMap = new ConcurrentHashMap<>();

    private static LocalDateTime lastTokenFetchTime = LocalDateTime.MIN;
    // ------------------------------
    // 定时任务：动态处理所有功率点（兼容任意长度的配置数组）
    // ------------------------------
    @Scheduled(cron = "0 */3 * * * ?")
    public void getBin() {
        for (String[] device : Config.IOCname) {
            try {
                // 必须包含的基础信息：至少需要设备点和表号（索引0和1）
                if (device.length < 2) {
                    logger.warn("跳过无效配置项：长度不足2（至少需要[设备点, 表号]）");
                    continue;
                }

                String mainPowerPoint = device[0];    // 主功率点（固定索引0）
                String equipmentCode = device[1];     // 表号（固定索引1）

                // 处理主功率点（固定为第一个设备点）
                // 第三个参数传入你想要存储的字段名
                processPowerPoint(mainPowerPoint, equipmentCode, "activePower");

                // 动态处理剩余的功率点（从索引2开始，数量不固定）
                for (int i = 2; i < device.length; i++) {
                    String extraPowerPoint = device[i];
                    // 这里传入你为扩展功率点指定的字段名
                    String[] fieldName={"","","bilibiliPower","monthPower","totalPower","day_charging_power","day_discharging_power","total_charging_power","total_discharging_power"};
                    processPowerPoint(extraPowerPoint, equipmentCode, fieldName[i]);
                }

            } catch (Exception e) {
                // 表号可能为空时的容错处理
                String safeEquipmentCode = (device.length >= 2) ? device[1] : "未知表号";
                logger.error("定时处理设备组（表号：{}）数据失败", safeEquipmentCode, e);
            }
        }
    }

    // ------------------------------
    // 手动接口：支持任意设备点查询
    // ------------------------------
//    @ResponseBody
//    @CrossOrigin(origins = "*")
//
//    public String getDin(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            @RequestParam String point,
//            @RequestParam (required = false) String date) {
//
//        response.setHeader("Access-Control-Allow-Origin", "*");
//
//        if (point != null || point.trim().isEmpty()) {
//            return "参数错误：point不能为空";
//        }
//
//        // 日期处理
//        String actualDate = (date == null || date.trim().isEmpty())
//                ? LocalDate.now().format(DATE_FORMAT)
//                : date;
//        try {
//            LocalDate.parse(actualDate, DATE_FORMAT);
//        } catch (DateTimeParseException e) {
//            return "参数错误：date格式应为yyyy-MM-dd";
//        }
//
//        // 动态匹配表号和目标字段
//        Map<String, Object> matchResult = matchEquipmentAndFieldByPoint(point);
//        if (matchResult == null) {
//            return "未找到设备点「" + point + "」的配置信息";
//        }
//
//        String equipmentCode = (String) matchResult.get("equipmentCode");
//        String targetField = (String) matchResult.get("targetField");
//
//        try {
//            String apiResult = getPointData(point, actualDate);
//            if (apiResult != null && !apiResult.isEmpty()) {
//                processAndSavePowerData(point, equipmentCode, apiResult, targetField);
//            }
//            return "获取成功：设备点=" + point + "，日期=" + actualDate + "，数据长度=" + apiResult.length();
//        } catch (Exception e) {
//            logger.error("手动处理设备点 {} 失败", point, e);
//            return "获取失败：" + e.getMessage();
//        }
//    }


    // 动态匹配设备点的表号和目标字段
    private Map<String, Object> matchEquipmentAndFieldByPoint(String point) {
        for (String[] device : Config.IOCname) {
            if (device.length < 2) continue; // 跳过无效配置

            // 主功率点（索引0）使用你指定的主字段
            if (device[0].equals(point)) {
                return Map.of(
                        "equipmentCode", device[1],
                        "targetField", "activePower"  // 这里修改为主功率点的目标字段
                );
            }

            // 扩展功率点（索引2及以后）使用你指定的扩展字段
            for (int i = 2; i < device.length; i++) {
                if (device[i].equals(point)) {
                    return Map.of(
                            "equipmentCode", device[1],
                            "targetField", "bilibiliPower"  // 这里修改为扩展功率点的目标字段
                    );
                }
            }
        }
        return null;
    }

    // ------------------------------
    // 功率点统一处理入口（接收目标字段参数）
    // ------------------------------
    private void processPowerPoint(String point, String equipmentCode, String targetField) {
        try {
            String apiResult = getPointData(point, LocalDate.now().format(DATE_FORMAT));
            if (apiResult != null && !apiResult.isEmpty()) {
                processAndSavePowerData(point, equipmentCode, apiResult, targetField);
            }
        } catch (Exception e) {
            logger.error("处理设备点 {}（表号：{}，目标字段：{}）失败", point, equipmentCode, targetField, e);
        }
    }

    // ------------------------------
    // 获取API数据(如果是第一次运行此程序，获取当天0点到当前时间的数据，之后获取实时数据）
    // ------------------------------
    public String getPointData(String point, String date) {
        LocalDate targetDate = LocalDate.parse(date, DATE_FORMAT);// 目标日期
        LocalDateTime timeStart = LocalDateTime.of(targetDate, LocalTime.MIN);// 当天00:00:00
        LocalDateTime timeEnd = LocalDateTime.of(targetDate, LocalTime.MAX);// 当天23:59:59

        if (targetDate.isEqual(LocalDate.now())) {// 如果是当天，则获取当前时间
            timeEnd = LocalDateTime.now();
        }

        boolean isFirstProcess = !lastProcessedTimeMap.containsKey(point);// 是否首次处理
        LocalDateTime lastTime = lastProcessedTimeMap.getOrDefault(point, timeStart);// 上次处理时间

        if (lastTime.toLocalDate().isBefore(targetDate)) {
            lastTime = timeStart;
            logger.info("设备点 {} 跨日期重置，起点：{}", point, timeStart);
        }

        String startTime = lastTime.format(DATE_TIME_FORMAT);
        String endTime = timeEnd.format(DATE_TIME_FORMAT);

        logger.info("获取设备点 {} 数据：{} 至 {} {}",
                point, startTime, endTime, isFirstProcess ? "(首次处理)" : "");

        // 认证与请求逻辑（复用原有代码）
        CompleteLoginClient1 loginClient = new CompleteLoginClient1();//声明CompleteLoginClient1
        Map<String,Object> tokenInfo = loginClient.token();//调用token方法
//        if(lastTokenFetchTime.isBefore(LocalDateTime.now().minusMinutes(60)) ||
//          lastTokenFetchTime.equals(LocalDateTime.MIN)){//获取token
//            tokenInfo =loginClient.token();
//            lastTokenFetchTime=LocalDateTime.now();//更新token获取时间
//        }else {
////        tokenInfo = loginClient.token();
//        }
        String authorization = tokenInfo.get("tokenB").toString();//获取tokenB
        String cookie = tokenInfo.get("Cookie").toString();//获取cookie

        String apiUrl = "http://ioc-f9374957.hi-link.huamod.com/apis/interface/query/dev/point/data";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);//设置请求头
        headers.set("Authorization", "huamod" + authorization);
        headers.set("Cookie", cookie);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("point", point);
        formData.add("start_time", startTime);
        formData.add("end_time", endTime);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(formData, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, requestEntity, String.class);//发送请求

        if (response.getStatusCode() != HttpStatus.OK) {//判断请求状态
            throw new RuntimeException("API请求失败，状态码：" + response.getStatusCodeValue());
        }

        return response.getBody() != null ? response.getBody() : "{}";
    }

    // ------------------------------
    // 解析并保存数据（根据目标字段动态插入）
    // ------------------------------
    private void processAndSavePowerData(
            String point, String equipmentCode, String apiResult, String targetField) {

        if (apiResult == null || apiResult.isEmpty()) {
            logger.warn("设备点 {} 数据为空", point);
            return;
        }

        DB1 dbHelper = new DB1();
        try {
            JSONObject responseJson = JSONObject.parseObject(apiResult);//解析json
//            if (!"success".equals(responseJson.getString("status"))) {
//                logger.error("设备点 {} API返回失败：{}", point, responseJson.getString("message"));
//                return;
//            }

            JSONObject result = responseJson.getJSONObject("result");//获取result
            if (result == null) {
                logger.warn("设备点 {} 无result数据", point);
                return;
            }

            JSONArray timeArray = result.getJSONArray("x_axis");//获取时间数组
            JSONArray valueArray = result.getJSONArray("point_data");//获取功率数组
//            if(timeArray==null||valueArray==null||timeArray.size()!= valueArray.size()){
//                logger.error("此设备点{}无数据",point);
//            }

            if (timeArray == null || valueArray == null || timeArray.size() != valueArray.size()) {//判断数组长度是否相等
                logger.error("设备点 {} 数组格式错误：时间{}，数值{}",
                        point, timeArray.size(), valueArray.size());
                return;
            }

            LocalDateTime lastTime = lastProcessedTimeMap.getOrDefault(
                    point, LocalDateTime.of(LocalDate.now(), LocalTime.MIN));//获取上次处理时间
            LocalDateTime latestRecordTime = lastTime;

            for (int i = 0; i < timeArray.size(); i++) {
                try {
                    String collectTime = timeArray.getString(i);
                    double powerValue = Double.parseDouble(valueArray.getString(i));

                    LocalDateTime dataTime = LocalDateTime.parse(collectTime, DATE_TIME_FORMAT);
                    //只处理当天的到现在数据，且在上次处理的时间之后插入最新记录
                    if (dataTime.toLocalDate().isEqual(LocalDate.now()) && dataTime.isAfter(lastTime)) {
                        // 核心：将数据插入到你指定的目标字段
                        dbHelper.saveOrUpdatePowerData(equipmentCode, collectTime, powerValue, targetField);

                        if (dataTime.isAfter(latestRecordTime)) {
                            latestRecordTime = dataTime;
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理第{}条数据失败", i, e);
                }
            }

            lastProcessedTimeMap.put(point, latestRecordTime);
            logger.info("设备点 {} 处理完成，最新时间：{}，存储字段：{}",
                    point, latestRecordTime.format(DATE_TIME_FORMAT), targetField);

        } catch (Exception e) {
            logger.error("设备点 {} 数据处理异常", point, e);
        } finally {
            if (dbHelper != null) {
                dbHelper.close();
            }
        }
    }




    @ResponseBody
    @CrossOrigin(origins = "*")
    @GetMapping("historyIOC")
    public void get() {
        for (String[] device : Config.IOCname) {
            if (device.length < 2) {
                logger.warn("跳过无效配置项：长度不足2");
                continue;
            }
            String mainPowerPoint = device[0];
            String equipmentCode  = device[1];

            try {
                // 主功率点
                process(mainPowerPoint, equipmentCode,"activePower");
                // 其他功率点
                for (int i = 2; i < device.length; i++) {
                    String[] fieldName={"","","bilibiliPower","monthPower","totalPower","day_charging_power","day_discharging_power","total_charging_power","total_discharging_power"};
                    process(device[i], equipmentCode, fieldName[i]);
                }

            } catch (Exception e) {
                logger.error("处理设备组失败：表号 {}", equipmentCode, e);
            }
        }
    }


    private void process(String point, String equipmentCode, String targetField) {
        try {
            if(point==null||point.isEmpty()){
                logger.error("设备点为空");
                return;
            };
            String apiResult = getPoint(point);//获取api数据
            if (apiResult != null && !apiResult.isEmpty()) {
                // 传入 start 和 end
                processAnd(point, equipmentCode, apiResult, targetField);
            } else {
                logger.warn("设备点 {} 返回空数据", point);
            }
        } catch (Exception e) {
            logger.error("处理设备点 {}（表号 {}）失败", point, equipmentCode, e);
        }
    }


    public String getPoint(String point)  {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        LocalDate startDate = LocalDate.of(2025, 11, 02);//开始日期
        LocalDate endDate   = LocalDate.of(2025, 11, 02);//结束日期

        String strStartTime = startDate.atStartOfDay().format(dtf);
        // 结束日 23:59:59
        String strEndTime   = endDate.atTime(LocalTime.MAX).format(dtf);

        CompleteLoginClient1 loginClient = new CompleteLoginClient1();//声明CompleteLoginClient1
        Map<String, Object> tokenInfo   = loginClient.token();//调用token方法
        String authorization = tokenInfo.get("tokenB").toString();//获取tokenB
        String cookie        = tokenInfo.get("Cookie").toString();//获取cookie

        String apiUrl = "http://ioc-f9374957.hi-link.huamod.com/apis/interface/query/dev/point/data";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "huamod" + authorization);
        headers.set("Cookie", cookie);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("point", point);
        formData.add("start_time", strStartTime);
        formData.add("end_time", strEndTime);

        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(formData, headers);//请求体
        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, req, String.class);//发送请求,获取数据

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("API请求失败，状态码：" + response.getStatusCodeValue());
        }
        return response.getBody() == null ? "{}" : response.getBody();
    }

    // 解析并保存数据（根据目标字段动态插入）
    private void processAnd(String point, String equipmentCode, String apiResult, String targetField) {
        DB1 dbHelper = new DB1();
        try {
            JSONObject result = JSONObject.parseObject(apiResult).getJSONObject("result");
            if (result == null) {
                logger.warn("设备点 {} 无 result 数据", point);
                return;
            }

            JSONArray timeArray  = result.getJSONArray("x_axis");
            JSONArray valueArray = result.getJSONArray("point_data");
            if (timeArray == null || valueArray == null || timeArray.size() != valueArray.size()) {
                logger.error("设备点 {} 数据格式错误：时间{}，数值{}", point,
                        timeArray != null ? timeArray.size() : -1,
                        valueArray != null ? valueArray.size() : -1);
                return;
            }

            for (int i = 0; i < timeArray.size(); i++) {
                try {
                    String collectTime = timeArray.getString(i);
                    double powerValue  = Double.parseDouble(valueArray.getString(i));
                    //  格式化两位小数
                    double rounded     = Math.round(powerValue * 100.0) / 100.0;
                    dbHelper.saveOrUpdatePowerData(equipmentCode, collectTime, rounded, targetField);
                } catch (Exception e) {
                    logger.error("设备点 {} 第 {} 条数据处理失败", point, i, e);
                }
            }
        } catch (Exception e) {
            logger.error("设备点 {} 数据解析异常", point, e);
        } finally {
            dbHelper.close();
        }
    }






}
