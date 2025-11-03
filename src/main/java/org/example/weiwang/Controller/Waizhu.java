package org.example.weiwang.Controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.example.weiwang.Config.Config;
import org.example.weiwang.mysql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class Waizhu {

    private static final Logger logger = LoggerFactory.getLogger(Waizhu.class);
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 存储每个电表最后处理的时间戳，用于去重(线程安全的Map)
    private static final Map<String, LocalDateTime> lastProcessedTimeMap = new ConcurrentHashMap<>();

    // 构建倍率 Map
    private static final Map<String, Double> configMap = new HashMap<>();
    static {
        for (String[] device : Config.devices) {
            String meterId = device[0];
            double multiplier = Double.parseDouble(device[2]);
            configMap.put(meterId, multiplier);
        }
    }

    @Scheduled(cron = "0 */3 * * * ?")
    public void getBin() {
        for (String[] device : Config.devices) {
            try {
                String meterId = device[0];
                String result = getRung(meterId);
                logger.info("电表 {} 本次获取数据总量: {}", meterId, JSONArray.parseArray(result).size());
            } catch (Exception e) {
                logger.error("定时获取电表 {} 数据失败", device[0], e);
            }
        }
    }

    @ResponseBody
    @CrossOrigin(origins = "*")
    @GetMapping("/bing")
    public String getDin(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        String meterId = request.getParameter("meterId");
        try {
            String result = getRung(meterId);
            logger.info("手动获取电表 {} 数据总量: {}", meterId, JSONArray.parseArray(result).size());
            return result;
        } catch (Exception e) {
            logger.error("手动获取电表 {} 数据失败", meterId, e);
            return "获取失败：" + e.getMessage();
        }
    }

    public static String getRung(String meterId)  {
        JSONArray powerPoint = new JSONArray();

        // 当天0点（作为首次处理的起始时间）
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime now = LocalDateTime.now();
        // 首次处理时从当天0点开始，后续从上次处理时间开始
        LocalDateTime lastTime = lastProcessedTimeMap.getOrDefault(meterId, todayStart);
        // 标记是否为首次处理
        boolean isFirstProcess = !lastProcessedTimeMap.containsKey(meterId);

        // 检查是否跨天，如果跨天则重置为当天0点
        if (lastTime.toLocalDate().isBefore(LocalDate.now())) {
            lastTime = todayStart;
            logger.info("电表 {} 跨天，重置处理起点为当天0点", meterId);
        }

        // 时间范围：上次处理时间 至 当前时间
        String strStartTime = lastTime.format(DATE_TIME_FORMAT);
        LocalDateTime todayEnd = LocalDate.now().atStartOfDay(); // 今天00:00:00
        String strEndTime = now.format(DATE_TIME_FORMAT);


        // 首次处理时明确标注"首次处理从当前时间开始"，即使实际起点是当天0点
        if (isFirstProcess) {
            logger.info("获取电表 {} 数据，时间范围: {} 至 {} (首次处理从当前时间开始)",
                    meterId, strStartTime, strEndTime);
        } else {
            logger.info("获取电表 {} 数据，时间范围: {} 至 {}",
                    meterId, strStartTime, strEndTime);
        }

        int page = 1;
        boolean hasMore = true;
        int totalRecords = 0;
        int newRecords = 0;  // 新增记录数
        LocalDateTime latestRecordTime = lastTime;  // 本次处理的最新记录时间

        DB1 dbHelper = new DB1();

        try {
            while (hasMore && page <= 10) {  // 限制最大页数
                try {
                    JSONObject params = new JSONObject();
                    JSONObject obparams = new JSONObject();
                    obparams.put("meterId", meterId);
                    obparams.put("startTime", strStartTime);
                    obparams.put("endTime", strEndTime);
                    params.put("param", obparams);
                    params.put("page", page);
                    params.put("size", 50);  // 分页大小

                    HttpPost httpPost = new HttpPost("http://gateway.xrny-os-core.sxxrny.com/dwjcys/iotApi/getRealDataByDate");
                    StringEntity entity = new StringEntity(params.toJSONString(), "utf-8");
                    entity.setContentType("application/json");
                    httpPost.setEntity(entity);
                    httpPost.addHeader("Accept", "application/json, text/plain, */*");
                    httpPost.addHeader("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.eyJsb2dpbl91c2VyX2tleSI6Ijc3ZTAyNWEzLTcxMWEtNGMxNS05Yjg5LTQwODA3ZjQzMTlhYyJ9.UsGBP097dnzzxpGJLSaTfsSUF1IbZlISedTNm9woT5r-mlyZWAye9n6mkcNVB9vbIa6Zt4vn4KiRzReqekLI3w");
                    httpPost.addHeader("Connection", "keep-alive");
                    httpPost.addHeader("Content-Type", "application/json; charset=UTF-8");
                    httpPost.addHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 SE 2.X MetaSr 1.0");
                    httpPost.addHeader("orgId", "08b460f496b0433f950b6db6903d1e98");

                    try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                        String strResponse = EntityUtils.toString(response.getEntity(), "utf-8");
                        JSONObject jsonResponse = JSONObject.parseObject(strResponse);

                        if (!jsonResponse.getString("code").equals("200")) {
                            logger.error("API返回错误: {}，响应内容: {}",
                                    jsonResponse.getString("msg"), strResponse);
                            hasMore = false;
                            continue;
                        }

                        JSONObject jsonResult = jsonResponse.getJSONObject("result");
                        if (jsonResult == null) {
                            logger.warn("API返回结果为空，响应内容: {}", strResponse);
                            hasMore = false;
                            continue;
                        }

                        JSONArray jsonRecords = jsonResult.getJSONArray("records");
                        if (jsonRecords == null) {
                            logger.warn("API返回记录为空，响应内容: {}", strResponse);
                            hasMore = false;
                            continue;
                        }

                        int currentPageSize = jsonRecords.size();
                        totalRecords += currentPageSize;
                        logger.info("电表 {} 第{}页获取到 {} 条记录，累计 {} 条",
                                meterId, page, currentPageSize, totalRecords);

                        for (int i = 0; i < jsonRecords.size(); i++) {

                            JSONObject record = jsonRecords.getJSONObject(i);
                            String equipmentCode = record.getString("equipmentCode");
                            String collectTimeStr = record.getString("collectTime");

                            // 解析记录时间
                            LocalDateTime collectTime;
                            try {
                                collectTime = LocalDateTime.parse(collectTimeStr, DATE_TIME_FORMAT);
                            } catch (Exception e) {
                                logger.error("解析时间格式失败: {}", collectTimeStr, e);
                                continue;
                            }

                            // 只处理：1.当天的数据 2.未处理过的数据（晚于上次处理时间）
                            if (collectTime.toLocalDate().isEqual(LocalDate.now()) && collectTime.isAfter(lastTime)) {
                                double activePower = record.getDoubleValue("activePower") * configMap.getOrDefault(equipmentCode, 0.0);
                                double positiveActive = record.getDoubleValue("positiveActive") * configMap.getOrDefault(equipmentCode, 0.0);
                                double reverseActive = record.getDoubleValue("reverseActive") * configMap.getOrDefault(equipmentCode, 0.0);
                                double activePowerA =record.getDouble("activePowerA")* configMap.getOrDefault(equipmentCode, 0.0);
                                double activePowerB =record.getDouble("activePowerB")* configMap.getOrDefault(equipmentCode, 0.0);
                                double activePowerC =record.getDouble("activePowerC")* configMap.getOrDefault(equipmentCode, 0.0);
                                double currentA =record.getDouble("currentA")* configMap.getOrDefault(equipmentCode, 0.0);
                                double currentB =record.getDouble("currentB")* configMap.getOrDefault(equipmentCode, 0.0);
                                double currentC =record.getDouble("currentC")* configMap.getOrDefault(equipmentCode, 0.0);


                                JSONObject point = new JSONObject();
                                point.put("equipmentCode", equipmentCode);
                                point.put("collectTime", collectTimeStr);
                                point.put("activePower", activePower);
                                point.put("positiveActive", positiveActive);
                                point.put("reverseActive", reverseActive);
                                point.put("activePowerA", activePowerA);
                                point.put("activePowerB", activePowerB);
                                point.put("activePowerC", activePowerC);
                                point.put("currentA", currentA);
                                point.put("currentB", currentB);
                                point.put("currentC", currentC);

                                powerPoint.add(point);
                                dbHelper.Auto(equipmentCode, collectTimeStr, activePower, positiveActive, reverseActive, activePowerA, activePowerB, activePowerC,currentA, currentB, currentC);

                                newRecords++;

                                // 更新本次处理的最新记录时间
                                if (collectTime.isAfter(latestRecordTime)) {
                                    latestRecordTime = collectTime;
                                }
                            } else {
                                logger.debug("跳过不符合条件的记录: 设备={}, 时间={} (原因: {}）",
                                        equipmentCode, collectTimeStr,
                                        collectTime.toLocalDate().isEqual(LocalDate.now()) ? "已处理过" : "非当天数据");
                            }
                        }

                        hasMore = currentPageSize >= 50;  // 分页大小判断
                        if (hasMore) {
                            page++;
                        }
                    }

                } catch (Exception e) {
                    logger.error("获取电表 {} 第 {} 页数据失败", meterId, page, e);
                    hasMore = false;
                }
            }
        } finally {
            dbHelper.close();
            // 更新最后处理时间为本次处理的最新记录时间
            lastProcessedTimeMap.put(meterId, latestRecordTime);
            logger.info("电表 {} 数据处理完成，总记录数: {}，新增记录数: {}，更新最后处理时间为: {}",
                    meterId, totalRecords, newRecords, latestRecordTime.format(DATE_TIME_FORMAT));
        }

        return powerPoint.toString();
    }

    @ResponseBody
    @GetMapping("/historyMalong")
    public void getD(HttpServletRequest request, HttpServletResponse response) throws ParseException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        for(String[] device : Config.devices){
            try{
                String meterId =device[0];
                String result =get(meterId);
                logger.info("电表{} 本次数据总量：[]",meterId,JSONArray.parseArray(result));
            }catch (Exception e){
                logger.error("定时获取电表 {} 数据失败", device[0], e);
            }
        }


    }


    public String get(String meterId) throws ParseException {

        JSONArray powerPoint = new JSONArray();
//设置一天时间范围
        SimpleDateFormat dayTime = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        LocalDate str = LocalDate.of(2025, 11, 02);
        LocalDate end = LocalDate.of(2025, 11, 03);


        while (str.isBefore(end)) {

            String date = str.toString();
            Date startTime = dayTime.parse(date);


            long startTimeStamp = startTime.getTime();
            long endTimeStamp = startTimeStamp + 24 * 3600 * 1000;
            Date endTimestop = new Date(endTimeStamp);
            String strStartTime = endTime.format(startTime);
            String strEndTime = endTime.format(endTimestop);
//创建请求
            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpPost httpPost = new HttpPost("http://gateway.xrny-os-core.sxxrny.com/dwjcys/iotApi/getRealDataByDate");

            JSONObject params = new JSONObject();
            JSONObject obparams = new JSONObject();
//请求体的请求类型
            obparams.put("meterId", meterId);
            obparams.put("startTime", strStartTime);
            obparams.put("endTime", strEndTime);
            params.put("param", obparams);
            params.put("page", 1);
            params.put("size", 2000);

//创建请求实体
//          httpPost.setEntity(new StringEntity(params.toJSONString()));
            StringEntity entity = new StringEntity(params.toJSONString(), "utf-8");

            entity.setContentType("application/json");
            httpPost.setEntity(entity);
//希望返回要求
            httpPost.addHeader("Accept", "application/json, text/plain, */*");
            httpPost.addHeader("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.eyJsb2dpbl91c2VyX2tleSI6Ijc3ZTAyNWEzLTcxMWEtNGMxNS05Yjg5LTQwODA3ZjQzMTlhYyJ9.UsGBP097dnzzxpGJLSaTfsSUF1IbZlISedTNm9woT5r-mlyZWAye9n6mkcNVB9vbIa6Zt4vn4KiRzReqekLI3w");
            httpPost.addHeader("Connection", "keep-alive");
            httpPost.addHeader("Content-Type", "application/json; charset=UTF-8");
            httpPost.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 SE 2.X MetaSr 1.0");
            httpPost.addHeader("orgId", "08b460f496b0433f950b6db6903d1e98");//身份授权


//获取数据


            try {
                HttpResponse httpResponse = httpClient.execute(httpPost);
                String strResponse = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);

                JSONObject jsonResponse = JSONObject.parseObject(strResponse);
                JSONObject jsonResult = jsonResponse.getJSONObject("result");
                JSONArray jsonRecords = jsonResult.getJSONArray("records");
                if (jsonRecords == null || jsonRecords.isEmpty()) {
                    break; // 没有数据，直接结束
                }

                DB1 dbHelper = new DB1();
                for (int i = 0; i < jsonRecords.size(); i++) {
                    JSONObject record = jsonRecords.getJSONObject(i);
                    String equipmentCode = record.getString("equipmentCode");
                    String collectTime = record.getString("collectTime");
                    double ratio = configMap.getOrDefault(equipmentCode, 1.0);

                    JSONObject point = new JSONObject();
                    point.put("equipmentCode", equipmentCode);
                    point.put("collectTime", collectTime);
                    point.put("activePower", record.getDoubleValue("activePower") * ratio);
                    point.put("positiveActive", record.getDoubleValue("positiveActive") * ratio);
                    point.put("reverseActive", record.getDoubleValue("reverseActive") * ratio);
                    point.put("activePowerA", record.getDoubleValue("activePowerA") * ratio);
                    point.put("activePowerB", record.getDoubleValue("activePowerB") * ratio);
                    point.put("activePowerC", record.getDoubleValue("activePowerC") * ratio);
                    point.put("currentA", record.getDoubleValue("currentA") * ratio);
                    point.put("currentB", record.getDoubleValue("currentB") * ratio);
                    point.put("currentC", record.getDoubleValue("currentC") * ratio);
                    powerPoint.add(point);

                    dbHelper.Auto(equipmentCode, collectTime,
                            point.getDoubleValue("activePower"),
                            point.getDoubleValue("positiveActive"),
                            point.getDoubleValue("reverseActive"),
                            point.getDoubleValue("activePowerA"),
                            point.getDoubleValue("activePowerB"),
                            point.getDoubleValue("activePowerC"),
                            point.getDoubleValue("currentA"),
                            point.getDoubleValue("currentB"),
                            point.getDoubleValue("currentC"));
                }

                dbHelper.close();


            } catch (ClientProtocolException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            str = str.plusDays(1);
        }
        return powerPoint.toString();

    }
}
