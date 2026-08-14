package com.qualitywebsite.security;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;

public class TestPemUtils {

    public static String[] generateValidPemPair() {
        try (InputStream is = TestPemUtils.class.getResourceAsStream("/testkeystore.p12")) {
            if (is == null) {
                throw new IllegalStateException("Classpath resource /testkeystore.p12 not found!");
            }
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test PEM pair from classpath: /testkeystore.p12", e);
        }
    }
}
