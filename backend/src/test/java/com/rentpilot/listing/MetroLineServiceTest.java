package com.rentpilot.listing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetroLineServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void acceptsOnlyExactMetroLineAndCombinesDirections() throws Exception {
        var root = json.readTree("""
          {"status":"1","buslines":[
            {"name":"地铁1号线(莘庄--富锦路)","type":"地铁","busstops":[{"name":"莘庄站"},{"name":"人民广场站"}]},
            {"name":"地铁1号线(富锦路--莘庄)","type":"地铁","busstops":[{"name":"人民广场站"},{"name":"富锦路站"}]},
            {"name":"地铁10号线(其他)","type":"地铁","busstops":[{"name":"误匹配站"}]},
            {"name":"地铁1号线(其他)","type":"普通公交","busstops":[{"name":"公交站"}]}
          ]}
          """);
        var result = MetroLineService.parse("1号线", root);
        assertEquals("OK", result.status());
        assertEquals(java.util.List.of("人民广场", "富锦路", "莘庄"), result.stations());
    }

    @Test void acceptsVerifiedPujiangRouteName() throws Exception {
        var root = json.readTree("{\"status\":\"1\",\"buslines\":[{\"name\":\"轨道交通浦江线(沈杜公路--汇臻路)\",\"type\":\"地铁\",\"busstops\":[{\"name\":\"沈杜公路站\"}]}]}");
        assertEquals(java.util.List.of("沈杜公路"), MetroLineService.parse("浦江线", root).stations());
    }

    @Test void upstreamErrorsAndMissingRoutesAreUnavailable() throws Exception {
        assertEquals("UNAVAILABLE", MetroLineService.parse("1号线", json.readTree("{\"status\":\"0\"}")).status());
        assertEquals("UNAVAILABLE", MetroLineService.parse("1号线", json.readTree("{\"status\":\"1\",\"buslines\":[]}")).status());
    }
}

