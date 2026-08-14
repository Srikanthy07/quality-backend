package com.qualitywebsite.security;

import org.junit.jupiter.api.Test;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;

public class ExtractKeystoreToPemTest {

    @Test
    void testExtractPem() throws Exception {
        String path = "d:\\quality-backend\\scratch\\testkeystore.p12";
        try (FileInputStream fis = new FileInputStream(path)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(fis, "password".toCharArray());
            String alias = ks.aliases().nextElement();
            Certificate cert = ks.getCertificate(alias);
            PrivateKey key = (PrivateKey) ks.getKey(alias, "password".toCharArray());

            String certPem = "-----BEGIN CERTIFICATE-----\n" +
                    Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(cert.getEncoded()) +
                    "\n-----END CERTIFICATE-----\n";

            String keyPem = "-----BEGIN PRIVATE KEY-----\n" +
                    Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded()) +
                    "\n-----END PRIVATE KEY-----\n";

            System.out.println("---CERT_START---");
            System.out.println(certPem);
            System.out.println("---CERT_END---");
            System.out.println("---KEY_START---");
            System.out.println(keyPem);
            System.out.println("---KEY_END---");
        }
    }
}
