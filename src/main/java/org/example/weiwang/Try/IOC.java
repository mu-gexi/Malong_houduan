//package org.example.weiwang;
//
//import com.alibaba.fastjson.JSONArray;
//import com.alibaba.fastjson.JSONObject;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.example.weiwang.mysql.DB1;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.*;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.ResponseBody;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.client.RestTemplate;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.LocalTime;
//import java.time.format.DateTimeFormatter;
//import java.time.format.DateTimeParseException;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@RestController
//public class IOC {
//
//    private static final Logger logger = LoggerFactory.getLogger(IOC.class);
//    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//    private final RestTemplate restTemplate = new RestTemplate();
//
//    // 时间戳存储：仅用设备点作为key
//    private static final Map<String, LocalDateTime> lastProcessedTimeMap = new ConcurrentHashMap<>();
//
//    // 定时任务：每3分钟执行一次
//    @Scheduled(cron = "0 */3 * * * ?")
//    public void getBin() {
//        for (String[] device : Config.IOCname) {
//            try {
//                String powerPoint = device[0];        // 请求部分1
//                String equipmentCode = device[1];     // 表号
//                String point = device[2];              // 请求部分2
//
//
//                processDevicePoint(powerPoint, equipmentCode);
//
//            } catch (Exception e) {
//                logger.error("定时处理设备点 {}（表号：{}）失败", device[0], device[1], e);
//            }
//        }
//    }
//
//    // 手动调用接口：参数为设备点+日期
//    @ResponseBody
//    @GetMapping("/ioc")
//    public String getDin(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            @RequestParam String point,          // 设备点
//            @RequestParam(required = false) String date) { // 日期
//
//        // 跨域设置
//        response.setHeader("Access-Control-Allow-Origin", "*");
//
//        // 参数校验
//        if (point == null || point.trim().isEmpty()) {
//            return "参数错误：point（设备点）不能为空";
//        }
//
//        // 日期处理：默认当天，校验格式
//        if (date == null || date.trim().isEmpty()) {
//            date = LocalDate.now().format(DATE_FORMAT);
//        } else {
//            try {
//                LocalDate.parse(date, DATE_FORMAT);
//            } catch (DateTimeParseException e) {
//                return "参数错误：date格式应为yyyy-MM-dd（例如2025-09-05）";
//            }
//        }
//
//        // 匹配表号（仅用于数据库保存）
//        String equipmentCode = matchEquipmentCode(point);
//        if (equipmentCode == null) {
//            return "未找到设备点「" + point + "」对应的表号，请检查配置";
//        }
//
//        // 处理数据
//        try {
//            String result = getPointData(point, date);
//            if (result != null && !result.isEmpty()) {
//                processAndSaveData(point, equipmentCode, result);
//            }
//            return "处理成功，数据长度：" + (result != null ? result.length() : 0) + "字符";
//        } catch (Exception e) {
//            logger.error("手动处理设备点 {}（表号：{}）失败", point, equipmentCode, e);
//            return "处理失败：" + e.getMessage();
//        }
//    }
//
//    /**
//     * 根据设备点匹配表号（仅用于数据库保存）
//     */
//    private String matchEquipmentCode(String point) {
//        for (String[] device : Config.IOCname) {
//            if (device[0].equals(point)) {
//                return device[1];
//            }
//        }
//        return null;
//    }
//
//    /**
//     * 处理单个设备点：获取数据并保存
//     */
//    private void processDevicePoint(String point, String equipmentCode) {
//        try {
//            // 获取数据
//            String result = getPointData(point, LocalDate.now().format(DATE_FORMAT));
//            // 保存数据（传入表号）
//            if (result != null && !result.isEmpty()) {
//                processAndSaveData(point, equipmentCode, result);
//            }
//        } catch (Exception e) {
//            logger.error("设备点 {}（表号：{}）处理失败", point, equipmentCode, e);
//        }
//    }
//
//    /**
//     * 获取设备点数据
//     */
//    public String getPointData(String point, String date) {
//        // 时间范围计算
//        LocalDate targetDate = LocalDate.parse(date, DATE_FORMAT);
//        LocalDateTime timeStart = LocalDateTime.of(targetDate, LocalTime.MIN);// 当天数据起始时间为当天0点
//        LocalDateTime timeEnd = LocalDateTime.of(targetDate, LocalTime.MAX);//
//
//        // 当天数据结束时间为当前时间
//        if (targetDate.isEqual(LocalDate.now())) {
//            timeEnd = LocalDateTime.now();
//        }
//
//        // 增量处理逻辑
//        boolean isFirstProcess = !lastProcessedTimeMap.containsKey(point);
//        LocalDateTime lastTime = lastProcessedTimeMap.getOrDefault(point, timeStart);
//
//        // 跨日期重置
//        if (lastTime.toLocalDate().isBefore(targetDate)) {
//            lastTime = timeStart;
//            logger.info("设备点 {} 跨日期，重置起点为 {} 0点", point, date);
//        }
//
//        // 格式化时间参数
//        String startTime = lastTime.format(DATE_TIME_FORMAT);
//        String endTime = timeEnd.format(DATE_TIME_FORMAT);
//
//        // 日志输出
//        if (isFirstProcess) {
//            logger.info("获取设备点 {} 数据，时间范围: {} 至 {} (首次处理从当前时间开始)",
//                    point, startTime, endTime);
//        } else {
//            logger.info("获取设备点 {} 数据，时间范围: {} 至 {}",
//                    point, startTime, endTime);
//        }
//
//        // 认证信息
//        CompleteLoginClient com = new CompleteLoginClient();
//        Map<String, Object> tokenInfo = com.token();
//        String authorization = tokenInfo.get("tokenB").toString();
//        String cookie = tokenInfo.get("Cookie").toString();
//
//        // 构造请求
//        String url = "http://ioc-f9374957.hi-link.huamod.com/apis/interface/query/dev/point/data";
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//        headers.set("Authorization", "huamod" + authorization);
//        headers.set("Cookie", cookie);
//
//        // 表单参数（无表号）
//        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
//        formData.add("point", point);
//        formData.add("start_time", startTime);
//        formData.add("end_time", endTime);
//
//        // 发送请求
//        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(formData, headers);
//        ResponseEntity<String> response = restTemplate.exchange(
//                url,
//                HttpMethod.POST,
//                requestEntity,
//                String.class
//        );
//
//        // 响应校验
//        if (response.getStatusCode() != HttpStatus.OK) {
//            logger.error("API请求失败，状态码: {}", response.getStatusCodeValue());
//            throw new RuntimeException("API请求失败，状态码: " + response.getStatusCodeValue());
//        }
//
//        return response.getBody() != null ? response.getBody() : "{}";
//    }
//
//    /**
//     * 处理并保存数据
//     */
//    private void processAndSaveData(String point, String equipmentCode, String responseBody) {
//        if (responseBody == null || responseBody.isEmpty()) {
//            logger.warn("设备点 {} 响应数据为空", point);
//            return;
//        }
//
//        DB1 dbHelper = new DB1();
//        try {
//            // 解析响应
//            JSONObject body = JSONObject.parseObject(responseBody);
////            if (!"success".equals(body.getString("status"))) {
////                logger.error("设备点 {} 数据获取失败：{}", point, body.getString("message"));
////                return;
////            }
//
//            JSONObject result = body.getJSONObject("result");
//            if (result == null) {
//                logger.warn("设备点 {} 无result数据", point);
//                return;
//            }
//
//            // 提取时间和值数组
//            JSONArray timeArray = result.getJSONArray("x_axis");
//            JSONArray valueArray = result.getJSONArray("point_data");
//
//            logger.info("设备点 {} 开始处理，共 {} 条记录", point, timeArray.size());
//
//            // 时间戳处理
//            LocalDateTime lastTime = lastProcessedTimeMap.getOrDefault(
//                    point,
//                    LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
//            );
//            LocalDateTime latestRecordTime = lastTime;
//
//            // 遍历数据并保存
//            for (int i = 0; i < timeArray.size(); i++) {
//                try {
//                    String collectTimeStr = timeArray.getString(i);
//                    String valueStr = valueArray.getString(i);
//
//                    // 转换数值类型
//                    double activePower;
//                    try {
//                        activePower = Double.parseDouble(valueStr);
//                    } catch (NumberFormatException e) {
//                        logger.error("设备点 {} 第 {} 条数据格式错误：{}", point, i, valueStr);
//                        continue;
//                    }
//
//                    // 解析时间
//                    LocalDateTime dataTime = LocalDateTime.parse(collectTimeStr, DATE_TIME_FORMAT);
//
//                    // 过滤条件：当天数据且未处理过
//                    if (dataTime.toLocalDate().isEqual(LocalDate.now()) && dataTime.isAfter(lastTime)) {
//                        // 表号仅在此处传入数据库
//                        dbHelper.AutoIOC(equipmentCode, collectTimeStr, activePower);
//
//                        // 更新最新时间
//                        if (dataTime.isAfter(latestRecordTime)) {
//                            latestRecordTime = dataTime;
//                        }
//                    } else {
//                        logger.debug("跳过数据：{} {}（原因：{}）",
//                                point, collectTimeStr,
//                                dataTime.toLocalDate().isEqual(LocalDate.now()) ? "已处理" : "非当天数据");
//                    }
//                } catch (Exception e) {
//                    logger.error("设备点 {} 第 {} 条数据处理失败", point, i, e);
//                }
//            }
//
//            // 更新时间戳
//            lastProcessedTimeMap.put(point, latestRecordTime);
//            logger.info("设备点 {} 处理完成，最新时间：{}",
//                    point, latestRecordTime.format(DATE_TIME_FORMAT));
//
//        } catch (Exception e) {
//            logger.error("设备点 {} 数据处理异常", point, e);
//        } finally {
//            // 关闭数据库连接
//            if (dbHelper != null) {
//                try {
//                    dbHelper.close();
//                } catch (Exception e) {
//                    logger.error("数据库连接关闭失败", e);
//                }
//            }
//        }
//    }
//}
