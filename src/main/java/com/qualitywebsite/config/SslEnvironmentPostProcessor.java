package com.qualitywebsite.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * EnvironmentPostProcessor to safely process SSL certificate and private key environment variables.
 * 
 * Supports:
 * 1. Direct PEM content supplied via SSL_CERTIFICATE and SSL_CERTIFICATE_PRIVATE_KEY (Option B deployment).
 * 2. Normalization of literal '\n' characters into real newlines.
 * 3. File path locations (e.g. classpath:..., file:..., or D:/server.crt) if intentionally supplied.
 */
public class SslEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String BUNDLE_CERT_PROP = "spring.ssl.bundle.pem.my-server-bundle.keystore.certificate";
    public static final String BUNDLE_KEY_PROP = "spring.ssl.bundle.pem.my-server-bundle.keystore.private-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawCert = environment.getProperty("SSL_CERTIFICATE");
        String rawKey = environment.getProperty("SSL_CERTIFICATE_PRIVATE_KEY");

        Map<String, Object> map = new HashMap<>();

        if (rawCert != null && !rawCert.isBlank()) {
            String processedCert = processSslProperty(rawCert);
            map.put(BUNDLE_CERT_PROP, processedCert);
        }

        if (rawKey != null && !rawKey.isBlank()) {
            String processedKey = processSslProperty(rawKey);
            map.put(BUNDLE_KEY_PROP, processedKey);
        }

        if (!map.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("sslPemEnvironmentPostProcessor", map));
        }
    }

    public static String processSslProperty(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }

        String normalized = normalizePem(raw);

        if (isPemContent(normalized)) {
            // Spring Boot's PemContent requires in-memory PEM content to start with -----BEGIN or pem:
            // Returning the normalized multi-line PEM string ensures Spring Boot treats it as PEM content directly
            return normalized;
        }

        return normalized;
    }

    public static String normalizePem(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();

        // Remove wrapping quotes if present (e.g. "-----BEGIN CERTIFICATE-----\n..." or '...')
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        // Convert literal '\n' (two-character sequence backslash + n) to actual newline character \n
        trimmed = trimmed.replace("\\n", "\n").replace("\\r", "");
        return trimmed;
    }

    public static boolean isPemContent(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalizePem(value);
        return normalized.contains("-----BEGIN CERTIFICATE-----")
                || normalized.contains("-----BEGIN PRIVATE KEY-----")
                || normalized.contains("-----BEGIN RSA PRIVATE KEY-----")
                || normalized.contains("-----BEGIN EC PRIVATE KEY-----")
                || normalized.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----")
                || normalized.contains("-----BEGIN")
                || normalized.startsWith("pem:");
    }
}
