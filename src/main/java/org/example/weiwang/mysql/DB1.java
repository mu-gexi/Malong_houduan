package org.example.weiwang.mysql;

import java.sql.*;
import java.util.Map;

public class DB1 {
    private Connection conn;
//    private Statement st;

    private String url="jdbc:mysql://localhost:3306/Malong?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8";
    private String user="root";
    private String password="Ftt@123mysql.";
//    private String user="malong";
//    private String password="ftt@123";

    public DB1() {
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


    public void Auto(String equipmentCode, String collectTime, double activePower,double positiveActive,double reverseActive,double activePowerA,double activePowerB,double activePowerC,double currentA,double currentB,double currentC){

        String sql="insert into time5m (equipmentCode,collectTime,activePower,positiveActive,reverseActive,activePowerA,activePowerB,activePowerC,currentA,currentB,currentC) value (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, equipmentCode);
            ps.setString(2, collectTime);
            ps.setDouble(3, activePower);
            ps.setDouble(4,positiveActive);
            ps.setDouble(5,reverseActive);
            ps.setDouble(6,activePowerA);
            ps.setDouble(7,activePowerB);
            ps.setDouble(8,activePowerC);
            ps.setDouble(9,currentA);
            ps.setDouble(10,currentB);
            ps.setDouble(11,currentC);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void saveOrUpdatePowerData(String equipmentCode, String collectTime, double value, String targetField) throws SQLException {
        // 1. 先尝试更新已存在的记录（通过表号和时间匹配）
        String updateSql = switch (targetField) {
            case "activePower" -> "UPDATE ioc SET activePower = ? WHERE equipmentCode = ? AND collectTime = ?";
            case "bilibiliPower" -> "UPDATE ioc SET bilibiliPower = ? WHERE equipmentCode = ? AND collectTime = ?";
            case "monthPower" -> "UPDATE ioc SET monthPower = ? WHERE equipmentCode = ? AND collectTime = ?";
            case "totalPower" -> "UPDATE ioc SET totalPower = ? WHERE equipmentCode = ? AND collectTime = ?";
            case "day_charging_power" -> "UPDATE ioc SET day_charging_power = ? WHERE equipmentCode = ? AND collectTime = ?";
            case "day_discharging_power" -> "UPDATE ioc SET day_discharging_power = ? WHERE equipmentCode = ? AND collectTime = ?";
            case "total_charging_power" -> "UPDATE ioc SET total_charging_power = ? WHERE equipmentCode = ? AND collectTime = ?";
            case "total_discharging_power" -> "UPDATE ioc SET total_discharging_power = ? WHERE equipmentCode = ? AND collectTime = ?";

            // 新增字段时添加对应的UPDATE语句
            // case "newField" -> "UPDATE ioc SET newField = ? WHERE equipmentCode = ? AND collectTime = ?";
            default -> throw new IllegalArgumentException("未支持的目标字段：" + targetField);
        };

        try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
            updatePs.setDouble(1, value);
            updatePs.setString(2, equipmentCode);
            updatePs.setString(3, collectTime);
            int updatedRows = updatePs.executeUpdate();
            Map<String,Integer> map= Map.of("activePower",3,"bilibiliPower",4,"monthPower",5,"totalPower",6,"day_charging_power",7,"day_discharging_power",8,"total_charging_power",9,"total_discharging_power",10);


            if (updatedRows == 0) {
                // 插入语句包含所有字段
                String insertSql = "INSERT INTO ioc (equipmentCode, collectTime, activePower,bilibiliPower,monthPower,totalPower,day_charging_power,day_discharging_power,total_charging_power,total_discharging_power) " +
                        "VALUES (?, ?, ?,?,?,?,?,?,?,?)";

                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setString(1, equipmentCode);
                    insertPs.setString(2, collectTime);

                    for(int i=3;i<=10;i++){
                        insertPs.setNull(i,java.sql.Types.DOUBLE);
                    }
                    Integer idx=map.get(targetField);
                    if(idx!=null){
                        insertPs.setDouble(idx,value);
                    }

                    int row = insertPs.executeUpdate();
                    if (row > 0) {
                        System.out.println("插入成功");
                    }
                }
            } else {
            }
        }
    }
}



