package com.rentpilot.listing;

import com.rentpilot.api.ApiException;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.util.Set;

public record ListingQuery(int page, int size, String q, String district, String town, String rentalType,
                           Integer bedrooms, BigDecimal minRent, BigDecimal maxRent, BigDecimal minArea,
                           BigDecimal maxArea, Integer maxMetroWalkMinutes, String metroLine, Boolean hasCoordinates,
                           String elevator, String petsAllowed, String privateBathroom, String sort, String nearestMetro)
        implements ListingFilterQuery {
    private static final Set<String> ALLOWED = Set.of("page", "size", "q", "district", "town", "rentalType",
            "bedrooms", "minRent", "maxRent", "minArea", "maxArea", "maxMetroWalkMinutes", "metroLine",
            "hasCoordinates", "elevator", "petsAllowed", "privateBathroom", "sort", "nearestMetro");

    public static ListingQuery parse(MultiValueMap<String, String> values) {
        for (var entry : values.entrySet()) {
            if (!ALLOWED.contains(entry.getKey())) throw ApiException.invalid(entry.getKey(), "不支持此查询条件");
            if (entry.getValue().size() != 1) throw ApiException.invalid(entry.getKey(), "同一条件只能传入一次");
            if (entry.getValue().getFirst() == null || entry.getValue().getFirst().isBlank())
                throw ApiException.invalid(entry.getKey(), "不能为空；不筛选时请省略该条件");
        }
        BigDecimal minRent = number(values, "minRent"), maxRent = number(values, "maxRent");
        BigDecimal minArea = number(values, "minArea"), maxArea = number(values, "maxArea");
        ordered("maxRent", minRent, maxRent);
        ordered("maxArea", minArea, maxArea);
        String hasCoordinates = option(values, "hasCoordinates", Set.of("true", "false"));
        Set<String> states = Set.of("YES", "NO", "UNKNOWN");
        return new ListingQuery(defaultInt(values, "page", 0, 0, Integer.MAX_VALUE),
                defaultInt(values, "size", 20, 1, 100), text(values, "q", 120), text(values, "district", 40),
                text(values, "town", 80), option(values, "rentalType", Set.of("ENTIRE", "SHARED")),
                integer(values, "bedrooms", 1, 10), minRent, maxRent, minArea, maxArea,
                integer(values, "maxMetroWalkMinutes", 1, 180), text(values, "metroLine", 40),
                hasCoordinates == null ? null : Boolean.valueOf(hasCoordinates),
                option(values, "elevator", states), option(values, "petsAllowed", states), option(values, "privateBathroom", states),
                values.containsKey("sort") ? option(values, "sort", Set.of("id", "rentAsc", "rentDesc", "areaDesc", "metroWalkAsc")) : "id", text(values, "nearestMetro", 80));
    }

    static String text(MultiValueMap<String, String> values, String key, int max) {
        String value = values.getFirst(key);
        if (value == null) return null;
        value = value.strip();
        if (value.length() > max) throw ApiException.invalid(key, "长度不能超过 " + max);
        return value;
    }

    static String option(MultiValueMap<String, String> values, String key, Set<String> options) {
        String value = text(values, key, 100);
        if (value != null && !options.contains(value)) throw ApiException.invalid(key, "不是支持的选项");
        return value;
    }

    static Integer integer(MultiValueMap<String, String> values, String key, int min, int max) {
        String value = values.getFirst(key);
        if (value == null) return null;
        try {
            if (!value.matches("-?\\d{1,10}")) throw new NumberFormatException();
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) { throw ApiException.invalid(key, "必须为 " + min + " 至 " + max + " 的整数"); }
    }

    static int defaultInt(MultiValueMap<String, String> values, String key, int fallback, int min, int max) {
        Integer value = integer(values, key, min, max);
        return value == null ? fallback : value;
    }

    static BigDecimal number(MultiValueMap<String, String> values, String key) {
        String value = values.getFirst(key);
        if (value == null) return null;
        if (!value.matches("\\d{1,9}(\\.\\d{1,2})?")) throw ApiException.invalid(key, "必须为非负数字，最多 9 位整数及 2 位小数");
        return new BigDecimal(value);
    }

    static void ordered(String key, BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) throw ApiException.invalid(key, "上限不能小于下限");
    }
}
