package com.example.chatapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginPolicyTest {

    @Test
    void productionCorsAllowsOnlyExplicitTrustedOrigins() {
        OriginPolicy policy = new OriginPolicy("https://app.example.com,https://admin.example.com", false, false);

        CorsConfiguration configuration = policy.toCorsConfiguration();

        assertEquals("https://app.example.com", configuration.checkOrigin("https://app.example.com"));
        assertEquals("https://admin.example.com", configuration.checkOrigin("https://admin.example.com"));
        assertNull(configuration.checkOrigin("https://evil.example.com"));
        assertTrue(configuration.getAllowedOriginPatterns() == null || configuration.getAllowedOriginPatterns().isEmpty());
    }

    @Test
    void wildcardCorsIsAllowedForCurrentTestingMode() {
        CorsConfiguration configuration = new OriginPolicy("*", false, false).toCorsConfiguration();

        assertNull(configuration.getAllowedOrigins());
        assertEquals("https://anywhere.example.com", configuration.checkOrigin("https://anywhere.example.com"));
    }

    @Test
    void localDevelopmentOriginPatternsAreOptIn() {
        OriginPolicy productionPolicy = new OriginPolicy("https://app.example.com", false, false);
        OriginPolicy developmentPolicy = new OriginPolicy("https://app.example.com", true, false);

        assertNull(productionPolicy.toCorsConfiguration().checkOrigin("http://localhost:3000"));
        assertEquals("http://localhost:3000", developmentPolicy.toCorsConfiguration().checkOrigin("http://localhost:3000"));
    }

    @Test
    void testingModeAllowsLanOriginEvenWhenExplicitOriginsAreStale() {
        OriginPolicy policy = new OriginPolicy("http://localhost:3000", false, true);

        CorsConfiguration configuration = policy.toCorsConfiguration();

        assertEquals("http://192.168.0.121:3000", configuration.checkOrigin("http://192.168.0.121:3000"));
    }
}
