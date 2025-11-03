package org.example.weiwang.mysql;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.sql.*;


// 定时充放数据的计算
public class DB5 {
    private final String url = "jdbc:mysql://localhost:3306/Malong?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8";
    private final String user = "root";
    private final String password = "Ftt@123mysql.";
    private Connection conn;

    public DB5() {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //查询一个月每天的数据
    public JSONArray monthdata(String equipmentCode, String date) {
        JSONArray json = new JSONArray();
        String sql = "SELECT equipmentCode, collectTime, sharp_peakPower, peakPower, flatPower, valleyPower, totalPower, " +
                "sharp_peakCost, peakCost, flatCost, valleyCost, totalCost " +
                "FROM time1month WHERE equipmentCode=? AND DATE_FORMAT(collectTime,'%Y-%m')=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, equipmentCode);
            ps.setString(2, date);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("equipmentCode", rs.getString("equipmentCode"));
                obj.put("collectTime", rs.getString("collectTime"));
                obj.put("sharp_peakPower", rs.getDouble("sharp_peakPower"));
                obj.put("peakPower", rs.getDouble("peakPower"));
                obj.put("flatPower", rs.getDouble("flatPower"));
                obj.put("valleyPower", rs.getDouble("valleyPower"));
                obj.put("totalPower", rs.getDouble("totalPower"));
                obj.put("sharp_peakCost", rs.getDouble("sharp_peakCost"));
                obj.put("peakCost", rs.getDouble("peakCost"));
                obj.put("flatCost", rs.getDouble("flatCost"));
                obj.put("valleyCost", rs.getDouble("valleyCost"));
                obj.put("totalCost", rs.getDouble("totalCost"));
                json.add(obj);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询设备 " + equipmentCode + " 月数据失败：" + e.getMessage(), e);
        }
        return json;
    }


    public void insertMonth(String equipmentCode, String collectTime, double sharp_peakPower, double peakPower, double flatPower, double valleyPower,
                            double totalPower, double sharp_peakCost, double peakCost, double flatCost, double valleyCost, double totalCost) {

        String sql = "UPDATE time1year SET " +
                "sharp_peakPower = ?, " +
                "peakPower = ?, " +
                "flatPower = ?, " +
                "valleyPower = ?, " +
                "totalPower = ?, " +
                "sharp_peakCost = ?, " +
                "peakCost = ?, " +
                "flatCost = ?, " +
                "valleyCost = ?, " +
                "totalCost = ? " +
                "WHERE equipmentCode = ? AND collectTime= ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, sharp_peakPower);
            ps.setDouble(2, peakPower);
            ps.setDouble(3, flatPower);
            ps.setDouble(4, valleyPower);
            ps.setDouble(5, totalPower);
            ps.setDouble(6, sharp_peakCost);
            ps.setDouble(7, peakCost);
            ps.setDouble(8, flatCost);
            ps.setDouble(9, valleyCost);
            ps.setDouble(10, totalCost);
            ps.setString(11, equipmentCode);
            ps.setString(12, collectTime);  // collectTime 是 yyyy-MM 格式

            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("更新成功：" + equipmentCode + "  月份：" + collectTime);
            } else {
                System.out.println("未找到匹配记录，未更新：" + equipmentCode + "  月份：" + collectTime);
            }

        } catch (SQLException e) {
            throw new RuntimeException("更新 time1year 表失败：" + e.getMessage(), e);
        }
    }

    //查询一个月每天的数据
    public JSONArray querydate(String equipmentCode, String month, String year) {
        JSONArray json = new JSONArray();

        // 查询月度数据
        String monthSql = "SELECT equipmentCode, collectTime, totalPower " +
                "FROM time1month " +
                "WHERE equipmentCode=? AND DATE_FORMAT(collectTime, '%Y-%m')=?";

        String yearSql = "SELECT equipmentCode, collectTime, totalPower " +
                "FROM time1year " +
                "WHERE equipmentCode=? AND LEFT(collectTime,4)=?";

        try {
            // 查询月度数据
            try (PreparedStatement ps = conn.prepareStatement(monthSql)) {
                ps.setString(1, equipmentCode);
                ps.setString(2, month);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject obj = new JSONObject();
                    obj.put("equipmentCode", rs.getString("equipmentCode"));
                    obj.put("collectTime", rs.getString("collectTime"));
                    obj.put("totalPower", rs.getDouble("totalPower"));
                    obj.put("dataType", "month"); // 标记为月度数据
                    json.add(obj);
                }
            }

            // 查询年度数据
            try (PreparedStatement ps = conn.prepareStatement(yearSql)) {
                ps.setString(1, equipmentCode);
                ps.setString(2, year);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject obj = new JSONObject();
                    obj.put("equipmentCode", rs.getString("equipmentCode"));
                    obj.put("collectTime", rs.getString("collectTime"));
                    obj.put("totalPower", rs.getDouble("totalPower"));
                    obj.put("dataType", "year"); // 标记为年度数据
                    json.add(obj);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询设备数据失败：" + e.getMessage(), e);
        }

        return json;
    }


    //每年定时插入12个月月份
    public void insertWaizuDeviceDate(String deviceCode, String collectTime) throws SQLException {
        String sql = "INSERT INTO time1year (equipmentCode, collectTime) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE collectTime = collectTime";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceCode);
            ps.setString(2, collectTime);
            ps.executeUpdate();
        }
    }





}
