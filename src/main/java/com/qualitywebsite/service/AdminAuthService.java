package com.qualitywebsite.service;

import com.qualitywebsite.entity.AdminPasswordResetToken;
import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminPasswordResetTokenRepository;
import com.qualitywebsite.repository.AdminUserRepository;
import com.qualitywebsite.security.LoginRateLimiterService;
import com.qualitywebsite.security.PasswordPolicyValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final AdminPasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final LoginRateLimiterService loginRateLimiterService;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${ADMIN_USERNAME:}")
    private String adminUsernameEnv;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmailEnv;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPasswordEnv;

    @Value("${ADMIN_PASSWORD_RESET:false}")
    private boolean adminPasswordResetEnv;

    @Value("${app.base-url:https://localhost:8093}")
    private String baseUrl;

    private String getSanitizedBaseUrl() {
        if (!StringUtils.hasText(baseUrl)) {
            return "https://localhost:8093";
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

    @PostConstruct
    public void initDefaultAdmin() {
        String username = StringUtils.hasText(adminUsernameEnv) ? adminUsernameEnv.trim() : null;
        String email = StringUtils.hasText(adminEmailEnv) ? adminEmailEnv.trim() : null;
        String password = StringUtils.hasText(adminPasswordEnv) ? adminPasswordEnv.trim() : null;

        if (adminPasswordResetEnv) {
            log.info("[Security Notice] ADMIN_PASSWORD_RESET=true detected. Processing explicit administrator password reset...");
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                throw new IllegalStateException("[Security Error] ADMIN_PASSWORD_RESET=true is set, but ADMIN_USERNAME or ADMIN_PASSWORD environment variable is missing or blank!");
            }

            PasswordPolicyValidator.validate(password);

            // Clear any brute-force lockout for this username
            loginRateLimiterService.loginSucceeded(username);

            Optional<AdminUser> existingOpt = adminUserRepository.findByUsername(username);
            if (existingOpt.isEmpty() && adminUserRepository.count() > 0) {
                existingOpt = adminUserRepository.findAll().stream().findFirst();
            }

            if (existingOpt.isPresent()) {
                AdminUser admin = existingOpt.get();
                admin.setUsername(username);
                if (email != null) admin.setEmail(email);
                admin.setPassword(passwordEncoder.encode(password));
                admin.setEnabled(true);
                adminUserRepository.save(admin);
                log.info("[Security Audit] Administrator account username set to '{}' and password reset successfully via ADMIN_PASSWORD_RESET=true.", username);
                activityLogService.logActivity("system", "Password Reset", "Administrator account ('" + username + "') username/password was reset via ADMIN_PASSWORD_RESET=true");
            } else {
                AdminUser newAdmin = AdminUser.builder()
                        .username(username)
                        .email(email)
                        .password(passwordEncoder.encode(password))
                        .enabled(true)
                        .build();
                adminUserRepository.save(newAdmin);
                log.info("[Security Audit] Created initial administrator account '{}' during explicit password reset request.", username);
                activityLogService.logActivity("system", "System Initialization", "Created admin account (" + username + ") during explicit password reset");
            }

            log.warn("[Security Alert] ADMIN_PASSWORD_RESET=true has been processed. Please set ADMIN_PASSWORD_RESET=false or remove it before restarting to prevent unintended password overrides.");
            return;
        }

        // Normal startup when ADMIN_PASSWORD_RESET is false
        if (adminUserRepository.count() == 0) {
            if (username != null && password != null) {
                try {
                    PasswordPolicyValidator.validate(password);
                } catch (IllegalArgumentException e) {
                    log.error("[Security Error] ADMIN_PASSWORD environment variable failed password policy validation: {}", e.getMessage());
                    return;
                }

                AdminUser admin = AdminUser.builder()
                        .username(username)
                        .email(email)
                        .password(passwordEncoder.encode(password))
                        .enabled(true)
                        .build();
                adminUserRepository.save(admin);
                log.info("Initialized initial administrator account from environment variables: {}", username);
                activityLogService.logActivity("system", "System Initialization", "Seeded admin account (" + username + ") from environment variables");
            } else {
                log.warn("[Security Notice] No admin account exists in database and ADMIN_USERNAME / ADMIN_PASSWORD environment variables are not set. Skipping automatic admin account creation.");
            }
        } else {
            adminUserRepository.findAll().forEach(a ->
                log.info("[Security Audit] Registered admin user in database: username='{}', email='{}', enabled={}, lastLoginAt={}", a.getUsername(), a.getEmail(), a.isEnabled(), a.getLastLoginAt())
            );
            log.info("Administrator account already exists in database. Existing password remains unchanged (ADMIN_PASSWORD_RESET=false).");
        }
    }

    public Optional<AdminUser> findByUsername(String username) {
        return adminUserRepository.findByUsername(username);
    }

    @Transactional
    public void updateLastLogin(String username) {
        if (username == null || username.isBlank()) return;
        adminUserRepository.findByUsername(username).ifPresent(admin -> {
            admin.setLastLoginAt(LocalDateTime.now());
            adminUserRepository.save(admin);
            log.info("[Security Audit] Updated last login timestamp for admin: {}", username);
        });
    }

    @Transactional
    public boolean changePassword(String username, String currentPassword, String newPassword) {
        PasswordPolicyValidator.validate(newPassword);

        Optional<AdminUser> opt = adminUserRepository.findByUsername(username);
        if (opt.isPresent()) {
            AdminUser admin = opt.get();
            if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
                log.warn("Password change failed for user {}: current password mismatch", username);
                return false;
            }
            if (passwordEncoder.matches(newPassword, admin.getPassword())) {
                throw new IllegalArgumentException("New password cannot be the same as current password.");
            }
            admin.setPassword(passwordEncoder.encode(newPassword));
            adminUserRepository.save(admin);
            log.info("[Security Audit] Password changed successfully for admin user: {}", username);
            activityLogService.logActivity(username, "Password Changed", "Administrator password was successfully updated");
            return true;
        } else {
            log.warn("Password change failed: user {} not found", username);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminProfile(String username) {
        Optional<AdminUser> opt = adminUserRepository.findByUsername(username);
        if (opt.isPresent()) {
            AdminUser u = opt.get();
            Map<String, Object> profile = new HashMap<>();
            profile.put("id", u.getId());
            profile.put("username", u.getUsername());
            profile.put("email", u.getEmail() != null ? u.getEmail() : "");
            profile.put("enabled", u.isEnabled());
            profile.put("status", u.isEnabled() ? "ACTIVE" : "DISABLED");
            profile.put("lastLoginAt", u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : "Never");
            profile.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "N/A");
            return profile;
        }
        return Map.of("username", username, "status", "ACTIVE", "lastLoginAt", "N/A");
    }

    @Transactional
    public void requestPasswordReset(String email) {
        if (!StringUtils.hasText(email)) {
            return; // Generic silent return to prevent user enumeration
        }

        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<AdminUser> adminOpt = adminUserRepository.findByEmailIgnoreCase(cleanEmail);

        if (adminOpt.isEmpty()) {
            log.info("[Security Notice] Password reset requested for non-existent admin email: {}", cleanEmail);
            return; // Generic silent return
        }

        AdminUser admin = adminOpt.get();
        if (!admin.isEnabled()) {
            log.warn("[Security Alert] Password reset requested for disabled admin account: {}", admin.getUsername());
            return;
        }

        passwordResetTokenRepository.deleteByAdminUser(admin);

        String rawToken = generateRandomToken();
        String tokenHash = hashToken(rawToken);

        AdminPasswordResetToken token = AdminPasswordResetToken.builder()
                .adminUser(admin)
                .tokenHash(tokenHash)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();

        passwordResetTokenRepository.save(token);

        String resetUrl = getSanitizedBaseUrl() + "/admin/reset-password?token=" + rawToken;
        try {
            emailService.sendPasswordResetEmail(cleanEmail, resetUrl, admin.getUsername());
            activityLogService.logActivity(admin.getUsername(), "Password Reset Requested", "Dispatched password reset token link to " + cleanEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", cleanEmail, e.getMessage());
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (!StringUtils.hasText(rawToken) || !StringUtils.hasText(newPassword)) {
            throw new IllegalArgumentException("Token and new password are required.");
        }

        String tokenHash = hashToken(rawToken);
        AdminPasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token."));

        if (token.isUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Password reset token has expired or already been used.");
        }

        PasswordPolicyValidator.validate(newPassword);

        AdminUser admin = token.getAdminUser();
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminUserRepository.save(admin);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        // Clear any login brute-force lockouts for this admin
        loginRateLimiterService.loginSucceeded(admin.getUsername());

        log.info("[Security Audit] Password reset successfully executed for administrator: {}", admin.getUsername());
        activityLogService.logActivity(admin.getUsername(), "Password Reset Complete", "Administrator password successfully reset via token link");
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public String hashTokenForTest(String rawToken) {
        return hashToken(rawToken);
    }
}
