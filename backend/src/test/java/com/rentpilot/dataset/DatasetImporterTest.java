package com.rentpilot.dataset;

import com.rentpilot.RentPilotApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = RentPilotApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:import-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "rentpilot.dataset.auto-import=false"
})
class DatasetImporterTest {
    @Autowired DatasetImporter importer;
    @Autowired DatasetService datasets;
    @Autowired JdbcTemplate jdbc;
    @Autowired NamedParameterJdbcTemplate namedJdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired ResourceLoader resources;
    @TempDir Path temp;

    @BeforeEach void resetTestDatabase() {
        jdbc.update("DELETE FROM listing");
        jdbc.update("DELETE FROM dataset_import");
    }

    @Test void repeatedImportPreservesOneBatchAnd1500Rows() {
        assertThat(importer.importDataset().inserted()).isTrue();
        var first = datasets.info();
        assertThat(importer.importDataset().inserted()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing", Integer.class)).isEqualTo(1500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_import", Integer.class)).isEqualTo(1);
        assertThat(datasets.info().importedAt()).isEqualTo(first.importedAt());
    }

    @Test void databaseFailureRollsBackWholeBatch() {
        jdbc.execute("ALTER TABLE listing ADD CONSTRAINT reject_last_test CHECK(id <> 'SH-MOCK-001500')");
        try {
            assertThatThrownBy(importer::importDataset).isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_import", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE listing DROP CONSTRAINT reject_last_test");
        }
        assertThat(importer.importDataset().rowCount()).isEqualTo(1500);
    }

    @Test void countDriftFailsInsteadOfSilentlyOverwriting() {
        importer.importDataset();
        jdbc.update("DELETE FROM listing WHERE id='SH-MOCK-001500'");
        assertThatThrownBy(importer::importDataset).isInstanceOf(IllegalStateException.class).hasMessageContaining("不一致");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing", Integer.class)).isEqualTo(1499);
    }

    @Test void changedCsvHashIsRejectedWithoutAffectingExistingDataset() throws Exception {
        importer.importDataset();
        String text;
        try (var in = resources.getResource("classpath:dataset/上海1500条模拟房源.csv").getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path changed = temp.resolve("changed.csv");
        Files.writeString(changed, text.replaceFirst(",2150,29.2,", ",2200,29.2,"), StandardCharsets.UTF_8);
        var other = new DatasetImporter(namedJdbc, transaction, resources, changed.toUri().toString(), false);
        assertThatThrownBy(other::importDataset).isInstanceOf(IllegalStateException.class).hasMessageContaining("哈希或数量");
        assertThat(jdbc.queryForObject("SELECT rent FROM listing WHERE id='SH-MOCK-000001'", Integer.class)).isEqualTo(2150);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing", Integer.class)).isEqualTo(1500);
    }
}
