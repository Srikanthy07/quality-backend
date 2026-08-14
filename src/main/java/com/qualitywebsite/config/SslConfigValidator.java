package com.qualitywebsite.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Validates SSL configuration fast at boot when SSL_ENABLED is true.
 * 
 * Supports both:
 * 1. Direct PEM content passed via SSL_CERTIFICATE and SSL_CERTIFICATE_PRIVATE_KEY env vars.
 * 2. File paths passed intentionally (e.g. classpath:... or file:...).
 * 
 * Security: NEVER logs certificate or private key material.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SslConfigValidator {

    private final Environment environment;

    @PostConstruct
    public void validate() {
        boolean sslEnabled = Boolean.parseBoolean(
            environment.getProperty("server.ssl.enabled",
                environment.getProperty("SSL_ENABLED", "false"))
        );

        if (!sslEnabled) {
            return;
        }

        String certVal = getSslProperty("SSL_CERTIFICATE", SslEnvironmentPostProcessor.BUNDLE_CERT_PROP);
        String keyVal = getSslProperty("SSL_CERTIFICATE_PRIVATE_KEY", SslEnvironmentPostProcessor.BUNDLE_KEY_PROP);

        boolean missingCert = certVal == null || certVal.isBlank();
        boolean missingKey = keyVal == null || keyVal.isBlank();

        if (missingCert || missingKey) {
            throw new IllegalStateException(
                "SSL_ENABLED is true but required certificate env vars are missing: "
                    + (missingCert ? "SSL_CERTIFICATE " : "")
                    + (missingKey ? "SSL_CERTIFICATE_PRIVATE_KEY" : "")
            );
        }

        validatePemOrFilePath("SSL_CERTIFICATE", certVal, "-----BEGIN CERTIFICATE-----");
        validatePemOrFilePath("SSL_CERTIFICATE_PRIVATE_KEY", keyVal, "-----BEGIN");

        log.info("SSL configuration validated - certificate and private key are present.");
    }

    private String getSslProperty(String envName, String springPropName) {
        String val = environment.getProperty(envName);
        if (val == null || val.isBlank()) {
            val = System.getenv(envName);
        }
        if (val == null || val.isBlank()) {
            val = environment.getProperty(springPropName);
        }
        return val;
    }

    private void validatePemOrFilePath(String varName, String value, String pemHeaderSnippet) {
        String normalized = SslEnvironmentPostProcessor.normalizePem(value);

        if (SslEnvironmentPostProcessor.isPemContent(normalized)) {
            if (!normalized.contains(pemHeaderSnippet)) {
                throw new IllegalStateException("Invalid SSL configuration: " + varName + " PEM content is missing expected header format (" + pemHeaderSnippet + ")");
            }
            log.info("SSL {} environment variable detected (PEM content format).", varName);
        } else {
            // File path format validation
            if (normalized.startsWith("classpath:")) {
                log.info("SSL {} environment variable detected (Classpath resource format).", varName);
            } else {
                String path = normalized.startsWith("file:") ? normalized.substring(5) : normalized;
                File f = new File(path);
                if (!f.exists()) {
                    throw new IllegalStateException("Invalid SSL configuration: " + varName + " specified file path does not exist: " + path);
                }
                log.info("SSL {} environment variable detected (File path format).", varName);
            }
        }
    }
}