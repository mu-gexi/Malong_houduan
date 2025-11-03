package org.example.weiwang.mysql;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.sql.*;


// 定时充放数据的计算
public class DB4 {
    private final String url = "jdbc:mysql://localhost:3306/Malong?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8";
    private final String user = "root";
    private final String password = "Ftt@123mysql.";
    private Connection conn;

    public DB4() {
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

    // 计算负载的电量
    public JSONArray queryLoad(String start,String end) {
        String sql = "select * from nub5m where collectTime>=? and collectTime<=? and equipmentCode in " +
                "('08102100002956','999999999','08102100002146','08102100002009')";
        JSONArray json = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start);
            ps.setString(2, end);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("equipmentCode", rs.getString("equipmentCode"));
                obj.put("collectTime", rs.getString("collectTime"));
                obj.put("recharge", rs.getDouble("recharge"));
                obj.put("disrecharge", rs.getDouble("disrecharge"));
                json.add(obj);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

    // 插入负载的电量
    public void insertLoad(String equipmentCode, String collectTime, double load) {
        String sql = "insert into nub5m(equipmentCode,collectTime,disrecharge) values(?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, equipmentCode);
            ps.setString(2, collectTime);
            ps.setDouble(3, load);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    //定时充放的
    public void calculateLoad(JSONObject data) {
        String sql = "update nub5m set recharge1=?,disrecharge1=? where equipmentCode=? and collectTime=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, data.getDouble("recharge1"));
            ps.setDouble(2, data.getDouble("disrecharge1"));
            ps.setString(3, data.getString("equipmentCode"));
            ps.setString(4, data.getString("collectTime"));
            int row = ps.executeUpdate();
            if (row > 0) {
                System.out.println("{}定时充放数据插入成功");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    //未使用
    public JSONArray queryDing(String date) {
        String sql = "SELECT equipmentCode, collectTime, activePower, positiveActive, reverseActive FROM time5m " +
                "WHERE equipmentCode IN ('08102100002321','08102100002922','08102100002146') " +
                "AND DATE(collectTime) = ? " +
                "UNION ALL " +
                "SELECT equipmentCode, collectTime, activePower, positiveActive, reverseActive FROM ioc " +
                "WHERE equipmentCode IN ('000000001','000000002','000000003') " +
                "AND DATE(collectTime) = ?";
        JSONArray json = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date);
            ps.setString(2, date);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("equipmentCode", rs.getString("equipmentCode"));
                obj.put("collectTime", rs.getString("collectTime"));
                obj.put("activePower", rs.getDouble("activePower"));
                obj.put("positiveActive", rs.getDouble("positiveActive"));
                obj.put("reverseActive", rs.getDouble("reverseActive"));
                json.add(obj);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

    //未使用
    public void calculateDing(String equipmentCode, String collectTime, double recharge1, double disrecharge1) {
        String sql = "update nub5m set recharge1=?,disrecharge1=? where equipmentCode=? and collectTime=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, recharge1);
            ps.setDouble(2, disrecharge1);
            ps.setString(3, equipmentCode);
            ps.setString(4, collectTime);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    //插入计算后外租单位的数据
    public void waizu(String equipmentCode, String collectTime, double sharp_peakPower, double peakPower, double flatPower, double valleyPower,
                      double totalPower, double sharp_peakCost, double peakCost, double flatCost, double totalCost,double valleyCost) {
        // 修改 SQL 语句为 update 操作
        String sql = "UPDATE waizu SET "
                + "sharp_peakPower=?, peakPower=?, flatPower=?, valleyPower=?, "
                + "totalPower=?, sharp_peakCost=?, peakCost=?, flatCost=?,valleyCost=?, totalCost=? "
                + "WHERE equipmentCode=? AND collectTime=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // 设置参数值
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
            ps.setString(12, collectTime);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
//                System.out.println("数据更新成功");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    //查询外租单位的数据
    public JSONArray queryWaizu(String equipmentCode, String date) {
        String sql = "select * from time1month where equipmentCode=? and DATE_FORMAT(collectTime,'%Y-%m')=?";
        JSONArray json = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, equipmentCode);
            ps.setString(2, date);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("equipmentCode", rs.getString("equipmentCode"));
                obj.put("collectTime", rs.getString("collectTime"));
                obj.put("sharp_peakPower", rs.getObject("sharp_peakPower"));
                obj.put("peakPower", rs.getObject("peakPower"));
                obj.put("flatPower", rs.getObject("flatPower"));
                obj.put("valleyPower", rs.getObject("valleyPower"));
                obj.put("totalPower", rs.getObject("totalPower"));
                obj.put("sharp_peakCost", rs.getObject("sharp_peakCost"));
                obj.put("peakCost", rs.getObject("peakCost"));
                obj.put("flatCost", rs.getObject("flatCost"));
                obj.put("valleyCost", rs.getObject("valleyCost"));
                obj.put("totalCost", rs.getObject("totalCost"));
                json.add(obj);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return json;
    }


    //每月定时插入天数
    public void insertWaizuDeviceDate(String deviceCode, String collectTime) throws SQLException {
        // SQL：插入基础记录，重复时跳过（避免主键冲突）
        String sql = "INSERT INTO time1month (equipmentCode, collectTime) " +
                "VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE equipmentCode = equipmentCode";

        // 预编译SQL（try-with-resources自动关闭资源）
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceCode); // 传入具体waizu设备编号
            ps.setString(2, collectTime); // 传入当月日期
            ps.executeUpdate(); // 执行：成功返回1，重复返回0（无报错）
        } catch (SQLException e) {
            // 携带具体设备编号报错，方便快速定位问题
            throw new SQLException("设备[" + deviceCode + "]日期[" + collectTime + "]插入失败", e);
        }
    }

}
