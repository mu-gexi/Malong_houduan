package org.example.weiwang.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Controller
public class CompleteLoginClient1 {
    private static final Logger logger = LoggerFactory.getLogger(CompleteLoginClient1.class);
    private static final RestTemplate restTemplate = new RestTemplate();

    // Token 缓存和上次获取时间
    private static String cachedTokenB = null;
    private static String cachedCookie = null;
    private static LocalDateTime lastTokenFetchTime = null;
    private static String cachedIOCurl=null;

    /**
     * 检查 token 是否过期
     */
    private static boolean isTokenExpired() {
        return lastTokenFetchTime == null || lastTokenFetchTime.isBefore(LocalDateTime.now().minusMinutes(60));
    }

    /**
     * 获取或更新 Token
     */
    private static void refreshToken() {
        if (isTokenExpired()) {
            // 第一次登录
            String tokenA = loginFirstAccount("sundawei", "268a4d1cd6f15fb2ef53764db6f21107");

            // 第二个接口获取 iocUrl
            String stationId = "HM4406062505074";
            Map<String, Object> data = getIocUrl(tokenA, stationId);
            String iocUrl = (String) data.get("iocUrl");
            String ioc = (String) data.get("ioc");

            // 第二次登录获取 tokenB
            String tokenB = loginSecondAccount("huamod", "9178c3a044f580722c95eb576f2d177d", iocUrl);

            // 获取 Cookie
            String io = fetchIoCookie(tokenB, iocUrl, ioc);
            String Cookie = iocUrl + "; " + io;

            // 缓存 token 和 Cookie
            cachedTokenB = tokenB;
            cachedCookie = Cookie;
            lastTokenFetchTime = LocalDateTime.now();

            cachedIOCurl=iocUrl;

            logger.info("TokenB 和 Cookie 已更新。TokenB: {}, Cookie: {}", cachedTokenB, cachedCookie);
        } else {
            logger.info("Token 已缓存，无需重新获取");
        }
    }

    /**
     * 第一次账号登录接口，获取 tokenA
     */
    public static String loginFirstAccount(String username, String password) {
        String loginUrl = "https://cloud.huamod.com/admin_apis/auth/oauth/token"; // 第一次登录接口

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);// 设置请求头为表单类型

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", username);
        formData.add("password", password);
        formData.add("verifycode", "0543");
        formData.add("grant_type", "password");
        formData.add("client_id", "user");
        formData.add("client_secret", "h0u2a!2mo)2d");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, request, Map.class);

        Map data = (Map) response.getBody().get("data");
        String tokenHead = (String) data.get("tokenHead");
        String token = (String) data.get("token");
        return tokenHead + token;
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

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        if (data != null && !data.isEmpty()) {
            String iocUrl = (String) data.get(0).get("url");
            String[] pars = iocUrl.split("/");
            String ioc = pars[2];
            Map<String, Object> map = new HashMap<>();
            map.put("iocUrl", "proxy_sst=" + pars[4]);
            map.put("ioc", ioc);
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
        headers.set("Cookie", urlPartCookie);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", username);
        formData.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, request, Map.class);

        Map data = (Map) response.getBody().get("result");
        return (String) data.get("token");
    }

    /**
     * 获取最终 cookie
     */
    public static String fetchIoCookie(String tokenB, String proxySst, String ioc) {
        String pageUrl = "http://" + ioc + "/socket.io/?jwt_token=huamod" + tokenB + "&EIO=3&transport=polling&t=PaIM9jP";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", proxySst);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(pageUrl, HttpMethod.GET, request, String.class);

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

        return io;
    }

    @ResponseBody
    @GetMapping(value = "/token1")
    public Map<String, Object> token() {
        // 在获取 token 之前，首先检查是否需要刷新 token
        refreshToken();

        // 返回缓存的 token 和 Cookie
        Map<String, Object> map = new HashMap<>();
        map.put("Cookie", cachedCookie);
        map.put("tokenB", cachedTokenB);
        map.put("iocUrl",cachedIOCurl);
        return map;
    }
}
