package com.rentpilot.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/metro-lines")
class MetroLineController {
    private final MetroLineService service;
    MetroLineController(MetroLineService service) { this.service = service; }
    @GetMapping("/{line}/stations")
    MetroLineService.Result stations(@PathVariable String line) { return service.stations(line); }
}

@Service
class MetroLineService {
    private final ListingRepository listings;
    private final String key;
    private final ObjectMapper json;
    private final Map<String, Result> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    MetroLineService(ListingRepository listings, @Value("${rentpilot.amap.web-service-key:}") String key, ObjectMapper json) {
        this.listings = listings; this.key = key.trim(); this.json = json;
    }
    Result stations(String line) {
        var options = listings.filters();
        if (!options.metroLines().contains(line) || key.isBlank()) return new Result(line, "AMAP_WEB_SERVICE", "UNAVAILABLE", List.of());
        try {
            String encoded = URLEncoder.encode(line.equals("浦江线") ? "浦江线" : "地铁" + line, StandardCharsets.UTF_8);
            URI uri = URI.create("https://restapi.amap.com/v3/bus/linename?city=310000&extensions=all&offset=100&page=1&keywords=" + encoded + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8));
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) { response.body().close(); return unavailable(line); }
            byte[] bytes;
            try (var in = response.body()) { bytes = in.readNBytes(2 * 1024 * 1024 + 1); }
            if (bytes.length > 2 * 1024 * 1024) return unavailable(line);
            Result result = parse(line, json.readTree(bytes));
            if ("OK".equals(result.status())) cache.put(line, result);
            return result;
        } catch (Exception ignored) { return unavailable(line); }
    }
    static Result parse(String line, JsonNode root) {
        if (!"1".equals(root.path("status").asText()) || !root.path("buslines").isArray()) return unavailable(line);
        Set<String> matched = new TreeSet<>();
        boolean found = false;
        for (JsonNode route : root.path("buslines")) {
            String name = route.path("name").asText();
            String type = route.path("type").asText();
            if (!type.contains("地铁") || !(name.startsWith("地铁" + line + "(") || name.startsWith("地铁" + line + "（") || name.startsWith("轨道交通" + line + "(") || name.startsWith("轨道交通" + line + "（"))) continue;
            if (!route.path("busstops").isArray()) continue;
            found = true;
            for (JsonNode stop : route.path("busstops")) {
                String nameOfStop = stop.path("name").asText();
                if (nameOfStop.endsWith("站")) nameOfStop = nameOfStop.substring(0, nameOfStop.length() - 1);
                if (!nameOfStop.isBlank()) matched.add(nameOfStop);
            }
        }
        return found ? new Result(line, "AMAP_WEB_SERVICE", "OK", List.copyOf(matched)) : unavailable(line);
    }
    private static Result unavailable(String line) { return new Result(line, "AMAP_WEB_SERVICE", "UNAVAILABLE", List.of()); }
    record Result(String line, String source, String status, List<String> stations) {}
}


