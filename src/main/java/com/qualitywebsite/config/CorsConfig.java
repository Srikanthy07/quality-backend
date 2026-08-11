package com.qualitywebsite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${ALLOWED_ORIGINS:https://quality.iast.com}")
    private String allowedOriginsEnv;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins;
        if ("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile)) {
            origins = Arrays.stream(allowedOriginsEnv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.equals("*") && !s.toLowerCase().contains("localhost") && !s.contains("127.0.0.1"))
                    .toList();
            if (origins.isEmpty()) {
                origins = List.of("https://quality.iast.com");
            }
        } else {
            origins = Arrays.asList(
                    "http://localhost:8080",
                    "http://localhost:8093",
                    "http://127.0.0.1:8080",
                    "http://127.0.0.1:8093",
                    "https://quality.iast.com"
            );
        }

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN", "X-XSRF-TOKEN", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}