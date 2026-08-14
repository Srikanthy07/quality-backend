package com.qualitywebsite.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestPemUtilsTest {

    @Test
    void testGenerateValidPemPair() {
        String[] pair = TestPemUtils.generateValidPemPair();
        assertNotNull(pair);
        assertEquals(2, pair.length);
        assertTrue(pair[0].startsWith("-----BEGIN CERTIFICATE-----"));
        assertTrue(pair[1].startsWith("-----BEGIN PRIVATE KEY-----"));
    }
}
