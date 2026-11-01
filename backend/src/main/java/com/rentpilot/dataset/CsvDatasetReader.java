package com.rentpilot.dataset;

import com.rentpilot.listing.TriState;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

public class CsvDatasetReader {
    private static final List<String> HEADERS = List.of(
            "房源编号", "房源名称", "行政区", "街道或镇", "板块", "小区名称", "详细位置", "经度", "纬度",
            "出租方式", "户型", "月租金", "面积", "楼层", "朝向", "装修情况", "最近地铁站", "地铁线路",
            "距离地铁站米数", "步行时间", "是否独立卫生间", "是否独立厨房", "是否有燃气", "是否有阳台",
            "是否有空调", "是否有冰箱", "是否有洗衣机", "是否有热水器", "是否有电梯", "是否有家具",
            "是否允许宠物", "是否可以做饭", "押金", "付款方式", "物业费", "入住状态", "房源描述", "数据性质");
    private static final String[] FACILITIES = {"private_bathroom", "private_kitchen", "gas", "balcony",
            "air_conditioning", "refrigerator", "washing_machine", "water_heater", "elevator", "furnished",
            "pets_allowed", "cooking_allowed"};
    private static final Pattern LAYOUT = Pattern.compile("^(\\d+)室.*");

    public ParsedDataset read(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            var format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(false).get();
            try (var parser = format.parse(new StringReader(text))) {
                if (!parser.getHeaderNames().equals(HEADERS)) throw invalid("表头必须与原始 38 列一致");
                List<Map<String, Object>> rows = new ArrayList<>();
                Set<String> ids = new HashSet<>();
                for (CSVRecord record : parser) {
                    try {
                        if (record.size() != HEADERS.size()) throw invalid("列数不是 38");
                        Map<String, Object> row = parseRow(record);
                        if (!ids.add((String) row.get("id"))) throw invalid("房源编号重复");
                        rows.add(Collections.unmodifiableMap(row));
                    } catch (IllegalArgumentException e) {
                        throw invalid("第 " + (record.getRecordNumber() + 1) + " 行：" + e.getMessage());
                    }
                }
                if (rows.isEmpty()) throw invalid("CSV 没有房源记录");
                return new ParsedDataset(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                        List.copyOf(rows));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV 无法解析为有效 UTF-8 房源数据", e);
        }
    }

    private Map<String, Object> parseRow(CSVRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        String id = required(r, "房源编号");
        if (!id.matches("SH-MOCK-\\d{6}")) throw invalid("房源编号格式不正确");
        row.put("id", id);
        String[] textKeys = {"name", "district", "town", "sector", "community", "address"};
        for (int i = 0; i < textKeys.length; i++) row.put(textKeys[i], required(r, HEADERS.get(i + 1)));
        BigDecimal lng = decimal(r, "经度", true, BigDecimal.valueOf(120), BigDecimal.valueOf(123), 6);
        BigDecimal lat = decimal(r, "纬度", true, BigDecimal.valueOf(30), BigDecimal.valueOf(32.5), 6);
        if ((lng == null) != (lat == null)) throw invalid("坐标必须成对存在或同时留空");
        row.put("longitude", lng);
        row.put("latitude", lat);
        row.put("rental_type", switch (required(r, "出租方式")) {
            case "整租" -> "ENTIRE";
            case "合租" -> "SHARED";
            default -> throw invalid("出租方式不支持");
        });
        String layout = required(r, "户型");
        var matcher = LAYOUT.matcher(layout);
        if (!matcher.matches()) throw invalid("户型无法识别卧室数");
        int bedrooms = Integer.parseInt(matcher.group(1));
        if (bedrooms < 1 || bedrooms > 10) throw invalid("户型卧室数不正确");
        row.put("layout", layout);
        row.put("bedrooms", bedrooms);
        row.put("rent", decimal(r, "月租金", false, new BigDecimal("0.01"), new BigDecimal("9999999999"), 2));
        row.put("area", decimal(r, "面积", false, new BigDecimal("0.01"), new BigDecimal("99999999"), 2));
        row.put("floor_description", required(r, "楼层"));
        row.put("orientation", required(r, "朝向"));
        row.put("decoration", required(r, "装修情况"));
        row.put("nearest_metro", required(r, "最近地铁站"));
        String lines = required(r, "地铁线路");
        row.put("metro_lines_raw", lines);
        row.put("metro_line_tokens", "|" + String.join("|", metroLines(lines)) + "|");
        Integer distance = integer(r, "距离地铁站米数", 3000);
        Integer walk = integer(r, "步行时间", 180);
        if ((distance == null) != (walk == null)) throw invalid("地铁距离和步行时间必须同时存在或留空");
        row.put("metro_distance_meters", distance);
        row.put("metro_walk_minutes", walk);
        for (int i = 0; i < FACILITIES.length; i++) {
            row.put(FACILITIES[i], TriState.fromCsv(required(r, HEADERS.get(20 + i))).name());
        }
        row.put("deposit", decimal(r, "押金", false, BigDecimal.ZERO, new BigDecimal("9999999999"), 2));
        row.put("payment_method", required(r, "付款方式"));
        row.put("property_fee", decimal(r, "物业费", true, BigDecimal.ZERO, new BigDecimal("9999999999"), 2));
        row.put("availability", required(r, "入住状态"));
        row.put("description", required(r, "房源描述"));
        String nature = required(r, "数据性质");
        row.put("data_nature", nature);
        String locationStatus = switch (nature) {
            case "模拟房源（位置参考真实小区）" -> "COMMUNITY_REFERENCE";
            case "模拟房源（位置待核验）" -> "UNVERIFIED";
            default -> throw invalid("仅接受明确标记的模拟房源");
        };
        if (locationStatus.equals("UNVERIFIED") != (lng == null)) throw invalid("数据性质与坐标状态不一致");
        row.put("location_status", locationStatus);
        return row;
    }

    public static List<String> metroLines(String raw) {
        if (raw.equals("暂无") || raw.equals("待核验")) return List.of();
        return Arrays.stream(raw.split("/"))
                .map(part -> part.matches("\\d+") ? part + "号线" : part).distinct().toList();
    }

    private static String required(CSVRecord r, String key) {
        String value = r.get(key).trim();
        if (value.isEmpty()) throw invalid(key + "不能为空");
        return value;
    }

    private static BigDecimal decimal(CSVRecord r, String key, boolean nullable, BigDecimal min, BigDecimal max, int scale) {
        String text = r.get(key).trim();
        if (nullable && text.isEmpty()) return null;
        try {
            if (!text.matches("-?\\d+(\\.\\d+)?")) throw invalid(key + "不是有效数字");
            BigDecimal value = new BigDecimal(text);
            if (value.compareTo(min) < 0 || value.compareTo(max) > 0 || value.scale() > scale)
                throw invalid(key + "超出范围或精度");
            return value;
        } catch (NumberFormatException e) {
            throw invalid(key + "不是有效数字");
        }
    }

    private static Integer integer(CSVRecord r, String key, int max) {
        BigDecimal value = decimal(r, key, true, BigDecimal.ZERO, BigDecimal.valueOf(max), 0);
        return value == null ? null : value.intValueExact();
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
