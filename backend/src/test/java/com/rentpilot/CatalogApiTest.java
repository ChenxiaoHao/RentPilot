package com.rentpilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureMockMvc
class CatalogApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void healthReportsActuallyImportedDataset() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.dataset.rowCount").value(1500))
                .andExpect(jsonPath("$.dataset.dataKind").value("SIMULATED"))
                .andExpect(jsonPath("$.dataset.missingCoordinates").value(14))
                .andExpect(jsonPath("$.dataset.missingMetroWalk").value(423));
    }

    @Test void paginates1500ListingsWithoutOverlap() throws Exception {
        mvc.perform(get("/api/listings"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1500))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(75)).andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].id").value("SH-MOCK-000001"))
                .andExpect(jsonPath("$.dataKind").value("SIMULATED"))
                .andExpect(jsonPath("$.source.sha256").value("07ff43a3600e34cffbdc0de8751406cac33f6cfc330c169472ae739ed8abdfa8"));
        mvc.perform(get("/api/listings").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("SH-MOCK-000021"));
        mvc.perform(get("/api/listings").param("page", "100000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(1500));
    }

    @Test void appliesAllHardFiltersTogether() throws Exception {
        var body = mvc.perform(get("/api/listings").param("district", "浦东新区")
                        .param("rentalType", "ENTIRE").param("bedrooms", "1").param("maxRent", "3000")
                        .param("size", "100").param("sort", "rentAsc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(68))
                .andReturn().getResponse().getContentAsString();
        var items = json.readTree(body).get("items");
        int prior = 0;
        for (var item : items) {
            assertThat(item.get("district").asText()).isEqualTo("浦东新区");
            assertThat(item.get("rentalType").asText()).isEqualTo("ENTIRE");
            assertThat(item.get("bedrooms").asInt()).isEqualTo(1);
            assertThat(item.get("rent").asInt()).isBetween(prior, 3000);
            prior = item.get("rent").asInt();
        }
    }

    @Test void preservesAllSourceMeaningAndNullsInDetails() throws Exception {
        mvc.perform(get("/api/listings/SH-MOCK-000001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rent").value(2150))
                .andExpect(jsonPath("$.area").value(29.2)).andExpect(jsonPath("$.deposit").value(4300))
                .andExpect(jsonPath("$.amenities.gas").value("UNKNOWN"))
                .andExpect(jsonPath("$.amenities.petsAllowed").value("NO"))
                .andExpect(jsonPath("$.metroWalkMinutes").value(19))
                .andExpect(jsonPath("$.coordinateSystem").value("GCJ-02"))
                .andExpect(jsonPath("$.propertyFeePeriod").value("UNSPECIFIED"))
                .andExpect(jsonPath("$.dataKind").value("SIMULATED"));
        mvc.perform(get("/api/listings/SH-MOCK-000201"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.longitude").value(nullValue()))
                .andExpect(jsonPath("$.latitude").value(nullValue()))
                .andExpect(jsonPath("$.metroDistanceMeters").value(nullValue()))
                .andExpect(jsonPath("$.metroWalkMinutes").value(nullValue()))
                .andExpect(jsonPath("$.locationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.amenities.petsAllowed").value("UNKNOWN"));
    }

    @Test void searchesLiteralTextAndDoesNotInterpretSqlOrWildcards() throws Exception {
        mvc.perform(get("/api/listings").param("q", "  潍坊一村  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(9));
        for (String q : new String[]{"%", "_", "!", "' OR 1=1 --"}) {
            mvc.perform(get("/api/listings").param("q", q))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        }
    }

    @Test void filtersNullAndUnknownSeparatelyAndDoesNotTreatMetroWalkAsCommute() throws Exception {
        mvc.perform(get("/api/listings").param("hasCoordinates", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(14));
        mvc.perform(get("/api/listings").param("elevator", "UNKNOWN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(171));
        mvc.perform(get("/api/listings").param("maxMetroWalkMinutes", "180"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1077));
        mvc.perform(get("/api/listings").param("metroLine", "1号线"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(114));
    }

    @Test void usesStableSortingAndMissingMetroTimesLast() throws Exception {
        mvc.perform(get("/api/listings").param("sort", "rentAsc").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("SH-MOCK-000584"));
        mvc.perform(get("/api/listings").param("sort", "rentDesc").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].rent").value(10100));
        mvc.perform(get("/api/listings").param("sort", "metroWalkAsc").param("page", "14").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].metroWalkMinutes").value(nullValue()));
    }

    @Test void returnsEmptyAnd404AsDifferentOutcomes() throws Exception {
        mvc.perform(get("/api/listings").param("maxRent", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalPages").value(0));
        mvc.perform(get("/api/listings/not-present"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test void returnsRealFilterCountsAndExactMetroTokens() throws Exception {
        mvc.perform(get("/api/listing-filters"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.districts", hasSize(16)))
                .andExpect(jsonPath("$.towns", hasSize(218)))
                .andExpect(jsonPath("$.rentalTypes[?(@.value == 'SHARED')].count", contains(450)))
                .andExpect(jsonPath("$.metroLines", hasItems("1号线", "11号线", "市域机场线")))
                .andExpect(jsonPath("$.source.rowCount").value(1500));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=2147483648", "minRent=-1",
            "minRent=3000&maxRent=2000", "minArea=60&maxArea=20", "maxRent=NaN", "maxRent=1e999",
            "rentalType=REAL", "elevator=maybe", "hasCoordinates=yes", "sort=rent;DROP TABLE listing",
            "commute=30", "bedrooms=0", "maxMetroWalkMinutes=0", "size=abc", "q=", "page=0&page=1"})
    void rejectsInvalidOrUnsupportedFilters(String query) throws Exception {
        mvc.perform(get(java.net.URI.create("/api/listings?" + query.replace(" ", "%20"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test void permitsOnlyConfiguredCorsOrigins() throws Exception {
        mvc.perform(get("/api/health").header("Origin", "http://127.0.0.1:5178"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5178"));
        mvc.perform(get("/api/health").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
