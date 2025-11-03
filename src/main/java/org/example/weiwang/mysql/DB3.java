package org.example.weiwang.mysql;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.sql.*;
import java.time.LocalDateTime;

public class DB3 {
    private String url = "jdbc:mysql://localhost:3306/Malong?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8";
    private String user = "root";
    private String password="Ftt@123mysql.";
//    private String user="malong";
//    private String password="ftt@123";

    private Connection conn;

    public DB3() {
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

    //计算5分钟的差值
    public JSONArray calculate(String equipmentCode, LocalDateTime date1, LocalDateTime date2) {
        JSONArray json = new JSONArray();
        String sql = "SELECT * FROM time5m " +
                "WHERE equipmentCode = ? " +
                "  AND collectTime >= ? " +
                "  AND collectTime < ? order by collectTime";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, equipmentCode);
            ps.setString(2, String.valueOf(date1));
            ps.setString(3, String.valueOf(date2));

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject jsonRow = new JSONObject();
                jsonRow.put("equipmentCode", rs.getString("equipmentCode"));
                jsonRow.put("collectTime", rs.getTimestamp("collectTime").toLocalDateTime());
                jsonRow.put("positiveActive", rs.getDouble("positiveActive"));
                jsonRow.put("reverseActive", rs.getDouble("reverseActive"));
                json.add(jsonRow);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return json;
    }



    //插入5分钟的差值
    public void deposit(String equipmentCode, String date, double value1, double value2) {
        String sql = "insert into nub5m (equipmentCode,collectTime,recharge,disrecharge) value (?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, equipmentCode);
            ps.setString(2, date);
            ps.setDouble(3, value1);
            ps.setDouble(4, value2);
            int row = ps.executeUpdate();
            if (row > 0) {
//                System.out.println("差值插入成功");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    //获取差值数据
    public JSONArray calculate2(String equipmentCode, String start,String end) {
        String sql = "select * from nub5m where equipmentCode=? and collectTime>=? and collectTime<=?";
        JSONArray json = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, equipmentCode);
            ps.setString(2, start);
            ps.setString(3,end);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                JSONObject jsonRow = new JSONObject();
                jsonRow.put("equipmentCode", rs.getString("equipmentCode"));
                jsonRow.put("collectTime", rs.getString("collectTime"));
                jsonRow.put("recharge", rs.getDouble("recharge"));
                jsonRow.put("disrecharge", rs.getDouble("disrecharge"));
                json.add(jsonRow);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return json;
    }

    //插入每日电量和电费数据
    public void deposit2(String equipmentCode,String collectTime,String recharge,String disrecharge,String earnings,String cost,String energy,String energyCost){
        String sql="insert into calculate (equipmentCode,collectTime,recharge,disrecharge,earnings,cost,energy,energyCost) value (?,?,?,?,?,?,?,?)";
        try(PreparedStatement ps=conn.prepareStatement(sql)){
            ps.setString(1,equipmentCode);
            ps.setString(2,collectTime);
            ps.setString(3,recharge);
            ps.setString(4,disrecharge);
            ps.setString(5,earnings);
            ps.setString(6,cost);
            ps.setString(7,energy);
            ps.setString(8,energyCost);
            int row=ps.executeUpdate();
            if(row>0){
//                System.out.println("电费插入成功");
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    //对变压器功率转换为电量计算
    public JSONArray calculate3 (String equipmentCode,String date){
        String sql = "select equipmentCode, collectTime, activePower from ioc where equipmentCode= ? and Date(collectTime)= ?";
        JSONArray json = new JSONArray();
        try( PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1,equipmentCode);
            ps.setString(2,date);
            ResultSet rs=ps.executeQuery();
            while(rs.next()){
                JSONObject obj=new JSONObject();
                obj.put("equipmentCode",rs.getString("equipmentCode"));
                obj.put("collectTime",rs.getString("collectTime"));
                obj.put("activePower",rs.getDouble("activePower"));
                json.add(obj);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return json;
    }
    public void deposit3(String equipmentCode,String collectTime,Double disrecharge){
        String sql="insert into nub5m(equipmentCode,collectTime,disrecharge)values(?,?,?)";
        try(PreparedStatement ps=conn.prepareStatement(sql)){
            ps.setString(1,equipmentCode);
            ps.setString(2,collectTime);
            ps.setDouble(3,disrecharge);
            int row=ps.executeUpdate();
            if(row>0){
//                System.out.println("5分钟电量插入成功");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONArray calculate4( String date) {
        String sql = "select * from calculate where Date(collectTime)= ?";
        JSONArray json = new JSONArray();
        try( PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1,date);
            ResultSet rs=ps.executeQuery();
            while(rs.next()){
                JSONObject obj=new JSONObject();
                obj.put("equipmentCode",rs.getString("equipmentCode"));
                obj.put("collectTime",rs.getString("collectTime"));
                obj.put("recharge",rs.getString("recharge"));
                obj.put("disrecharge",rs.getString("disrecharge"));
                obj.put("earnings",rs.getString("earnings"));
                obj.put("cost",rs.getString("cost"));
                obj.put("energy",rs.getString("energy"));
                obj.put("energyCost",rs.getString("energyCost"));
                json.add(obj);
            }
            return json;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

//    //插入计算定时充放数据
//    public void deposit4(String equipmentCode, String date,String recharge1,String disrecharge1,String earnings1,String cost1,String energy1,String energyCost) {
//          String sql="update calculate set recharge1= ?, disrecharge1= ?, earnings1= ?, cost1= ?, energy1= ?, energyCost=? "+
//                  "where equipmentCode=? and Date(collectTime)=?";
//          try( PreparedStatement ps = conn.prepareStatement(sql)){
//              ps.setString(1,recharge1);
//              ps.setString(2,disrecharge1);
//              ps.setString(3,earnings1);
//              ps.setString(4,cost1);
//              ps.setString(5,energy1);
//              ps.setString(6,energyCost);
//              ps.setString(7,equipmentCode);
//              ps.setString(8,date);
//              int row=ps.executeUpdate();
//              if(row>0){
//                  System.out.println("定时充放数据插入成功");
//              }
//          } catch (SQLException e) {
//              throw new RuntimeException(e);
//          }
//    }

    //插入光伏、储能、变压器汇总的数据
    public void deposit4(String equipmentCode, String date, String recharge, String disrecharge, String earnings, String cost,
                         String peak, String height, String valley, String flat, String peak1, String height1, String valley1, String flat1){
        // 修复：将13个问号改为14个问号
        String sql="insert into counter (equipmentCode,collectTime,recharge,disrecharge,earnings,cost,peak,height,valley,flat,peak1,height1,valley1,flat1) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement ps=conn.prepareStatement(sql)){
            ps.setString(1,equipmentCode);
            ps.setString(2,date);
            ps.setString(3,recharge);
            ps.setString(4,disrecharge);
            ps.setString(5,earnings);
            ps.setString(6,cost);
            ps.setString(7,peak);
            ps.setString(8,height);
            ps.setString(9,valley);
            ps.setString(10,flat);
            ps.setString(11,peak1);
            ps.setString(12,height1);
            ps.setString(13,valley1);
            ps.setString(14,flat1);
            int row=ps.executeUpdate();
            if(row>0){
//                System.out.println("电量数据插入成功");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    //获取电费电量数据，前端获取每日电量和电费数据
    public JSONArray getbili(String equipmentCode, String date){
        String sql="select * from calculate where equipmentCode=? and Date(collectTime)=?";
        JSONArray json=new JSONArray();
        try(PreparedStatement ps=conn.prepareStatement(sql)){
            ps.setString(1,equipmentCode);
            ps.setString(2,date);
            ResultSet rs=ps.executeQuery();
            while(rs.next()){
                JSONObject obj=new JSONObject();
                obj.put("equipmentCode",rs.getString("equipmentCode"));
                obj.put("collectTime",rs.getString("collectTime"));
                obj.put("recharge",rs.getString("recharge"));
                obj.put("disrecharge",rs.getString("disrecharge"));
                obj.put("earnings",rs.getString("earnings"));
                obj.put("cost",rs.getString("cost"));
                obj.put("energy",rs.getString("energy"));
                obj.put("energyCost",rs.getString("energyCost"));
                json.add(obj);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return json;
    }





}
