package com.rentpilot;

import com.rentpilot.dataset.CsvDatasetReader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class CsvDatasetReaderTest {
    private final CsvDatasetReader reader = new CsvDatasetReader();

    static byte[] original() throws IOException {
        try (var in = CsvDatasetReaderTest.class.getResourceAsStream("/dataset/上海1500条模拟房源.csv")) {
            if (in == null) throw new IOException("Missing packaged CSV");
            return in.readAllBytes();
        }
    }

    private byte[] altered(String before, String after) throws IOException {
        return new String(original(), StandardCharsets.UTF_8).replaceFirst(
                java.util.regex.Pattern.quote(before), java.util.regex.Matcher.quoteReplacement(after))
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test void readsBomAndPreservesUnknownAndMissingFields() throws Exception {
        var dataset = reader.read(original());
        assertThat(dataset.rows()).hasSize(1500);
        assertThat(dataset.sha256()).isEqualTo("07ff43a3600e34cffbdc0de8751406cac33f6cfc330c169472ae739ed8abdfa8");
        var first = dataset.rows().getFirst();
        assertThat(first).containsEntry("id", "SH-MOCK-000001").containsEntry("gas", "UNKNOWN");
        assertThat(dataset.rows().get(200)).containsEntry("longitude", null).containsEntry("metro_walk_minutes", null);
        assertThat(dataset.rows()).filteredOn(row -> row.get("longitude") == null).hasSize(14);
        assertThat(dataset.rows()).filteredOn(row -> row.get("metro_distance_meters") == null).hasSize(423);
    }

    @Test void rejectsDuplicateIds() throws Exception {
        assertThatThrownBy(() -> reader.read(altered("SH-MOCK-000002", "SH-MOCK-000001")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重复");
    }

    @Test void rejectsUnknownFacilitySpelling() throws Exception {
        assertThatThrownBy(() -> reader.read(altered(",未知,", ",也许,")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("设施");
    }

    @Test void rejectsHalfCoordinates() throws Exception {
        assertThatThrownBy(() -> reader.read(altered("121.520000,31.220000", ",31.220000")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("坐标");
    }

    @Test void rejectsNegativeRentAndBadHeader() throws Exception {
        assertThatThrownBy(() -> reader.read(altered(",2150,29.2,", ",-1,29.2,")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("月租金");
        assertThatThrownBy(() -> reader.read(altered("房源编号,房源名称", "未知编号,房源名称")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("表头");
    }

    @Test void acceptsQuotedCommasAndLineBreaksAsOneField() throws Exception {
        var parsed = reader.read(altered("潍坊一村精装合租单间，近4号线塘桥站，29.2㎡", "\"带逗号,和换行\n的房源\""));
        assertThat(parsed.rows()).hasSize(1500);
        assertThat(parsed.rows().getFirst()).containsEntry("name", "带逗号,和换行\n的房源");
    }

    @Test void rejectsMalformedUtf8AndTruncatedRows() throws Exception {
        assertThatThrownBy(() -> reader.read(new byte[]{(byte) 0xC3, 0x28}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> reader.read(altered(",2150,29.2,", ",2150,")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("列数");
    }
}
