package com.rentpilot.listing;

import com.rentpilot.api.ApiException;
import com.rentpilot.dataset.CsvDatasetReader;
import com.rentpilot.dataset.DatasetInfo;
import com.rentpilot.dataset.DatasetService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class ListingRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final DatasetService datasets;
    private static final RowMapper<Listing> MAPPER = ListingRepository::map;
    private static final RowMapper<MapListing> MARKER_MAPPER = ListingRepository::mapMarker;

    public ListingRepository(NamedParameterJdbcTemplate jdbc, DatasetService datasets) {
        this.jdbc = jdbc;
        this.datasets = datasets;
    }

    public List<Listing> byIds(List<String> ids) {
        if(ids.isEmpty())return List.of();
        var found=jdbc.query("SELECT * FROM listing WHERE dataset_id=:datasetId AND id IN (:ids)",Map.of("datasetId",DatasetService.ID,"ids",ids),MAPPER);
        var indexed=new HashMap<String,Listing>();found.forEach(home->indexed.put(home.id(),home));
        return ids.stream().map(indexed::get).filter(Objects::nonNull).toList();
    }

    public ListingPage find(ListingQuery query) {
        StringBuilder where = new StringBuilder(" WHERE dataset_id=:datasetId");
        var parameters = new MapSqlParameterSource("datasetId", DatasetService.ID);
        applyFilters(where, parameters, query);
        if (query.hasCoordinates() != null) where.append(query.hasCoordinates() ? " AND longitude IS NOT NULL" : " AND longitude IS NULL");
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM listing" + where, parameters, Long.class));
        parameters.addValue("limit", query.size()).addValue("offset", (long) query.page() * query.size());
        var items = jdbc.query("SELECT * FROM listing" + where + " ORDER BY " + orderBy(query.sort()) + " LIMIT :limit OFFSET :offset", parameters, MAPPER);
        return new ListingPage(items, query.page(), query.size(), total, (int) ((total + query.size() - 1) / query.size()),
                "SIMULATED", datasets.info());
    }

    public MapPage mapFind(MapListingQuery query) {
        StringBuilder where = new StringBuilder(" WHERE dataset_id=:datasetId AND longitude IS NOT NULL AND latitude IS NOT NULL")
                .append(" AND longitude BETWEEN :minLongitude AND :maxLongitude")
                .append(" AND latitude BETWEEN :minLatitude AND :maxLatitude");
        var parameters = new MapSqlParameterSource("datasetId", DatasetService.ID)
                .addValue("minLongitude", query.minLongitude()).addValue("maxLongitude", query.maxLongitude())
                .addValue("minLatitude", query.minLatitude()).addValue("maxLatitude", query.maxLatitude());
        applyFilters(where, parameters, query);
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM listing" + where, parameters, Long.class));
        parameters.addValue("limit", query.limit());
        List<MapListing> items;
        if (query.centerLongitude() != null) {
            items = jdbc.query("SELECT * FROM listing" + where, parameters, MARKER_MAPPER).stream()
                    .sorted(Comparator.comparingInt((MapListing item) -> item.bedrooms() == 1 ? 0 : 1)
                            .thenComparingDouble(item -> distance(item, query.centerLongitude(), query.centerLatitude()))
                            .thenComparing(MapListing::id))
                    .limit(Math.min(query.limit(), 6)).toList();
        } else {
            items = jdbc.query("SELECT * FROM listing" + where + " ORDER BY " + orderBy(query.sort()) + " LIMIT :limit", parameters, MARKER_MAPPER);
        }
        return new MapPage(new Bounds(query.minLongitude(), query.minLatitude(), query.maxLongitude(), query.maxLatitude()),
                items, total, items.size(), query.centerLongitude() == null ? query.limit() : Math.min(query.limit(), 6), total > items.size(),
                "SIMULATED", "GCJ-02", "COMMUNITY", datasets.info());
    }

    private static double distance(MapListing item, BigDecimal longitude, BigDecimal latitude) {
        double lat1 = Math.toRadians(latitude.doubleValue()), lat2 = Math.toRadians(item.latitude().doubleValue());
        double dlat = lat2 - lat1, dlon = Math.toRadians(item.longitude().doubleValue() - longitude.doubleValue());
        double a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    }

    public Listing detail(String id) {
        var items = jdbc.query("SELECT * FROM listing WHERE dataset_id=:datasetId AND id=:id",
                Map.of("datasetId", DatasetService.ID, "id", id), MAPPER);
        if (items.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "未找到该模拟房源", Map.of());
        return items.getFirst();
    }

    public FilterOptions filters() {
        Map<String, Object> parameters = Map.of("datasetId", DatasetService.ID);
        List<CountOption> districts = jdbc.query("SELECT district, COUNT(*) AS total FROM listing WHERE dataset_id=:datasetId GROUP BY district ORDER BY district",
                parameters, (rs, n) -> new CountOption(rs.getString("district"), rs.getInt("total")));
        List<TownOption> towns = jdbc.query("SELECT district,town,COUNT(*) AS total FROM listing WHERE dataset_id=:datasetId GROUP BY district,town ORDER BY district,town",
                parameters, (rs, n) -> new TownOption(rs.getString("district"), rs.getString("town"), rs.getInt("total")));
        List<CountOption> rentalTypes = jdbc.query("SELECT rental_type,COUNT(*) AS total FROM listing WHERE dataset_id=:datasetId GROUP BY rental_type ORDER BY rental_type",
                parameters, (rs, n) -> new CountOption(rs.getString("rental_type"), rs.getInt("total")));
        List<BedroomOption> bedrooms = jdbc.query("SELECT bedrooms,COUNT(*) AS total FROM listing WHERE dataset_id=:datasetId GROUP BY bedrooms ORDER BY bedrooms",
                parameters, (rs, n) -> new BedroomOption(rs.getInt("bedrooms"), rs.getInt("total")));
        List<String> lines = jdbc.queryForList("SELECT DISTINCT metro_lines_raw FROM listing WHERE dataset_id=:datasetId", parameters, String.class)
                .stream().flatMap(raw -> CsvDatasetReader.metroLines(raw).stream()).distinct().sorted().toList();
        List<String> stations = jdbc.queryForList("SELECT DISTINCT nearest_metro FROM listing WHERE dataset_id=:datasetId AND nearest_metro IS NOT NULL AND nearest_metro <> '' ORDER BY nearest_metro", parameters, String.class);
        return new FilterOptions(districts, towns, rentalTypes, bedrooms, lines, stations, datasets.info());
    }

    private static void applyFilters(StringBuilder where, MapSqlParameterSource parameters, ListingFilterQuery query) {
        if (query.q() != null) {
            String pattern = "%" + escaped(query.q().toLowerCase(Locale.ROOT)) + "%";
            where.append(" AND (LOWER(name) LIKE :q ESCAPE '!' OR LOWER(district) LIKE :q ESCAPE '!'"
                    + " OR LOWER(town) LIKE :q ESCAPE '!' OR LOWER(community) LIKE :q ESCAPE '!'"
                    + " OR LOWER(address) LIKE :q ESCAPE '!')");
            parameters.addValue("q", pattern);
        }
        condition(where, parameters, "nearest_metro", "=", query.nearestMetro());
        condition(where, parameters, "district", "=", query.district());
        condition(where, parameters, "town", "=", query.town());
        condition(where, parameters, "rental_type", "=", query.rentalType());
        condition(where, parameters, "bedrooms", "=", query.bedrooms());
        condition(where, parameters, "rent", ">=", "minRent", query.minRent());
        condition(where, parameters, "rent", "<=", "maxRent", query.maxRent());
        condition(where, parameters, "area", ">=", "minArea", query.minArea());
        condition(where, parameters, "area", "<=", "maxArea", query.maxArea());
        condition(where, parameters, "metro_walk_minutes", "<=", query.maxMetroWalkMinutes());
        condition(where, parameters, "elevator", "=", query.elevator());
        condition(where, parameters, "pets_allowed", "=", query.petsAllowed());
        condition(where, parameters, "private_bathroom", "=", query.privateBathroom());
        if (query.metroLine() != null) {
            where.append(" AND metro_line_tokens LIKE :metroLine ESCAPE '!'");
            parameters.addValue("metroLine", "%|" + escaped(query.metroLine()) + "|%");
        }
    }

    private static String orderBy(String sort) {
        return switch (sort) {
            case "rentAsc" -> "rent ASC, id ASC";
            case "rentDesc" -> "rent DESC, id ASC";
            case "areaDesc" -> "area DESC, id ASC";
            case "metroWalkAsc" -> "CASE WHEN metro_walk_minutes IS NULL THEN 1 ELSE 0 END, metro_walk_minutes ASC, id ASC";
            default -> "id ASC";
        };
    }

    private static void condition(StringBuilder where, MapSqlParameterSource params, String column, String operator, Object value) {
        condition(where, params, column, operator, column, value);
    }

    private static void condition(StringBuilder where, MapSqlParameterSource params, String column, String operator, String name, Object value) {
        if (value == null) return;
        where.append(" AND ").append(column).append(' ').append(operator).append(" :").append(name);
        params.addValue(name, value);
    }

    private static String escaped(String text) { return text.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }

    private static Listing map(ResultSet rs, int row) throws SQLException {
        var amenities = new Listing.Amenities(state(rs, "private_bathroom"), state(rs, "private_kitchen"), state(rs, "gas"),
                state(rs, "balcony"), state(rs, "air_conditioning"), state(rs, "refrigerator"), state(rs, "washing_machine"),
                state(rs, "water_heater"), state(rs, "elevator"), state(rs, "furnished"), state(rs, "pets_allowed"), state(rs, "cooking_allowed"));
        String lines = rs.getString("metro_lines_raw");
        return new Listing(rs.getString("id"), rs.getString("name"), rs.getString("district"), rs.getString("town"),
                rs.getString("sector"), rs.getString("community"), rs.getString("address"), rs.getBigDecimal("longitude"),
                rs.getBigDecimal("latitude"), rs.getString("rental_type"), rs.getString("layout"), rs.getInt("bedrooms"),
                rs.getBigDecimal("rent"), rs.getBigDecimal("area"), rs.getString("floor_description"), rs.getString("orientation"),
                rs.getString("decoration"), rs.getString("nearest_metro"), lines, CsvDatasetReader.metroLines(lines),
                rs.getObject("metro_distance_meters", Integer.class), rs.getObject("metro_walk_minutes", Integer.class), amenities,
                rs.getBigDecimal("deposit"), rs.getString("payment_method"), rs.getBigDecimal("property_fee"), "UNSPECIFIED",
                rs.getString("availability"), rs.getString("description"), rs.getString("data_nature"), rs.getString("location_status"),
                "SIMULATED", "GCJ-02", "COMMUNITY", rs.getString("dataset_id"), DatasetService.SHA256, DatasetService.NOTICE);
    }

    private static TriState state(ResultSet rs, String column) throws SQLException { return TriState.valueOf(rs.getString(column)); }

    private static MapListing mapMarker(ResultSet rs, int row) throws SQLException {
        String lines = rs.getString("metro_lines_raw");
        return new MapListing(rs.getString("id"), rs.getBigDecimal("longitude"), rs.getBigDecimal("latitude"),
                rs.getString("name"), rs.getString("district"), rs.getString("town"), rs.getString("community"),
                rs.getString("address"), rs.getString("rental_type"), rs.getString("layout"), rs.getInt("bedrooms"),
                rs.getBigDecimal("rent"), rs.getBigDecimal("area"), rs.getString("nearest_metro"),
                CsvDatasetReader.metroLines(lines), rs.getObject("metro_walk_minutes", Integer.class),
                "SIMULATED", "GCJ-02", "COMMUNITY");
    }

    public record ListingPage(List<Listing> items, int page, int size, long total, int totalPages, String dataKind, DatasetInfo source) {}
    public record MapListing(String id, BigDecimal longitude, BigDecimal latitude, String name, String district,
                             String town, String community, String address, String rentalType, String layout,
                             int bedrooms, BigDecimal rent, BigDecimal area, String nearestMetro,
                             List<String> metroLines, Integer metroWalkMinutes, String dataKind,
                             String coordinateSystem, String coordinateScope) {}
    public record Bounds(BigDecimal minLongitude, BigDecimal minLatitude, BigDecimal maxLongitude, BigDecimal maxLatitude) {}
    public record MapPage(Bounds bounds, List<MapListing> items, long total, int returned, int limit, boolean truncated,
                          String dataKind, String coordinateSystem, String coordinateScope, DatasetInfo source) {}
    public record CountOption(String value, int count) {}
    public record TownOption(String district, String value, int count) {}
    public record BedroomOption(int value, int count) {}
    public record FilterOptions(List<CountOption> districts, List<TownOption> towns, List<CountOption> rentalTypes,
                                List<BedroomOption> bedrooms, List<String> metroLines, List<String> metroStations, DatasetInfo source) {}
}
