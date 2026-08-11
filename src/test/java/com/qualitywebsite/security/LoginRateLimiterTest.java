package com.qualitywebsite.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    private LoginRateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiterService();
    }

    @Test
    void testNotBlockedInitially() {
        assertFalse(rateLimiter.isBlocked("admin"));
    }

    @Test
    void testLockoutTriggeredAfter5FailedAttempts() {
        String username = "admin";
        for (int i = 0; i < 4; i++) {
            rateLimiter.loginFailed(username);
            assertFalse(rateLimiter.isBlocked(username));
        }

        rateLimiter.loginFailed(username);
        assertTrue(rateLimiter.isBlocked(username));
    }

    @Test
    void testSuccessfulLoginResetsAttempts() {
        String username = "admin";
        for (int i = 0; i < 4; i++) {
            rateLimiter.loginFailed(username);
        }

        rateLimiter.loginSucceeded(username);
        assertFalse(rateLimiter.isBlocked(username));

        rateLimiter.loginFailed(username);
        assertFalse(rateLimiter.isBlocked(username));
    }
}
