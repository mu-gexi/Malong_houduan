package org.example.weiwang.Try;

import com.alibaba.fastjson.JSONObject;
import org.example.weiwang.Config.CompleteLoginClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

//微网的接口
@RestController
public class IOCquery {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/IOCquery")
    @CrossOrigin(origins ="*")
    public String getPointData(
            //@RequestParam请求的字符串
            @RequestParam String point,
            @RequestParam String date) throws ParseException {
        SimpleDateFormat timeStr=new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat Strtime=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date sTime=timeStr.parse(date);
        long st=sTime.getTime();
        long et=st+24*3600*1000;
        Date eTime=new Date(et);

        String startTime=Strtime.format(sTime);
        String endTime=Strtime.format(eTime);
        CompleteLoginClient com=new CompleteLoginClient();
//        com.token();
        String Authorization=com.token().get("tokenB").toString();
        String Cookie=com.token().get("Cookie").toString();


        String url = "http://ioc-f9374957.hi-link.huamod.com/apis/interface/query/dev/point/data";

        // 构造请求头
        HttpHeaders headers = new HttpHeaders();
        //设置请求ContentType的配置，表示请求将以application/x-www-from-urlencoded格式编码
        //setContentType()指定请求类型
        //MediaType.APPLICATION_FROM_URLENCODED表示发生表单数据
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "huamod" + Authorization);
        headers.set("Cookie", Cookie);

        // 构造请求体
        String body = "point=" + URLEncoder.encode(point, StandardCharsets.UTF_8) +
                "&start_time=" + URLEncoder.encode(startTime, StandardCharsets.UTF_8) +
                "&end_time=" + URLEncoder.encode(endTime, StandardCharsets.UTF_8);

        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

        // 发送 POST 请求
        ResponseEntity<String> response = restTemplate.exchange( url, HttpMethod.POST, requestEntity, String.class);
        JSONObject Body=JSONObject.parseObject(response.getBody());
        JSONObject result=Body.getJSONObject("result");

        return result.toString(); // 返回 JSON
    }
}
