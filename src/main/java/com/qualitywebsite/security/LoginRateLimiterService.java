package com.qualitywebsite.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = TimeUnit.MINUTES.toMillis(15);

    private final ConcurrentHashMap<String, Integer> attemptsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lockoutMap = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        Long lockoutUntil = lockoutMap.get(key);
        if (lockoutUntil != null) {
            if (System.currentTimeMillis() < lockoutUntil) {
                return true;
            } else {
                lockoutMap.remove(key);
                attemptsMap.remove(key);
            }
        }

        return false;
    }

    public void loginFailed(String key) {
        if (key == null || key.isBlank()) return;

        int attempts = attemptsMap.getOrDefault(key, 0) + 1;
        attemptsMap.put(key, attempts);

        if (attempts >= MAX_ATTEMPTS) {
            long lockUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            lockoutMap.put(key, lockUntil);
            log.warn("[Security Alert] Login brute-force limit reached for key '{}'. Account/IP temporarily locked for 15 minutes.", key);
        }
    }

    public void loginSucceeded(String key) {
        if (key == null || key.isBlank()) return;
        attemptsMap.remove(key);
        lockoutMap.remove(key);
    }
}
