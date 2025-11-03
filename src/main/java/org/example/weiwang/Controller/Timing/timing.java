//package org.example.weiwang.Controller.Timing;
//
//
//import jdk.jfr.Enabled;
//import org.example.weiwang.Config.Config;
//import org.example.weiwang.Controller.Waizhu;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//
//@EnableScheduli
//@SpringBootApplicati
//public class timing {
//    @Scheduled(cron = "0 */1 * * * ?")
//    public void getBin() {
//        for (String[] device : Config.devices) {
//            try {
//                String meterId = device[0];
////                String result =
//                        Waizhu.getRung(meterId);
//                        System.out.println("插入成功");
////                logger.info("电表 {} 本次获取数据总量: {}", meterId, JSONArray.parseArray(result).size());
//            } catch (Exception e) {
////                logger.error("定时获取电表 {} 数据失败", device[0], e);
//            }
//        }
//    }
//
//}
