package org.example.weiwang.query;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.weiwang.Config.Config;
import org.example.weiwang.mysql.DB2;
import org.example.weiwang.mysql.DB3;
import org.example.weiwang.mysql.DB4;
import org.example.weiwang.mysql.DB5;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
@CrossOrigin(origins = "*")
@RestController
public class Query {

    // 获取能碳表的数据
    @GetMapping("/Malong")
    private Map<String, List<Object>> query1(HttpServletRequest request, HttpServletResponse response) throws SQLException {
        response.setHeader("Access-Control-Allow-Oring","*");
        String equipment=request.getParameter("equipment");
        String start =request.getParameter("start");
        DB2 db =new DB2();
        Map<String, List<Object>> json=db.AutoTime(equipment,start);
        db.close();
        return json;

    }

    // 获取微网表的数据
    @GetMapping("/IOC")
    private Map<String,List<Object>> query2(HttpServletRequest request,HttpServletResponse response) throws SQLException {
        response.setHeader("Access-Control-Allow-Oring","*");
        String equipment =request.getParameter("equipment");
        String start =request.getParameter("start");

        DB2 db=new DB2();
        Map<String,List<Object>> json=db.AutoTime1(equipment,start);
        db.close();
        return json;
    }
    //只获取两个表的数据，（接口未使用）
    @GetMapping("/queryBit")
    private JSONObject query3(HttpServletRequest request, HttpServletResponse response) throws SQLException {
        response.setHeader("Access-Control-Allow-Oring","*");
        String equipment =request.getParameter("equipment");
        String start =request.getParameter("start");
        String end = request.getParameter("end");
        DB2 db=new DB2();
        JSONObject json=db.AutoTime2(equipment,start);
        db.close();
        return json;
    }

    //获取两个表的数据并对负荷汇总
    @GetMapping("/queryBian")
    private JSONObject query4(HttpServletRequest request, HttpServletResponse response) throws SQLException {
        response.setHeader("Access-Control-Allow-Oring","*");
        String equipment =request.getParameter("equipment");
        String date =request.getParameter("date");
        if(equipment==null || date==null){
            System.out.println("参数不对：equipment,date");
        }
        DB2 db=new DB2();
        JSONObject json=db.AutoTime3(equipment,date);
        db.close();
        return json;
    }


    //光伏、储能、负荷、市电的数据
    @GetMapping("/queryAll")
    private JSONArray query5(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Oring", "*");
        String date = request.getParameter("date");
        JSONArray json = new JSONArray();
        DB3 db = new DB3();

        try {
            for (String device : Config.calculate) {
                JSONArray deviceData = db.getbili(device, date);
                // 将每个设备的数据合并到总结果中
                if (deviceData != null) {
                    json.addAll(deviceData);
                }
            }
        } finally {
            db.close();
        }

        return json;
    }

    //外租单位的电表数据
    @GetMapping("/tableNub")
    public JSONArray tableNub(HttpServletRequest request, HttpServletResponse response){
        response.setHeader("Access-Control-Allow-Oring","*");
        JSONArray json=new JSONArray();
        String meterId=request.getParameter("meter");
        String date=request.getParameter("month");
        DB4 db=new DB4();
        json=db.queryWaizu(meterId,date);
        db.close();
        return json;
    }

//    //外租单位的电表数据每天、每月
//    @GetMapping("/tableNub1")
//    public JSONArray tableNub1(HttpServletRequest request, HttpServletResponse response){
//        response.setHeader("Access-Control-Allow-Oring","*");
//        JSONArray json=new JSONArray();
//        String meterId=request.getParameter("meter");
//        String date=request.getParameter("date");
//        DB5 db=new DB5();
//
//    }
    //外租单位的电表数据每天、每月
    @GetMapping("/tableNub1")
    public JSONArray tableNub1(HttpServletRequest request, HttpServletResponse response){
        response.setHeader("Access-Control-Allow-Origin", "*");

        JSONArray json = new JSONArray();
        String meterId = request.getParameter("meter");
        String date    = request.getParameter("date");

        if(meterId == null || date == null) {
            System.out.println("缺少必要参数: meter 或 date");
            return json;
        }

        // 自动提取年和月
        String month = "";
        String year  = "";
        if(date.length() >= 7){
            month = date.substring(0,7);
            year  = date.substring(0,4);
        } else if(date.length() >= 4){
            month = date.substring(0,4);
            year  = date.substring(0,4);
        }

        DB5 db = new DB5();
        json = db.querydate(meterId, month, year);
        db.close();

        return json;
    }




}
