package com.rentpilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:map-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureMockMvc
class MapListingApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static MockHttpServletRequestBuilder map(String minLongitude, String minLatitude, String maxLongitude, String maxLatitude) {
        return get("/api/listings/map").param("minLongitude", minLongitude).param("minLatitude", minLatitude)
                .param("maxLongitude", maxLongitude).param("maxLatitude", maxLatitude);
    }

    private static MockHttpServletRequestBuilder shanghai() { return map("120", "30", "123", "32.5"); }

    private static List<String> ids(JsonNode items) {
        var list = new ArrayList<String>();
        items.forEach(item -> list.add(item.get("id").asText()));
        return list;
    }

    @Test void returnsMarkersInsideBoundsWithCardFieldsAndSimulationMarkers() throws Exception {
        var body = mvc.perform(shanghai())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1486))
                .andExpect(jsonPath("$.returned").value(200))
                .andExpect(jsonPath("$.limit").value(200))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.dataKind").value("SIMULATED"))
                .andExpect(jsonPath("$.coordinateSystem").value("GCJ-02"))
                .andExpect(jsonPath("$.coordinateScope").value("COMMUNITY"))
                .andExpect(jsonPath("$.bounds.minLongitude").value(120))
                .andExpect(jsonPath("$.bounds.minLatitude").value(30))
                .andExpect(jsonPath("$.bounds.maxLongitude").value(123))
                .andExpect(jsonPath("$.bounds.maxLatitude").value(32.5))
                .andExpect(jsonPath("$.source.rowCount").value(1500))
                .andExpect(jsonPath("$.source.missingCoordinates").value(14))
                .andExpect(jsonPath("$.items", hasSize(200)))
                .andExpect(jsonPath("$.items[0].id").value("SH-MOCK-000001"))
                .andExpect(jsonPath("$.items[0].longitude").value(121.52))
                .andExpect(jsonPath("$.items[0].latitude").value(31.22))
                .andExpect(jsonPath("$.items[0].name").value(startsWith("潍坊一村")))
                .andExpect(jsonPath("$.items[0].district").value("浦东新区"))
                .andExpect(jsonPath("$.items[0].town").value("潍坊新村街道"))
                .andExpect(jsonPath("$.items[0].community").value("潍坊一村"))
                .andExpect(jsonPath("$.items[0].address").value(not(emptyString())))
                .andExpect(jsonPath("$.items[0].rentalType").value("SHARED"))
                .andExpect(jsonPath("$.items[0].layout").value("3室1厅1厨2卫（合租单间）"))
                .andExpect(jsonPath("$.items[0].bedrooms").value(3))
                .andExpect(jsonPath("$.items[0].rent").value(2150))
                .andExpect(jsonPath("$.items[0].area").value(29.2))
                .andExpect(jsonPath("$.items[0].nearestMetro").value("塘桥"))
                .andExpect(jsonPath("$.items[0].metroLines", hasItem("4号线")))
                .andExpect(jsonPath("$.items[0].metroWalkMinutes").value(19))
                .andExpect(jsonPath("$.items[0].dataKind").value("SIMULATED"))
                .andExpect(jsonPath("$.items[0].coordinateSystem").value("GCJ-02"))
                .andExpect(jsonPath("$.items[0].coordinateScope").value("COMMUNITY"))
                .andReturn().getResponse().getContentAsString();
        var items = json.readTree(body).get("items");
        for (var item : items) {
            assertThat(item.get("longitude").isNull()).isFalse();
            assertThat(item.get("latitude").isNull()).isFalse();
            assertThat(item.get("longitude").decimalValue()).isBetween(new BigDecimal("120"), new BigDecimal("123"));
            assertThat(item.get("latitude").decimalValue()).isBetween(new BigDecimal("30"), new BigDecimal("32.5"));
        }
    }

    @Test void treatsBoundsAsInclusiveAndExcludesMarkersJustOutside() throws Exception {
        var onPoint = json.readTree(mvc.perform(map("121.52", "31.22", "121.52", "31.22"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("items");
        assertThat(ids(onPoint)).contains("SH-MOCK-000001");
        onPoint.forEach(item -> {
            assertThat(item.get("longitude").decimalValue()).isEqualByComparingTo("121.52");
            assertThat(item.get("latitude").decimalValue()).isEqualByComparingTo("31.22");
        });
        var outside = json.readTree(mvc.perform(map("121.520001", "31.22", "121.6", "31.3"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("items");
        assertThat(ids(outside)).doesNotContain("SH-MOCK-000001");
        var latOutside = json.readTree(mvc.perform(map("121.52", "31.220001", "121.6", "31.3"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("items");
        assertThat(ids(latOutside)).doesNotContain("SH-MOCK-000001");
    }

    @Test void neverReturnsListingsWithoutCoordinates() throws Exception {
        mvc.perform(shanghai().param("district", "黄浦区").param("town", "豫园街道"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.returned").value(0)).andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(shanghai().param("district", "静安区").param("town", "石门二路街道"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
        var body = mvc.perform(shanghai().param("limit", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1486))
                .andExpect(jsonPath("$.returned").value(500))
                .andExpect(jsonPath("$.truncated").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(json.readTree(body).get("items"))).doesNotContain("SH-MOCK-000201", "SH-MOCK-000351", "SH-MOCK-001405");
    }

    @Test void returnsEmptyResultOutsideDataWithoutInventingPoints() throws Exception {
        mvc.perform(map("100", "20", "101", "21"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.returned").value(0)).andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test void limitsReturnedMarkersAndKeepsStableOrder() throws Exception {
        var body = mvc.perform(shanghai().param("limit", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1486))
                .andExpect(jsonPath("$.returned").value(5)).andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.truncated").value(true)).andExpect(jsonPath("$.items", hasSize(5)))
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(json.readTree(body).get("items")))
                .containsExactly("SH-MOCK-000001", "SH-MOCK-000002", "SH-MOCK-000003", "SH-MOCK-000004", "SH-MOCK-000005");
        mvc.perform(shanghai().param("limit", "500").param("sort", "rentAsc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.returned").value(500))
                .andExpect(jsonPath("$.items[0].id").value("SH-MOCK-000584"))
                .andExpect(jsonPath("$.items[0].rent").value(900));
        mvc.perform(shanghai().param("limit", "1").param("sort", "rentDesc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("SH-MOCK-000216"))
                .andExpect(jsonPath("$.items[0].rent").value(10100));
    }

    @Test void reusesListFiltersAndAgreesWithTheListApi() throws Exception {
        var mapBody = mvc.perform(shanghai().param("district", "浦东新区").param("maxRent", "3000").param("limit", "500"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(166))
                .andExpect(jsonPath("$.returned").value(166))
                .andReturn().getResponse().getContentAsString();
        for (var item : json.readTree(mapBody).get("items")) {
            assertThat(item.get("district").asText()).isEqualTo("浦东新区");
            assertThat(item.get("rent").decimalValue()).isLessThanOrEqualTo(new BigDecimal("3000"));
        }
        var listBody = mvc.perform(get("/api/listings").param("district", "浦东新区")
                        .param("maxRent", "3000").param("hasCoordinates", "true").param("size", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(mapBody).get("total").asLong()).isEqualTo(json.readTree(listBody).get("total").asLong());
        mvc.perform(shanghai().param("q", "潍坊一村").param("metroLine", "4号线").param("maxMetroWalkMinutes", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(9));
    }

    @Test void centerModeFindsClosestAndAppliesStationFilter() throws Exception {
        var result = json.readTree(mvc.perform(shanghai().param("centerLongitude", "121.52").param("centerLatitude", "31.22")
                        .param("limit", "500").param("nearestMetro", "塘桥").param("town", "潍坊新村街道"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.limit").value(6)).andReturn().getResponse().getContentAsString());
        assertThat(result.get("items").get(0).get("bedrooms").asInt()).isEqualTo(1);
        result.get("items").forEach(item -> assertThat(item.get("nearestMetro").asText()).isEqualTo("塘桥"));
        mvc.perform(shanghai().param("centerLongitude", "121.52")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/listing-filters")).andExpect(status().isOk()).andExpect(jsonPath("$.metroStations", hasItem("塘桥")));
        mvc.perform(get("/api/listings").param("nearestMetro", "塘桥")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nearestMetro").value("塘桥"));
    }

    @Test void keepsExistingListAndDetailContracts() throws Exception {
        mvc.perform(get("/api/listings"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1500))
                .andExpect(jsonPath("$.items[0].id").value("SH-MOCK-000001"));
        mvc.perform(get("/api/listings/SH-MOCK-000201"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("SH-MOCK-000201"))
                .andExpect(jsonPath("$.longitude").value(nullValue()))
                .andExpect(jsonPath("$.locationStatus").value("UNVERIFIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6",
            "minLongitude=121.5&minLatitude=31.2&maxLatitude=31.3",
            "minLongitude=121.6&minLatitude=31.2&maxLongitude=121.5&maxLatitude=31.3",
            "minLongitude=121.5&minLatitude=31.3&maxLongitude=121.6&maxLatitude=31.2",
            "minLongitude=181&minLatitude=31.2&maxLongitude=181&maxLatitude=31.3",
            "minLongitude=121.5&minLatitude=-91&maxLongitude=121.6&maxLatitude=31.3",
            "minLongitude=abc&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3",
            "minLongitude=121.1234567&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3",
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3&limit=0",
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3&limit=501",
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3&limit=abc",
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3&page=0",
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3&hasCoordinates=true",
            "minLongitude=&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3",
            "minLongitude=121.5&minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3",
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3&sort=rent;DROP TABLE listing",
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3&bedrooms=0",
            "minLongitude=121.5&minLatitude=31.2&maxLongitude=121.6&maxLatitude=31.3&rentalType=REAL"
    })
    void rejectsInvalidMapParameters(String query) throws Exception {
        mvc.perform(get(URI.create("/api/listings/map?" + query.replace(" ", "%20"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }
}