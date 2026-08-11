package com.qualitywebsite.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SecurityConfig forces every request onto HTTPS (requiresSecure()).
 * If SSL_ENABLED=true but the cert env vars are missing, the app still
 * starts cleanly and only fails once real traffic hits it - the same
 * silent-failure pattern that hit the SMTP password bug. This fails
 * fast at boot instead.
 */
@Component
@Slf4j
public class SslConfigValidator {

    @Value("${server.ssl.enabled:${SSL_ENABLED:false}}")
    private boolean sslEnabled;

    @PostConstruct
    public void validate() {
        if (!sslEnabled) {
            return;
        }

        boolean missingCert = isBlank(System.getenv("SSL_CERTIFICATE"));
        boolean missingKey = isBlank(System.getenv("SSL_CERTIFICATE_PRIVATE_KEY"));

        if (missingCert || missingKey) {
            throw new IllegalStateException(
                "SSL_ENABLED is true but required certificate env vars are missing: "
                    + (missingCert ? "SSL_CERTIFICATE " : "")
                    + (missingKey ? "SSL_CERTIFICATE_PRIVATE_KEY" : "")
            );
        }

        log.info("SSL configuration validated - certificate and private key are present.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}