package com.example.chatapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

@Component
public class OriginPolicy {

    private static final List<String> LOCAL_DEVELOPMENT_PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://localhost:*",
            "https://127.0.0.1:*"
    );

    private final List<String> allowedOrigins;
    private final boolean allowLocalDevelopmentOrigins;
    private final boolean allowAnyOrigin;

    public OriginPolicy(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins,
            @Value("${app.cors.allow-local-development-origins:false}") boolean allowLocalDevelopmentOrigins,
            @Value("${app.cors.allow-any-origin:true}") boolean allowAnyOrigin) {
        this.allowedOrigins = parseOrigins(allowedOrigins);
        this.allowLocalDevelopmentOrigins = allowLocalDevelopmentOrigins;
        this.allowAnyOrigin = allowAnyOrigin;
    }

    public CorsConfiguration toCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(exactAllowedOrigins());
        configuration.setAllowedOriginPatterns(allowedOriginPatterns());
        if (configuration.getAllowedOrigins().isEmpty()) {
            configuration.setAllowedOrigins(null);
        }
        if (configuration.getAllowedOriginPatterns().isEmpty()) {
            configuration.setAllowedOriginPatterns(null);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        return configuration;
    }

    public String[] websocketAllowedOriginPatterns() {
        List<String> patterns = new java.util.ArrayList<>(allowedOrigins);
        patterns.addAll(localDevelopmentPatterns());
        patterns.addAll(anyOriginPattern());
        return patterns.toArray(String[]::new);
    }

    private List<String> exactAllowedOrigins() {
        return allowedOrigins.stream()
                .filter(origin -> !origin.contains("*"))
                .toList();
    }

    private List<String> allowedOriginPatterns() {
        List<String> patterns = new java.util.ArrayList<>(allowedOrigins.stream()
                .filter(origin -> origin.contains("*"))
                .toList());
        patterns.addAll(localDevelopmentPatterns());
        patterns.addAll(anyOriginPattern());
        return patterns;
    }

    private List<String> localDevelopmentPatterns() {
        return allowLocalDevelopmentOrigins ? LOCAL_DEVELOPMENT_PATTERNS : List.of();
    }

    private List<String> anyOriginPattern() {
        return allowAnyOrigin ? List.of("*") : List.of();
    }

    private List<String> parseOrigins(String origins) {
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
    }
}
