package org.example.weiwang.mysql;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.weiwang.Config.Config;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DB2 {
    private Connection conn;


    private String url="jdbc:mysql://localhost:3306/Malong?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8";
    private String user="root";
    private String password="Ftt@123mysql.";
//    private String user="malong";
//    private String password="ftt@123";

    public DB2() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // 加载 MySQL 驱动
            conn = DriverManager.getConnection(url, user, password);
//            System.out.println("数据库连接成功");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
//                System.out.println("数据库连接已关闭");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



//    public Map<String, List<Object>> AutoTime(String equipmentCode, String  date) throws SQLException {
//        Map<String, List<Object> > map=new HashMap<>();
//        String sql="select equipmentCode,collectTime,activePower,positiveActive from time5m where equipmentCode= ? and Date(collectTime)=?";
//         try (PreparedStatement ps = conn.prepareStatement(sql)) {
//             ps.setString(1, equipmentCode);
//             ps.setString(2, date);
//             ResultSet rs = ps.executeQuery();
//
//             ResultSetMetaData metaData=rs.getMetaData();
//             int columnCount=metaData.getColumnCount();
//
//             for(int i=1;columnCount>=i;i++){
//                 String columnName=metaData.getColumnName(i);
//                 map.put(columnName,new ArrayList<>());
//             }
//             while(rs.next()){
//                 for(int i=1;columnCount>=i;i++){
//                     String columnName=metaData.getColumnName(i);
//                     Object value=rs.getObject(i);
//                     map.get(columnName).add(value);
//                 }
//             }
//         }
//        return map;
//    }


//获取能碳数据
public Map<String, List<Object>> AutoTime(String equipmentCode, String  start) throws SQLException {
    Map<String, List<Object> > map=new HashMap<>();
    String sql="select * from time5m where equipmentCode= ? and Date(collectTime)=? order by collectTime";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, equipmentCode);
        ps.setString(2, start);
        ResultSet rs = ps.executeQuery();

        ResultSetMetaData metaData=rs.getMetaData();
        int columnCount=metaData.getColumnCount();

        for(int i=1;columnCount>=i;i++){
            String columnName=metaData.getColumnName(i);
            map.put(columnName,new ArrayList<>());
        }
        while(rs.next()){
            for(int i=1;columnCount>=i;i++){
                String columnName=metaData.getColumnName(i);
                Object value=rs.getObject(i);
                map.get(columnName).add(value);
            }
        }
    }
    return map;
}

    //获取微网数据
    public Map<String,List<Object>> AutoTime1(String equipmentCode,String start) throws SQLException {
        String sql ="select * from ioc where equipmentCode= ? and Date(collectTime)=? order by collectTime";
        Map<String,List<Object>> map = new HashMap<>();
        try(PreparedStatement ps= conn.prepareStatement(sql)){
            ps.setString(1,equipmentCode);
            ps.setString(2,start);
            ResultSet rs=ps.executeQuery();

            ResultSetMetaData rsData= rs.getMetaData();//为每一列创建一个数组
            int columnCount = rsData.getColumnCount();//查寻多少列
            for(int i=1;columnCount>=i;i++){
                String columnName=rsData.getColumnName(i);
                map.put(columnName,new ArrayList<>());
            }
            while(rs.next()){
                for(int i=1;columnCount>=i;i++){
                    String columnName=rsData.getColumnName(i);
                    Object value=rs.getObject(i);
                    map.get(columnName).add(value);
                }
            }

        }
        return map;
    }



    //根据表好选择数据库表，获取数据
    public JSONObject AutoTime2(String equipment, String start) throws SQLException {
        // 预定义设备编码数组（用于判断表类型）
        String[] num = {"200000001", "200000002", "200000003", "200000004", "200000005", "200000006",
                "100000001","100000002","100000003","100000004","100000005","100000006","100000007",
                "000000001","000000002","000000003","1111111111","1111111112","1111111113","1111111114","1111111115",
                "1111111116","1111111117","1111111118","1111111119","11111111110"
        };
        JSONObject result = new JSONObject();

        // 遍历 Config.bit 中的每一行
        for (int i = 0; i < Config.bit.length; i++) {
            if (equipment.equals(Config.bit[i][0])) {
                // 获取该行下所有的设备编码
                for (int j = 0; j < Config.bit[i].length; j++) {
                    String codeFromConfig = Config.bit[i][j];
                    boolean isIoc = false;

                    // 判断设备编码是否在 num 中
                    for (String code : num) {
                        if (code.equals(codeFromConfig)) {
                            isIoc = true;
                            break;
                        }
                    }

                    // 构造 SQL 查询语句
                    String sql = isIoc ?
                            "SELECT equipmentCode, collectTime, activePower FROM ioc WHERE equipmentCode = ? AND Date(collectTime) = ? order by collectTime" :
                            "SELECT equipmentCode, collectTime, activePower FROM time5m WHERE equipmentCode = ? AND Date(collectTime) = ? order by collectTime";

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, codeFromConfig);
                        ps.setString(2, start);


                        // 设备编号和功率值用数组存储
                        JSONArray codeArray = new JSONArray();
                        JSONArray powerArray = new JSONArray();
                        JSONArray timeArray = new JSONArray();

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                codeArray.add(rs.getString("equipmentCode"));
                                powerArray.add(rs.getString("activePower"));
                                timeArray.add(rs.getString("collectTime"));
                            }
                        }


                        result.put("equipmentCode" + j, codeArray);
                        result.put("activePower" + j, powerArray);
                        result.put("collectTime" + j, timeArray);
                    }
                }
                break; // 处理完一行后退出循环
            }
        }

        return result;
    }


    //根据表好选择数据库表，获取数据,并把统一时段负荷累加
    public JSONObject AutoTime3(String equipment, String date) throws SQLException {
        // 预定义设备编码数组（用于判断表类型）
        String[] num = {"200000001", "200000002", "200000003", "200000004", "200000005", "200000006",
                "100000001","100000002","100000003","100000004","100000005","100000006","100000007",
                "000000001","000000002","000000003","1111111111","1111111112","1111111113","1111111114","1111111115",
                "1111111116","1111111117","1111111118","1111111119","11111111110"
        };
        JSONObject result = new JSONObject();
        Map<String,Double> timeSum=new HashMap<>();


        for (int i = 0; i < Config.bit.length; i++) {
            if (equipment.equals(Config.bit[i][0])) {
                // 获取该行下所有的设备编码
                for (int j = 0; j < Config.bit[i].length; j++) {
                    String codeFromConfig = Config.bit[i][j];
                    boolean isIoc = false;

                    // 判断设备编码是否在 num 中
                    for (String code : num) {
                        if (code.equals(codeFromConfig)) {
                            isIoc = true;
                            break;
                        }
                    }


                    // 构造 SQL 查询语句
                    String sql = isIoc ?
                            "SELECT equipmentCode, collectTime, activePower FROM ioc WHERE equipmentCode = ? AND DATE(collectTime) = ? order by collectTime" :
                            "SELECT equipmentCode, collectTime, activePower FROM time5m WHERE equipmentCode = ? AND DATE(collectTime) = ? order by collectTime";

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, codeFromConfig);
                        ps.setString(2, date);

                        // 设备编号和功率值用数组存储
                        JSONArray codeArray = new JSONArray();
                        JSONArray powerArray = new JSONArray();
                        JSONArray timeArray = new JSONArray();

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                codeArray.add(rs.getString("equipmentCode"));
                                powerArray.add(rs.getString("activePower"));
                                timeArray.add(rs.getString("collectTime"));
                            }
                        }


                        result.put("equipmentCode" + j, codeArray);
                        result.put("activePower" + j, powerArray);
                        result.put("collectTime" + j, timeArray);
                    }
                }
                break; // 处理完一行后退出循环
            }
        }
        for (int i = 0; i < Config.bian1.length; i++) {
            if(equipment.equals(Config.bian1[i][0])){
                for(int j=1;j<Config.bian1[i].length;j++){
                    String countName=Config.bian1[i][j];
                    String sql="select collectTime,activePower from time5m where equipmentCode=? and Date(collectTime) =?";
                    try(PreparedStatement ps = conn.prepareStatement(sql)){
                        ps.setString(1,countName);
                        ps.setString(2,date);
//
                        try(ResultSet rs = ps.executeQuery()){
                            while(rs.next()){
                                String collectTime=rs.getString("collectTime");
                                double activePower=rs.getDouble("activePower");
                                timeSum.put(collectTime,timeSum.getOrDefault(collectTime,0.0)+activePower);
                            }
                        }
                    }
                }
                break;
            }

        }
        if(!timeSum.isEmpty()){
            JSONArray sumTime =new JSONArray();
            JSONArray sumPower =new JSONArray();
            for(Map.Entry<String,Double> entry :timeSum.entrySet()){
                sumTime.add(entry.getKey());
                sumPower.add(entry.getValue());
            }
            result.put("collectTime4",sumTime);
            result.put("activePower4",sumPower);
        }

        return result;
    }
}
