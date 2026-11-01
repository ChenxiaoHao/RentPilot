package com.rentpilot.dataset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;

@Service
public class DatasetService {
    public static final String ID = "shanghai-simulation-v1";
    public static final String SHA256 = "07ff43a3600e34cffbdc0de8751406cac33f6cfc330c169472ae739ed8abdfa8";
    public static final int ROW_COUNT = 1500;
    public static final String NOTICE = "仅供开发、测试和演示的模拟房源；位置参考小区，不代表真实在租。地铁步行时间不是工作地通勤时间。";
    private final JdbcTemplate jdbc;

    public DatasetService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DatasetInfo info() {
        return jdbc.queryForObject("""
                SELECT d.*, (SELECT COUNT(*) FROM listing WHERE dataset_id=d.dataset_id AND longitude IS NULL) AS missing_coordinates,
                (SELECT COUNT(*) FROM listing WHERE dataset_id=d.dataset_id AND metro_walk_minutes IS NULL) AS missing_metro_walk
                FROM dataset_import d WHERE dataset_id=?
                """, (rs, n) -> new DatasetInfo(rs.getString("dataset_id"), rs.getString("source_name"),
                rs.getString("source_sha256"), rs.getInt("row_count"), rs.getObject("imported_at", OffsetDateTime.class),
                rs.getInt("missing_coordinates"), rs.getInt("missing_metro_walk"), "SIMULATED", "GCJ-02", "COMMUNITY", NOTICE), ID);
    }
}
