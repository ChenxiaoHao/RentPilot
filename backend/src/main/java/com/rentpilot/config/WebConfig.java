package com.rentpilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] origins;
    public WebConfig(@Value("${rentpilot.cors-origins}") String[] origins) { this.origins = origins; }

    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Accept", "Content-Type", "Authorization").allowCredentials(false).maxAge(600);
        registry.addMapping("/_AMapService/**").allowedOrigins(origins).allowedMethods("GET", "OPTIONS")
                .allowedHeaders("Accept", "Content-Type").allowCredentials(false).maxAge(600);
    }
}
