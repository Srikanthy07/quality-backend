package com.qualitywebsite.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyValidatorTest {

    @Test
    void testValidStrongPasswordAccepted() {
        assertDoesNotThrow(() -> PasswordPolicyValidator.validate("SecureP@ssw0rd2026!"));
    }

    @Test
    void testShortPasswordRejected() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> PasswordPolicyValidator.validate("P@ss1!"));
        assertTrue(ex.getMessage().contains("at least 12 characters"));
    }

    @Test
    void testMissingUppercaseRejected() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> PasswordPolicyValidator.validate("securep@ssw0rd2026!"));
        assertTrue(ex.getMessage().contains("uppercase letter"));
    }

    @Test
    void testMissingLowercaseRejected() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> PasswordPolicyValidator.validate("SECUREP@SSW0RD2026!"));
        assertTrue(ex.getMessage().contains("lowercase letter"));
    }

    @Test
    void testMissingDigitRejected() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> PasswordPolicyValidator.validate("SecureP@ssword!"));
        assertTrue(ex.getMessage().contains("digit"));
    }

    @Test
    void testMissingSpecialCharRejected() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> PasswordPolicyValidator.validate("SecurePassword2026"));
        assertTrue(ex.getMessage().contains("special character"));
    }

    @Test
    void testWeakCommonPasswordRejected() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> PasswordPolicyValidator.validate("admin12345678"));
        assertTrue(ex.getMessage().contains("too common or weak"));
    }
}
