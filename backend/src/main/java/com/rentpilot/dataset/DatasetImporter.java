package com.rentpilot.dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Map;

@Component
@DependsOnDatabaseInitialization
public class DatasetImporter implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(DatasetImporter.class);
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ResourceLoader resources;
    private final String location;
    private final boolean autoImport;

    public DatasetImporter(NamedParameterJdbcTemplate jdbc, TransactionTemplate transaction, ResourceLoader resources,
                           @Value("${rentpilot.dataset.location}") String location,
                           @Value("${rentpilot.dataset.auto-import:true}") boolean autoImport) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.resources = resources;
        this.location = location;
        this.autoImport = autoImport;
    }

    @Override public void afterPropertiesSet() {
        if (autoImport) importDataset();
    }

    public ImportResult importDataset() {
        byte[] bytes;
        try (var in = resources.getResource(location).getInputStream()) {
            bytes = in.readNBytes(10 * 1024 * 1024 + 1);
            if (bytes.length > 10 * 1024 * 1024) throw new IllegalStateException("房源 CSV 大于允许的 10 MiB");
        } catch (IOException e) {
            throw new IllegalStateException("无法读取房源 CSV；请检查 rentpilot.dataset.location", e);
        }
        ParsedDataset parsed = new CsvDatasetReader().read(bytes);
        if (!DatasetService.SHA256.equals(parsed.sha256()) || parsed.rows().size() != DatasetService.ROW_COUNT)
            throw new IllegalStateException("原始房源 CSV 哈希或数量不一致；拒绝导入或覆盖已有数据");
        ImportResult result = transaction.execute(status -> {
            Map<String, Object> key = Map.of("datasetId", DatasetService.ID);
            var batches = jdbc.queryForList("SELECT source_sha256, row_count FROM dataset_import WHERE dataset_id=:datasetId", key);
            Integer actual = jdbc.queryForObject("SELECT COUNT(*) FROM listing WHERE dataset_id=:datasetId", key, Integer.class);
            if (!batches.isEmpty()) {
                var batch = batches.getFirst();
                if (!parsed.sha256().equals(batch.get("source_sha256"))
                        || ((Number) batch.get("row_count")).intValue() != DatasetService.ROW_COUNT
                        || actual == null || actual != DatasetService.ROW_COUNT)
                    throw new IllegalStateException("已有导入批次不一致；请人工检查数据库，未执行覆盖");
                return new ImportResult(false, actual, parsed.sha256());
            }
            if (actual == null || actual != 0) throw new IllegalStateException("发现未登记的房源数据，拒绝覆盖");
            jdbc.update("""
                    INSERT INTO dataset_import(dataset_id,source_name,source_sha256,row_count)
                    VALUES(:datasetId,:sourceName,:sha256,:rowCount)
                    """, Map.of("datasetId", DatasetService.ID, "sourceName", "上海1500条模拟房源.csv",
                    "sha256", parsed.sha256(), "rowCount", parsed.rows().size()));
            var columns = parsed.rows().getFirst().keySet();
            String insert = "INSERT INTO listing(dataset_id," + String.join(",", columns) + ") VALUES(:dataset_id,"
                    + String.join(",", columns.stream().map(c -> ":" + c).toList()) + ")";
            var batch = parsed.rows().stream().map(row -> new MapSqlParameterSource(row)
                    .addValue("dataset_id", DatasetService.ID)).toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate(insert, batch);
            return new ImportResult(true, parsed.rows().size(), parsed.sha256());
        });
        log.info("Simulated listing dataset {}: {} rows, sha256={}", result.inserted() ? "imported" : "verified (no duplicate import)",
                result.rowCount(), result.sha256());
        return result;
    }

    public record ImportResult(boolean inserted, int rowCount, String sha256) {}
}
