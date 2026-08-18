package com.qualitywebsite.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;

public class TestPemUtils {

    public static String[] generateValidPemPair() {
        try {
            InputStream input = TestPemUtils.class.getResourceAsStream("/testkeystore.p12");
            if (input == null) {
                File file = new File(System.getProperty("user.dir"), "src/test/resources/testkeystore.p12");
                if (file.exists()) {
                    input = new FileInputStream(file);
                } else {
                    file = new File("d:/quality-backend/src/test/resources/testkeystore.p12");
                    if (file.exists()) {
                        input = new FileInputStream(file);
                    }
                }
            }
            if (input == null) {
                throw new IllegalStateException("Could not locate testkeystore.p12 via classpath or file path");
            }
            try (InputStream is = input) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(is, "password".toCharArray());
                String alias = ks.aliases().nextElement();
                Certificate cert = ks.getCertificate(alias);
                PrivateKey key = (PrivateKey) ks.getKey(alias, "password".toCharArray());

                String certPem = "-----BEGIN CERTIFICATE-----\n" +
                        Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(cert.getEncoded()) +
                        "\n-----END CERTIFICATE-----\n";

                String keyPem = "-----BEGIN PRIVATE KEY-----\n" +
                        Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded()) +
                        "\n-----END PRIVATE KEY-----\n";

                return new String[]{certPem, keyPem};
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test PEM pair from testkeystore.p12", e);
        }
    }
}
