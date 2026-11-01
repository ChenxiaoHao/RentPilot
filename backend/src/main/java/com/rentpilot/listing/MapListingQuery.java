package com.rentpilot.listing;

import com.rentpilot.api.ApiException;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.Set;

public record MapListingQuery(BigDecimal minLongitude, BigDecimal minLatitude, BigDecimal maxLongitude,
                              BigDecimal maxLatitude, String q, String district, String town, String rentalType,
                              Integer bedrooms, BigDecimal minRent, BigDecimal maxRent, BigDecimal minArea,
                              BigDecimal maxArea, Integer maxMetroWalkMinutes, String metroLine, String elevator,
                              String petsAllowed, String privateBathroom, String sort, int limit, BigDecimal centerLongitude, BigDecimal centerLatitude, String nearestMetro)
        implements ListingFilterQuery {
    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 500;
    private static final Set<String> ALLOWED = Set.of("minLongitude", "minLatitude", "maxLongitude", "maxLatitude",
            "q", "district", "town", "rentalType", "bedrooms", "minRent", "maxRent", "minArea", "maxArea",
            "maxMetroWalkMinutes", "metroLine", "elevator", "petsAllowed", "privateBathroom", "sort", "limit", "centerLongitude", "centerLatitude", "nearestMetro");
    private static final Set<String> SORTS = Set.of("id", "rentAsc", "rentDesc", "areaDesc", "metroWalkAsc");

    public static MapListingQuery parse(MultiValueMap<String, String> values) {
        for (var entry : values.entrySet()) {
            if (!ALLOWED.contains(entry.getKey())) throw ApiException.invalid(entry.getKey(), "不支持此查询条件");
            if (entry.getValue().size() != 1) throw ApiException.invalid(entry.getKey(), "同一条件只能传入一次");
            if (entry.getValue().getFirst() == null || entry.getValue().getFirst().isBlank())
                throw ApiException.invalid(entry.getKey(), "不能为空；不筛选时请省略该条件");
        }
        BigDecimal minLongitude = coordinate(values, "minLongitude", -180, 180);
        BigDecimal minLatitude = coordinate(values, "minLatitude", -90, 90);
        BigDecimal maxLongitude = coordinate(values, "maxLongitude", -180, 180);
        BigDecimal maxLatitude = coordinate(values, "maxLatitude", -90, 90);
        ListingQuery.ordered("maxLongitude", minLongitude, maxLongitude);
        ListingQuery.ordered("maxLatitude", minLatitude, maxLatitude);
        if (values.containsKey("centerLongitude") != values.containsKey("centerLatitude")) throw ApiException.invalid("centerLatitude", "中心经纬度必须同时提供");
        BigDecimal minRent = ListingQuery.number(values, "minRent"), maxRent = ListingQuery.number(values, "maxRent");
        BigDecimal minArea = ListingQuery.number(values, "minArea"), maxArea = ListingQuery.number(values, "maxArea");
        ListingQuery.ordered("maxRent", minRent, maxRent);
        ListingQuery.ordered("maxArea", minArea, maxArea);
        Set<String> states = Set.of("YES", "NO", "UNKNOWN");
        return new MapListingQuery(minLongitude, minLatitude, maxLongitude, maxLatitude,
                ListingQuery.text(values, "q", 120), ListingQuery.text(values, "district", 40),
                ListingQuery.text(values, "town", 80), ListingQuery.option(values, "rentalType", Set.of("ENTIRE", "SHARED")),
                ListingQuery.integer(values, "bedrooms", 1, 10), minRent, maxRent, minArea, maxArea,
                ListingQuery.integer(values, "maxMetroWalkMinutes", 1, 180), ListingQuery.text(values, "metroLine", 40),
                ListingQuery.option(values, "elevator", states), ListingQuery.option(values, "petsAllowed", states),
                ListingQuery.option(values, "privateBathroom", states),
                values.containsKey("sort") ? ListingQuery.option(values, "sort", SORTS) : "id",
                ListingQuery.defaultInt(values, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT), values.containsKey("centerLongitude") ? coordinate(values, "centerLongitude", -180, 180) : null, values.containsKey("centerLatitude") ? coordinate(values, "centerLatitude", -90, 90) : null, ListingQuery.text(values, "nearestMetro", 80));
    }

    private static BigDecimal coordinate(MultiValueMap<String, String> values, String key, int min, int max) {
        String value = values.getFirst(key);
        if (value == null) throw ApiException.invalid(key, "必须提供地图可视范围");
        if (!value.matches("-?\\d{1,3}(\\.\\d{1,6})?"))
            throw ApiException.invalid(key, "必须为 " + min + " 至 " + max + " 的数字，最多 6 位小数");
        BigDecimal coordinate = new BigDecimal(value);
        if (coordinate.compareTo(BigDecimal.valueOf(min)) < 0 || coordinate.compareTo(BigDecimal.valueOf(max)) > 0)
            throw ApiException.invalid(key, "必须为 " + min + " 至 " + max + " 的数字，最多 6 位小数");
        return coordinate;
    }
}