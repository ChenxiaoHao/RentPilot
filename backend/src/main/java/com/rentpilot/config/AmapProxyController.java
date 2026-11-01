package com.rentpilot.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;



import java.nio.charset.StandardCharsets;

import java.util.concurrent.Semaphore;

@RestController
public class AmapProxyController {
    private static final int MAX_RESPONSE = 2 * 1024 * 1024;
    private static final Semaphore IN_FLIGHT = new Semaphore(16);

    private final String securityCode;
    private final String webKey;

    public AmapProxyController(@Value("${rentpilot.amap.security-code:}") String securityCode,
                               @Value("${rentpilot.amap.web-key:}") String webKey) {
        this.securityCode = securityCode.trim();
        this.webKey = webKey.trim();
    }

    @GetMapping("/_AMapService/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        String path = request.getRequestURI().substring("/_AMapService".length());
        if (!path.equals("/v3/log/init")) return ResponseEntity.badRequest().build();
        if (securityCode.isBlank() || webKey.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        String query = request.getQueryString();
        try {
            int keys = 0;
            if (query == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            if (query.length() > 4096) return ResponseEntity.badRequest().build();
            for (String parameter : query.split("&", -1)) {
                String[] pair = parameter.split("=", 2);
                String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                if (name.equalsIgnoreCase("jscode")) return ResponseEntity.badRequest().build();
                if (name.equalsIgnoreCase("key")) {
                    keys++;
                    if (pair.length != 2 || !webKey.equals(URLDecoder.decode(pair[1], StandardCharsets.UTF_8))) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
            if (keys != 1) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            if (!IN_FLIGHT.tryAcquire()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            try {
                String uri = "https://restapi.amap.com" + path + "?" + query + "&jscode=" + URLEncoder.encode(securityCode, StandardCharsets.UTF_8);
                var connection = (java.net.HttpURLConnection) URI.create(uri).toURL().openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(false);
                try {
                    int status = connection.getResponseCode();
                    InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    byte[] body;
                    if (stream == null) body = new byte[0];
                    else try (InputStream input = stream) { body = input.readNBytes(MAX_RESPONSE + 1); }
                    if (body.length > MAX_RESPONSE) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
                    HttpHeaders headers = new HttpHeaders();
                    String contentType = connection.getContentType();
                    if (contentType != null) headers.set(HttpHeaders.CONTENT_TYPE, contentType);
                    headers.setCacheControl("no-store");
                    return ResponseEntity.status(status).headers(headers).body(body);
                } finally { connection.disconnect(); }
            } finally { IN_FLIGHT.release(); }
        } catch (Exception e) { return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build(); }
    }
}


