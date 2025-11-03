package org.example.weiwang.Try;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
//import java.net.http.HttpClient;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

//马龙的接口
@RestController
public class Controller {
    @ResponseBody
//    @RequestMapping(value = "/Malong")
    public String getRealDataByDate(HttpServletRequest request, HttpServletResponse response) throws ParseException, UnsupportedEncodingException {

        response.setHeader("Access-Control-Allow-Origin", "*"); //  这里最好明确的写允许的域名

        JSONArray powerPoints = new JSONArray();


        String date = request.getParameter("date");
        String meterId = request.getParameter("meterId");

        SimpleDateFormat sdfLite = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date startTime = sdfLite.parse(date);//
        long startTimeStamp = startTime.getTime();
        long endTimeStamp = startTimeStamp + 24 * 3600 * 1000-1;
        Date endTime = new Date(endTimeStamp);
        String strStartTime = sdf.format(startTime);
        String strEndTime = sdf.format(endTime);

        // 创建POST请求
        // 创建HttpClient实例
//        HttpClient httpClient = HttpClientBuilder.create().build();
        org.apache.http.client.HttpClient httpClient = HttpClientBuilder.create().build();

        HttpPost httpPost = new HttpPost("http://gateway.xrny-os-core.sxxrny.com/dwjcys/iotApi/getRealDataByDate");

        //post参数体构建
        JSONObject params = new JSONObject();

        JSONObject objParam = new JSONObject();
        objParam.put("meterId", meterId);
        objParam.put("startTime", strStartTime);
        objParam.put("endTime", strEndTime);
        params.put("param", objParam);

        params.put("page", 1);
        params.put("size", 1000);

        System.out.println(params.toJSONString());

        httpPost.setEntity(new StringEntity(params.toJSONString()));

        httpPost.addHeader("Accept", "application/json, text/plain, */*");
        httpPost.addHeader("Accept-Encoding", "gzip, deflate");
        httpPost.addHeader("Accept-Language", "zh-CN,zh;q=0.9");
        httpPost.addHeader("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.eyJsb2dpbl91c2VyX2tleSI6Ijc3ZTAyNWEzLTcxMWEtNGMxNS05Yjg5LTQwODA3ZjQzMTlhYyJ9.UsGBP097dnzzxpGJLSaTfsSUF1IbZlISedTNm9woT5r-mlyZWAye9n6mkcNVB9vbIa6Zt4vn4KiRzReqekLI3w");
        httpPost.addHeader("Connection", "keep-alive");
        httpPost.addHeader("Content-Type", "application/json; charset=UTF-8");
        httpPost.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 SE 2.X MetaSr 1.0");
        httpPost.addHeader("orgId", "08b460f496b0433f950b6db6903d1e98");

        try {
            // 发送请求并获取响应
            HttpResponse httpResponse = httpClient.execute(httpPost);

            String strResponse = EntityUtils.toString(httpResponse.getEntity());

            JSONObject jsonResponse = JSONObject.parseObject(strResponse);
            JSONObject jsonResult = jsonResponse.getJSONObject("result");
            JSONArray jsonRecords = jsonResult.getJSONArray("records");
//            System.out.println(jsonRecords);

            for (int i = 0; i < jsonRecords.size(); i++) {
                powerPoints.add(jsonRecords.getJSONObject(i));
            }
        } catch (ClientProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return powerPoints.toJSONString();

    }
}

