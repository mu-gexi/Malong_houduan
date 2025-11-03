package org.example.weiwang.Config;
//此文件已淘汰
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.util.*;
//模拟登录获取最终cookie、Authorization
@Controller
public class CompleteLoginClient {
    private static final Logger logger = LoggerFactory.getLogger(CompleteLoginClient.class);

    private static final RestTemplate restTemplate = new RestTemplate();

    /**
     * 第一次账号登录接口，获取 tokenA
     */
    public static String loginFirstAccount(String username, String password) {
        String loginUrl = "https://cloud.huamod.com/admin_apis/auth/oauth/token"; // 第一次登录接口

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", username);
        formData.add("password", password);
        formData.add("verifycode", "0543");
        formData.add("grant_type", "password");
        formData.add("client_id", "user");
        formData.add("client_secret", "h0u2a!2mo)2d");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, request, Map.class);

//        System.out.println("=== 第一次登录响应头 ===");
        response.getHeaders().forEach((k,v) -> System.out.println(k + ": " + v));

        Map data = (Map) response.getBody().get("data");
        String tokenHead = (String) data.get("tokenHead");
        String token = (String) data.get("token");
        String tokenA = tokenHead + token;

//        System.out.println("第一次登录成功，tokenA = " + tokenA);
        return tokenA;
    }

    /**
     * 第二个接口，获取 iocUrl
     */
    public static Map<String, Object> getIocUrl(String tokenA, String stationId) {
        String url2 = "https://cloud.huamod.com/admin_apis/resource/station/ioc/url"; // 第二个接口

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", tokenA);

        Map<String, String> body = new HashMap<>();
        body.put("stationId", stationId);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url2, request, Map.class);

//        System.out.println("=== 第二个接口响应头 ===");
        response.getHeaders().forEach((k,v) -> System.out.println(k + ": " + v));

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        if (data != null && !data.isEmpty()) {
            String iocUr = (String) data.get(0).get("url");
//            System.out.println("接口返回 iocUrl = " + iocUr);
            String pars[]=iocUr.split("/");
            String iocUrl ="proxy_sst="+pars[4];
            String ioc =pars[2];
            Map<String,Object> map=new HashMap<>();
            map.put("iocUrl",iocUrl);
            map.put("ioc",ioc);
//            System.out.println("获取到 iocUrl = " + iocUrl);
            return map;
        }

        throw new RuntimeException("第二个接口未返回 iocUrl");
    }

    /**
     * 第二次账号登录接口，获取 tokenB 和 proxy_sst
     */
    public static String loginSecondAccount(String username, String password, String urlPartCookie) {
        String loginUrl = "http://ioc-733eb65e.hi-link.huamod.com/apis/user/authentication";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", urlPartCookie); // 请求头只带 Cookie

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", username);
        formData.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, request, Map.class);

        Map data = (Map) response.getBody().get("result");
        String tokenB = (String) data.get("token"); // 直接返回 token

        return tokenB;
    }





    //获取最终cookie
    public static String fetchIoCookie(String tokenB, String proxySst,String ioc) {
        String pageUrl = "http://"+ ioc + "/socket.io/?jwt_token=huamod" + tokenB + "&EIO=3&transport=polling&t=PaIM9jP";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", proxySst);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(pageUrl, HttpMethod.GET, request, String.class);

//        System.out.println("=== 访问 login_nopassword 页面响应头 ===");
        response.getHeaders().forEach((k,v) -> System.out.println(k + ": " + v));

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        String io = null;
        if (setCookies != null) {
            for (String c : setCookies) {
                if (c.startsWith("io=")) {
                    io = c.split(";", 2)[0];
                    break;
                }
            }
        }

        if (io == null) {
            throw new RuntimeException("未获取到 io，请检查 URL 是否正确");
        }

//        System.out.println("获取到 io = " + io);
        return io;
    }



    @ResponseBody
    @GetMapping(value = "/token")
    public Map<String,Object> token() {
        // 第一次登录
        String tokenA = loginFirstAccount("sundawei", "268a4d1cd6f15fb2ef53764db6f21107");

        // 第二个接口获取 iocUrl
        String stationId = "HM4406062505074";
        Map<String, Object> data = getIocUrl(tokenA, stationId);
        String iocUrl=(String)data.get("iocUrl");
        String ioc=(String)data.get("ioc");
        // 第二次登录
        String tokenB = loginSecondAccount("huamod", "9178c3a044f580722c95eb576f2d177d", iocUrl);
//        System.out.println("第二次登录成功，tokenB = " + tokenB);

        // 访问 login_nopassword 页面获取 io
        String io = fetchIoCookie(tokenB, iocUrl,ioc);

        // 拼接完整 Cookie
        String Cookie = iocUrl + "; " + io;
        Map<String,Object> map=new HashMap<>();
        map.put("Cookie",Cookie);
        map.put("tokenB",tokenB);

//        System.out.println("完整 Cookie = " + Cookie);
        logger.info("Cookie是{}，tokenB是{}",Cookie,tokenB);
        System.out.println(map);
        return map;

//        // 调用最终接口
//        String finalUrl = "https://example.com/api/final";
//        callFinalApi(finalUrl, tokenB, fullCookie);
//

    }
}
