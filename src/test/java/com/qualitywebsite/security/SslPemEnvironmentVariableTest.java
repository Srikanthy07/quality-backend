package com.qualitywebsite.security;

import com.qualitywebsite.config.SslConfigValidator;
import com.qualitywebsite.config.SslEnvironmentPostProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "SSL_ENABLED=true",
    "http.port=0",
    "spring.datasource.url=jdbc:h2:mem:ssl_pem_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SslPemEnvironmentVariableTest {

    @BeforeAll
    static void setupSslProperties() {
        String[] pair = TestPemUtils.generateValidPemPair();
        // Escaped \n version simulating environment variables
        String escapedCert = pair[0].replace("\n", "\\n");
        String escapedKey = pair[1].replace("\n", "\\n");

        System.setProperty("SSL_CERTIFICATE", escapedCert);
        System.setProperty("SSL_CERTIFICATE_PRIVATE_KEY", escapedKey);
    }

    @AfterAll
    static void cleanupSslProperties() {
        System.clearProperty("SSL_CERTIFICATE");
        System.clearProperty("SSL_CERTIFICATE_PRIVATE_KEY");
        System.clearProperty("SSL_ENABLED");
        System.clearProperty("server.ssl.enabled");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @Autowired
    private SslConfigValidator sslConfigValidator;

    @Test
    @DisplayName("Verify PEM certificate and private key content detection and normalization")
    void testPemDetectionAndNormalization() {
        String rawCertEscaped = "-----BEGIN CERTIFICATE-----\\nLINE1\\nLINE2\\n-----END CERTIFICATE-----";
        String normalizedCert = SslEnvironmentPostProcessor.normalizePem(rawCertEscaped);

        assertTrue(SslEnvironmentPostProcessor.isPemContent(rawCertEscaped));
        assertTrue(normalizedCert.contains("\n"));
        assertFalse(normalizedCert.contains("\\n"));
        assertTrue(normalizedCert.startsWith("-----BEGIN CERTIFICATE-----"));

        String rawKeyEscaped = "-----BEGIN PRIVATE KEY-----\\nKEYLINE1\\nKEYLINE2\\n-----END PRIVATE KEY-----";
        String normalizedKey = SslEnvironmentPostProcessor.normalizePem(rawKeyEscaped);

        assertTrue(SslEnvironmentPostProcessor.isPemContent(rawKeyEscaped));
        assertTrue(normalizedKey.contains("\n"));
        assertFalse(normalizedKey.contains("\\n"));
        assertTrue(normalizedKey.startsWith("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    @DisplayName("Verify Spring Environment gets normalized PEM properties without file path requirement")
    void testEnvironmentGetsNormalizedPemProperties() {
        String certProp = environment.getProperty(SslEnvironmentPostProcessor.BUNDLE_CERT_PROP);
        String keyProp = environment.getProperty(SslEnvironmentPostProcessor.BUNDLE_KEY_PROP);

        assertNotNull(certProp, "PEM Certificate property should be set");
        assertNotNull(keyProp, "PEM Private Key property should be set");

        assertTrue(certProp.contains("\n"), "Certificate should have normalized real newlines");
        assertTrue(keyProp.contains("\n"), "Private key should have normalized real newlines");

        assertFalse(certProp.contains("\\n"), "Certificate should not contain literal \\n string");
        assertFalse(keyProp.contains("\\n"), "Private key should not contain literal \\n string");

        // Verify that neither property points to a D:\ or file path
        assertFalse(certProp.startsWith("D:"));
        assertFalse(certProp.startsWith("file:"));
        assertFalse(keyProp.startsWith("D:"));
        assertFalse(keyProp.startsWith("file:"));
    }

    @Test
    @DisplayName("Verify SslConfigValidator runs successfully with PEM environment variables")
    void testSslConfigValidatorPasses() {
        assertDoesNotThrow(() -> sslConfigValidator.validate());
    }

    @Test
    @DisplayName("Verify invalid PEM header produces clear configuration error rather than FileNotFoundException")
    void testInvalidPemContentProducesClearConfigurationError() {
        ConfigurableEnvironment mockEnv = new org.springframework.mock.env.MockEnvironment()
                .withProperty("server.ssl.enabled", "true")
                .withProperty("SSL_CERTIFICATE", "NON_EXISTENT_FILE_OR_BAD_PEM")
                .withProperty("SSL_CERTIFICATE_PRIVATE_KEY", "NON_EXISTENT_FILE_OR_BAD_KEY");

        SslConfigValidator validator = new SslConfigValidator(mockEnv);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(ex.getMessage().contains("does not exist") || ex.getMessage().contains("missing expected header"));
        assertFalse(ex.getMessage().contains("FileNotFoundException: D:\\server.crt"));
    }
}
