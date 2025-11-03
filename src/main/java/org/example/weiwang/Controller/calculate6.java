//package org.example.weiwang.Controller;
//
//import com.alibaba.fastjson.JSONArray;
//import com.alibaba.fastjson.JSONObject;
//import org.example.weiwang.mysql.DB4;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.LocalTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//
///**
// * 储能定时充放策略计算控制器
// * 功能：处理两组设备（储能、市电、光伏、负荷）的5分钟间隔数据，计算并更新各设备电量
// * 核心特点：
// * 1. 光伏、负荷使用单表原始数据（无需累加，每组仅一个对应表）
// * 2. 储能、市电根据时段策略计算电量（充电/放电/不充不放）
// * 3. 所有设备均属于两组内，无需处理外部设备
// */
//@RestController
//public class calculate6 {
//
//    private static final Logger logger = LoggerFactory.getLogger(calculate6.class);
//
//    // ================== 设备编码常量（两组内包含所有设备） ==================
//    // 第一组设备
//    private static final String GROUP1_STORAGE = "08102100002321";      // 储能设备1（需计算充放电）
//    private static final String GROUP1_LOAD = "08102100002009";        // 负荷设备1（透传原始数据）
//    private static final String GROUP1_PV = "08102100002146";          // 光伏设备1（透传原始数据）
//    private static final String GROUP1_GRID = "000000002";             // 市电设备1（需计算下网电量）
//
//    // 第二组设备
//    private static final String GROUP2_STORAGE = "08102100002922";      // 储能设备3（需计算充放电）
//    private static final String GROUP2_LOAD = "999999999";             // 负荷设备3（透传原始数据）
//    private static final String GROUP2_PV = "08102100002956";          // 光伏设备3（透传原始数据）
//    private static final String GROUP2_GRID = "000000001";             // 市电设备3（需计算下网电量）
//
//    // 储能核心参数（数组含义：[0]容量(kWh), [1]5分钟最大充电电量(kWh), [2]5分钟最大放电电量(kWh)）
//    private static final Map<String, double[]> STORAGE_PARAMS = new HashMap<String, double[]>() {{
//        put(GROUP1_STORAGE, new double[]{1400.0, 55.0, 55.0});  // 储能1：容量1400kWh，5分钟最大充/放电55kWh
//        put(GROUP2_STORAGE, new double[]{932.0, 36.67, 36.67}); // 储能3：容量932kWh，5分钟最大充/放电≈36.67kWh
//    }};
//
//    // ================== 常量定义 ==================
//    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // 时间格式
//    private static final double MIN_LOAD_ENERGY = 1.67; // 储能放电最小净负荷阈值（5分钟内，单位kWh）
//
//    // 储能实时电量缓存（key：储能设备编码，value：当前电量kWh，按设备独立维护）
//    private Map<String, Double> storageCurrentEnergy = new HashMap<String, Double>() {{
//        put(GROUP1_STORAGE, 0.0); // 初始电量0
//        put(GROUP2_STORAGE, 0.0);
//    }};
//
//    // 定时任务：每分钟执行一次（处理5分钟间隔数据，确保及时更新）
//    @Scheduled(cron = "0 */1 * * * *")
//    public void scheduledTask() {
//        run();
//    }
//
//    /**
//     * 核心执行方法：查询数据→处理数据→计算策略→更新数据库
//     */
//    private void run() {
//        DB4 db = new DB4(); // 数据库操作实例
//        try {
//            // 数据查询范围：昨天全天（避免跨天数据遗漏）
//            LocalDate yesterday = LocalDate.now().minusDays(1);
//            LocalDateTime dayStart = yesterday.atStartOfDay(); // 昨天00:00:00
//            LocalDateTime dayEnd = yesterday.plusDays(1).atStartOfDay(); // 今天00:00:00（不含）
//            JSONArray data = db.queryLoad(dayStart.format(FORMATTER), dayEnd.format(FORMATTER));
//
//            if (data == null || data.isEmpty()) {
//                logger.warn("查询无数据：时间范围{}至{}", dayStart, dayEnd);
//                return;
//            }
//
//            // ================== 1. 存储光伏/负荷单表数据（按时间+分组） ==================
//            // 结构：timeGroupAgg[时间戳][分组] = [光伏电量, 负荷电量]
//            Map<String, Map<String, double[]>> timeGroupAgg = new HashMap<>();
//            for (int i = 0; i < data.size(); i++) {
//                JSONObject obj = data.getJSONObject(i);
//                String timeStr = obj.getString("collectTime"); // 5分钟时间戳（如2025-10-28 10:00:00）
//                String code = obj.getString("equipmentCode");  // 设备编码
//
//                if (timeStr == null || code == null) {
//                    logger.debug("跳过无效数据（时间或设备编码为空）");
//                    continue;
//                }
//
//                // 获取设备所属分组（仅两组）
//                String group = getGroupByCode(code);
//
//
//                // 初始化当前时间+分组的电量容器
//                timeGroupAgg.putIfAbsent(timeStr, new HashMap<>());
//                Map<String, double[]> groupAgg = timeGroupAgg.get(timeStr);
//                groupAgg.putIfAbsent(group, new double[]{0.0, 0.0}); // [0]光伏, [1]负荷
//                double[] agg = groupAgg.get(group);
//
//                // 直接赋值单表数据（无需累加，因每组仅一个光伏/负荷表）
//                if (isPV(code, group)) {
//                    agg[0] = obj.getDoubleValue("recharge"); // 光伏取recharge字段
//                }
//                if (isLoad(code, group)) {
//                    agg[1] = obj.getDoubleValue("disrecharge"); // 负荷取disrecharge字段
//                }
//            }
//
//            // ================== 2. 定义时段策略（一天内的充放电规则） ==================
//            List<LocalTime[]> periods = Arrays.asList(
//                    new LocalTime[]{LocalTime.of(0, 0), LocalTime.of(8, 0)},   // 时段0：00:00-08:00 储能充电
//                    new LocalTime[]{LocalTime.of(8, 0), LocalTime.of(10, 0)},  // 时段1：08:00-10:00 储能放电（优先级2）
//                    new LocalTime[]{LocalTime.of(10, 0), LocalTime.of(12, 0)}, // 时段2：10:00-12:00 储能放电（优先级1）
//                    new LocalTime[]{LocalTime.of(12, 0), LocalTime.of(14, 0)}, // 时段3：12:00-14:00 储能充电
//                    new LocalTime[]{LocalTime.of(14, 0), LocalTime.of(19, 0)}, // 时段4：14:00-19:00 储能放电（优先级1，需放完）
//                    new LocalTime[]{LocalTime.of(19, 0), LocalTime.of(23, 59, 59)} // 时段5：19:00-23:59:59 不充不放
//            );
//
//            // ================== 3. 遍历所有设备，计算并更新数据 ==================
//            for (int i = 0; i < data.size(); i++) {
//                JSONObject item = data.getJSONObject(i);
//                String timeStr = item.getString("collectTime");
//                String code = item.getString("equipmentCode");
//                if (timeStr == null || code == null) continue;
//
//                String group = getGroupByCode(code);
//                if (group == null) {
//                    logger.debug("跳过非目标设备：{}", code);
//                    continue;
//                }
//
//                // 获取当前时间+分组的光伏/负荷单表电量
//                Map<String, double[]> groupAgg = timeGroupAgg.getOrDefault(timeStr, new HashMap<>());
//                double[] agg = groupAgg.getOrDefault(group, new double[]{0.0, 0.0});
//                double pvEnergy = agg[0];     // 光伏单表电量（kWh）
//                double loadEnergy = agg[1];   // 负荷单表电量（kWh）
//
//                // 匹配当前时间所属时段，执行对应策略
//                LocalTime time = LocalDateTime.parse(timeStr, FORMATTER).toLocalTime();
//                boolean matched = false; // 标记是否匹配到时段
//                for (int j = 0; j < periods.size(); j++) {
//                    LocalTime[] p = periods.get(j);
//                    // 时间在[p[0], p[1})范围内（包含开始，不含结束）
//                    if ((time.isAfter(p[0]) || time.equals(p[0])) && time.isBefore(p[1])) {
//                        boolean needDrainAll = (j == 4); // 时段4需放完电
//                        // 核心设备（储能/市电）用计算值，非核心（光伏/负荷）用原始值
//                        JSONObject result = isCoreDevice(code)
//                                ? dispatchCoreStrategy(j, item, group, pvEnergy, loadEnergy, needDrainAll)
//                                : getNonCoreResult(item);
//
//                        if (result != null) {
//                            db.calculateLoad(result); // 更新数据库
//                        }
//                        matched = true;
//                        break;
//                    }
//                }
//
//                // 未匹配到任何时段（极端情况），用默认策略（不充不放）
//                if (!matched) {
//                    logger.warn("设备{}在时间{}未匹配到时段，使用默认策略", code, timeStr);
//                    JSONObject result = isCoreDevice(code)
//                            ? (isStorage(code) ? calcStorageIdle(item) : calcGridIdle(item, pvEnergy, loadEnergy))
//                            : getNonCoreResult(item);
//                    db.calculateLoad(result);
//                }
//            }
//
//        } catch (Exception e) {
//            logger.error("计算任务执行失败：", e);
//        }
//    }
//
//    /**
//     * 核心策略分发：根据时段索引和设备类型，调用对应计算方法
//     * @param periodIndex 时段索引（0-5）
//     * @param item 原始数据
//     * @param group 设备分组
//     * @param pvEnergy 光伏电量（单表）
//     * @param loadEnergy 负荷电量（单表）
//     * @param needDrainAll 是否需要放完电（仅时段4为true）
//     * @return 计算结果
//     */
//    private JSONObject dispatchCoreStrategy(int periodIndex, JSONObject item, String group,
//                                            double pvEnergy, double loadEnergy, boolean needDrainAll) {
//        String code = item.getString("equipmentCode");
//        switch (periodIndex) {
//            case 0: case 3: // 充电时段（00:00-08:00、12:00-14:00）
//                return isStorage(code) ? calcStorageCharge(item) : calcGridCharge(item, group, pvEnergy, loadEnergy);
//            case 1: case 2: case 4: // 放电时段（08:00-10:00、10:00-12:00、14:00-19:00）
//                return isStorage(code) ? calcStorageDischarge(item, group, pvEnergy, loadEnergy, needDrainAll) : calcGridDischarge(item, group, pvEnergy, loadEnergy, needDrainAll);
//            case 5: // 不充不放时段（19:00-23:59:59）
//                return isStorage(code) ? calcStorageIdle(item) : calcGridIdle(item, pvEnergy, loadEnergy);
//            default:
//                logger.warn("设备{}匹配到无效时段索引：{}", code, periodIndex);
//                return getNonCoreResult(item);
//        }
//    }
//
//    // ================== 储能设备计算方法 ==================
//
//    /**
//     * 储能充电计算（充电时段调用）
//     * 计算逻辑：实际充电量 = 原始充电量、最大充电量、剩余容量的最小值（取非负）
//     */
//    private JSONObject calcStorageCharge(JSONObject item) {
//        JSONObject result = baseResult(item); // 基础结果（设备编码+时间）
//        String code = item.getString("equipmentCode");
//        double[] params = STORAGE_PARAMS.get(code); // 储能参数（容量、最大充放电量）
//        double currentEnergy = storageCurrentEnergy.get(code); // 当前电量
//
//        // 原始充电电量（从数据中获取）
//        double originalChargeEnergy = item.getDoubleValue("disrecharge");
//
//        // 充电限制：不能超过最大充电量（参数[1]），且不能超过剩余容量（容量-当前电量）
//        double maxChargeEnergy = params[1];
//        double remainCap = params[0] - currentEnergy;
//        double maxAllowChargeEnergy = Math.min(maxChargeEnergy, remainCap);
//
//        // 实际充电电量（取非负，避免负数）
//        double actualChargeEnergy = Math.max(0, Math.min(originalChargeEnergy, maxAllowChargeEnergy));
//
//        // 更新储能实时电量（当前电量+实际充电量）
//        storageCurrentEnergy.put(code, currentEnergy + actualChargeEnergy);
//
//        // 封装结果：充电量存disrecharge1，放电量为0
//        result.put("disrecharge1", actualChargeEnergy);
//        result.put("recharge1", 0.0);
//        setDefaultValues(result); // 确保无null值
//        return result;
//    }
//
//    /**
//     * 储能放电计算（放电时段调用）
//     * 计算逻辑：实际放电量 = 净负荷、最大放电量、当前电量的最小值（需满足净负荷≥阈值）
//     */
//    private JSONObject calcStorageDischarge(JSONObject item, String group, double pvEnergy,
//                                            double loadEnergy, boolean needDrainAll) {
//        JSONObject result = baseResult(item);
//        String code = item.getString("equipmentCode");
//        double[] params = STORAGE_PARAMS.get(code);
//        double currentEnergy = storageCurrentEnergy.get(code);
//        double netLoadEnergy = loadEnergy - pvEnergy; // 净负荷 = 负荷 - 光伏
//
//        // 放电前提：净负荷≥最小阈值（1.67kWh）且当前有电量
//        if (netLoadEnergy < MIN_LOAD_ENERGY || currentEnergy <= 0) {
//            result.put("recharge1", 0.0); // 放电量为0
//            result.put("disrecharge1", 0.0);
//            setDefaultValues(result);
//            return result;
//        }
//
//        // 放电限制：不能超过最大放电量（参数[2]），且不能超过净负荷需求
//        double maxDischargeEnergy = params[2];
//        double demandEnergy = Math.min(netLoadEnergy, maxDischargeEnergy);
//        // 时段4需放完电：额外限制不能超过当前电量
//        if (needDrainAll) {
//            demandEnergy = Math.min(demandEnergy, currentEnergy);
//        }
//
//        // 实际放电电量（取非负）
//        double actualDischargeEnergy = Math.max(0, Math.min(demandEnergy, currentEnergy));
//
//        // 更新储能实时电量（当前电量-实际放电量，不小于0）
//        storageCurrentEnergy.put(code, Math.max(0, currentEnergy - actualDischargeEnergy));
//
//        // 封装结果：放电量存recharge1，充电量为0
//        result.put("recharge1", actualDischargeEnergy);
//        result.put("disrecharge1", 0.0);
//        setDefaultValues(result);
//        return result;
//    }
//
//    /**
//     * 储能不充不放（对应时段5）
//     * 结果：充放电量均为0
//     */
//    private JSONObject calcStorageIdle(JSONObject item) {
//        JSONObject result = baseResult(item);
//        result.put("recharge1", 0.0);
//        result.put("disrecharge1", 0.0);
//        setDefaultValues(result);
//        return result;
//    }
//
//    // ================== 市电设备计算方法 ==================
//
//    /**
//     * 充电时段市电下网电量计算
//     * 公式：市电下网电量 = 负荷 - 光伏 + 储能充电量（取非负）
//     */
//    private JSONObject calcGridCharge(JSONObject item, String group, double pvEnergy, double loadEnergy) {
//        JSONObject result = baseResult(item);
//        String storageCode = getStorageCodeByGroup(group); // 获取同组储能编码
//        double storageChargeEnergy = 0.0;
//
//        // 计算同组储能的实际充电量（用于市电计算）
//        if (STORAGE_PARAMS.containsKey(storageCode)) {
//            double originalChargeEnergy = item.getDoubleValue("disrecharge");
//            double maxChargeEnergy = STORAGE_PARAMS.get(storageCode)[1];
//            double currentEnergy = storageCurrentEnergy.get(storageCode);
//            double remainCap = STORAGE_PARAMS.get(storageCode)[0] - currentEnergy;
//            // 取三者最小值：原始充电量、最大充电量、剩余容量
//            storageChargeEnergy = Math.max(0, Math.min(Math.min(originalChargeEnergy, maxChargeEnergy), remainCap));
//        }
//
//        // 市电下网电量 = 负荷 - 光伏（光伏抵消部分负荷） + 储能充电量（额外从电网取电）
//        double gridEnergy = Math.max(0, loadEnergy - pvEnergy + storageChargeEnergy);
//
//        result.put("disrecharge1", gridEnergy); // 下网电量存disrecharge1
//        result.put("recharge1", 0.0);
//        setDefaultValues(result);
//        return result;
//    }
//
//    /**
//     * 放电时段市电下网电量计算
//     * 公式：市电下网电量 = 负荷 - 光伏 - 储能放电量（取非负）
//     */
//    private JSONObject calcGridDischarge(JSONObject item, String group, double pvEnergy,
//                                         double loadEnergy, boolean needDrainAll) {
//        JSONObject result = baseResult(item);
//        String storageCode = getStorageCodeByGroup(group);
//        double netLoadEnergy = loadEnergy - pvEnergy; // 净负荷
//        double storageDischargeEnergy = 0.0;
//
//        // 计算同组储能的实际放电量（用于市电计算）
//        if (STORAGE_PARAMS.containsKey(storageCode) && netLoadEnergy >= MIN_LOAD_ENERGY) {
//            double maxDischargeEnergy = STORAGE_PARAMS.get(storageCode)[2];
//            double currentEnergy = storageCurrentEnergy.get(storageCode);
//            double demandEnergy = Math.min(netLoadEnergy, maxDischargeEnergy);
//            if (needDrainAll) {
//                demandEnergy = Math.min(demandEnergy, currentEnergy);
//            }
//            storageDischargeEnergy = Math.max(0, demandEnergy);
//        }
//
//        // 市电下网电量 = 负荷 - 光伏（光伏抵消） - 储能放电（储能抵消）
//        double gridEnergy = Math.max(0, loadEnergy - pvEnergy - storageDischargeEnergy);
//
//        result.put("disrecharge1", gridEnergy);
//        result.put("recharge1", 0.0);
//        setDefaultValues(result);
//        return result;
//    }
//
//    /**
//     * 不充不放时段市电下网电量计算
//     * 公式：市电下网电量 = 负荷 - 光伏（取非负）
//     */
//    private JSONObject calcGridIdle(JSONObject item, double pvEnergy, double loadEnergy) {
//        JSONObject result = baseResult(item);
//        double gridEnergy = Math.max(0, loadEnergy - pvEnergy); // 仅光伏抵消负荷，无储能参与
//        result.put("disrecharge1", gridEnergy);
//        result.put("recharge1", 0.0);
//        setDefaultValues(result);
//        return result;
//    }
//
//    // ================== 辅助方法 ==================
//
//    /**
//     * 构建基础结果对象（包含设备编码和时间，确保核心字段存在）
//     */
//    private JSONObject baseResult(JSONObject item) {
//        JSONObject result = new JSONObject();
//        result.put("equipmentCode", item.getString("equipmentCode"));
//        result.put("collectTime", item.getString("collectTime"));
//        return result;
//    }
//
//    /**
//     * 非核心设备（光伏/负荷）结果：透传原始数据
//     * 光伏：recharge1=原始recharge，disrecharge1=0
//     * 负荷：disrecharge1=原始disrecharge，recharge1=0
//     */
//    private JSONObject getNonCoreResult(JSONObject item) {
//        JSONObject result = baseResult(item);
//        String code = item.getString("equipmentCode");
//        String group = getGroupByCode(code);
//
//        if (isPV(code, group)) {
//            // 光伏透传原始充电量（recharge）
//            result.put("recharge1", item.getDoubleValue("recharge"));
//            result.put("disrecharge1", 0.0);
//        } else if (isLoad(code, group)) {
//            // 负荷透传原始放电量（disrecharge）
//            result.put("disrecharge1", item.getDoubleValue("disrecharge"));
//            result.put("recharge1", 0.0);
//        }
//
//        setDefaultValues(result);
//        return result;
//    }
//
//    /**
//     * 设置默认值，避免结果中出现null（确保数据库字段非空）
//     */
//    private void setDefaultValues(JSONObject result) {
//        result.put("recharge1", result.getOrDefault("recharge1", 0.0)); // 默认为0
//        result.put("disrecharge1", result.getOrDefault("disrecharge1", 0.0));
//        result.remove("gridElectricity1"); // 移除无关字段
//    }
//
//    /**
//     * 判断是否为核心设备（需计算的设备：储能、市电）
//     */
//    private boolean isCoreDevice(String code) {
//        return STORAGE_PARAMS.containsKey(code) // 储能设备
//                || GROUP1_GRID.equals(code) || GROUP2_GRID.equals(code); // 市电设备
//    }
//
//    /**
//     * 根据设备编码获取所属分组（仅GROUP1或GROUP2）
//     */
//    private String getGroupByCode(String code) {
//        if (GROUP1_STORAGE.equals(code) || GROUP1_LOAD.equals(code)
//                || GROUP1_PV.equals(code) || GROUP1_GRID.equals(code)) {
//            return "GROUP1";
//        }
//        if (GROUP2_STORAGE.equals(code) || GROUP2_LOAD.equals(code)
//                || GROUP2_PV.equals(code) || GROUP2_GRID.equals(code)) {
//            return "GROUP2";
//        }
//        return null; // 非目标设备（理论上不存在）
//    }
//
//    /**
//     * 根据分组获取同组储能设备编码
//     */
//    private String getStorageCodeByGroup(String group) {
//        return "GROUP1".equals(group) ? GROUP1_STORAGE : GROUP2_STORAGE;
//    }
//
//    /**
//     * 判断是否为光伏设备
//     */
//    private boolean isPV(String code, String group) {
//        return ("GROUP1".equals(group) && GROUP1_PV.equals(code))
//                || ("GROUP2".equals(group) && GROUP2_PV.equals(code));
//    }
//
//    /**
//     * 判断是否为负荷设备
//     */
//    private boolean isLoad(String code, String group) {
//        return ("GROUP1".equals(group) && GROUP1_LOAD.equals(code))
//                || ("GROUP2".equals(group) && GROUP2_LOAD.equals(code));
//    }
//
//    /**
//     * 判断是否为储能设备
//     */
//    private boolean isStorage(String code) {
//        return STORAGE_PARAMS.containsKey(code);
//    }
//}