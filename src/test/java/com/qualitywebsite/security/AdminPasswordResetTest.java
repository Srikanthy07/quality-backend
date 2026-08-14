package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import com.qualitywebsite.service.ActivityLogService;
import com.qualitywebsite.service.AdminAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:admin_reset_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminPasswordResetTest {

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private com.qualitywebsite.repository.AdminPasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private LoginRateLimiterService rateLimiter;

    @Autowired
    private com.qualitywebsite.service.EmailService emailService;

    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        adminAuthService = new AdminAuthService(adminUserRepository, passwordResetTokenRepository, passwordEncoder, activityLogService, rateLimiter, emailService);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        adminUserRepository.deleteAll();
    }

    @Test
    void test1_NoAdminExists_InitialAdminCreated() {
        adminUserRepository.deleteAll();
        assertEquals(0, adminUserRepository.count());

        ReflectionTestUtils.setField(adminAuthService, "adminUsernameEnv", "admin_new");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordEnv", "S3cure!P@ssw0rd2026");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordResetEnv", false);

        adminAuthService.initDefaultAdmin();

        Optional<AdminUser> createdOpt = adminUserRepository.findByUsername("admin_new");
        assertTrue(createdOpt.isPresent());
        assertTrue(passwordEncoder.matches("S3cure!P@ssw0rd2026", createdOpt.get().getPassword()));
    }

    @Test
    void test2_AdminAlreadyExists_PasswordUnchanged() {
        adminUserRepository.deleteAll();
        AdminUser existing = AdminUser.builder()
                .username("admin_existing")
                .password(passwordEncoder.encode("ExistingP@ss1234"))
                .build();
        adminUserRepository.save(existing);

        ReflectionTestUtils.setField(adminAuthService, "adminUsernameEnv", "admin_existing");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordEnv", "DifferentP@ss2026!");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordResetEnv", false);

        adminAuthService.initDefaultAdmin();

        AdminUser updated = adminUserRepository.findByUsername("admin_existing").orElseThrow();
        assertTrue(passwordEncoder.matches("ExistingP@ss1234", updated.getPassword()));
        assertFalse(passwordEncoder.matches("DifferentP@ss2026!", updated.getPassword()));
    }

    @Test
    void test3_ExistingAdmin_ResetEnabled_PasswordUpdated() {
        adminUserRepository.deleteAll();
        AdminUser existing = AdminUser.builder()
                .username("admin_reset_test")
                .password(passwordEncoder.encode("OldP@ssword1234"))
                .build();
        adminUserRepository.save(existing);

        ReflectionTestUtils.setField(adminAuthService, "adminUsernameEnv", "admin_reset_test");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordEnv", "NewStrongP@ss2026!");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordResetEnv", true);

        adminAuthService.initDefaultAdmin();

        AdminUser resetUser = adminUserRepository.findByUsername("admin_reset_test").orElseThrow();
        assertTrue(passwordEncoder.matches("NewStrongP@ss2026!", resetUser.getPassword()));
    }

    @Test
    void test4_RestartAfterReset_PasswordNotChangedAgain() {
        adminUserRepository.deleteAll();
        AdminUser existing = AdminUser.builder()
                .username("admin_restart")
                .password(passwordEncoder.encode("OldP@ssword1234"))
                .build();
        adminUserRepository.save(existing);

        // Step A: Reset enabled
        ReflectionTestUtils.setField(adminAuthService, "adminUsernameEnv", "admin_restart");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordEnv", "ResetP@ssword1234!");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordResetEnv", true);
        adminAuthService.initDefaultAdmin();

        assertTrue(passwordEncoder.matches("ResetP@ssword1234!", adminUserRepository.findByUsername("admin_restart").orElseThrow().getPassword()));

        // Step B: Reset disabled (subsequent restart)
        ReflectionTestUtils.setField(adminAuthService, "adminUsernameEnv", "admin_restart");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordEnv", "AnotherP@ss2026!");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordResetEnv", false);
        adminAuthService.initDefaultAdmin();

        // Password must remain ResetP@ssword1234!, NOT AnotherP@ss2026!
        AdminUser afterRestart = adminUserRepository.findByUsername("admin_restart").orElseThrow();
        assertTrue(passwordEncoder.matches("ResetP@ssword1234!", afterRestart.getPassword()));
        assertFalse(passwordEncoder.matches("AnotherP@ss2026!", afterRestart.getPassword()));
    }

    @Test
    void test5_ResetEnabledButPasswordMissing_ThrowsException() {
        adminUserRepository.deleteAll();
        ReflectionTestUtils.setField(adminAuthService, "adminUsernameEnv", "admin");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordEnv", "");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordResetEnv", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> adminAuthService.initDefaultAdmin());
        assertTrue(ex.getMessage().contains("ADMIN_PASSWORD_RESET=true is set, but ADMIN_USERNAME or ADMIN_PASSWORD"));
    }

    @Test
    void test6_VerifyOldPasswordRejectedAndNewAccepted() {
        adminUserRepository.deleteAll();
        AdminUser existing = AdminUser.builder()
                .username("admin_auth_check")
                .password(passwordEncoder.encode("OldP@ssword1234"))
                .build();
        adminUserRepository.save(existing);

        ReflectionTestUtils.setField(adminAuthService, "adminUsernameEnv", "admin_auth_check");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordEnv", "BrandNewP@ss2026!");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordResetEnv", true);

        adminAuthService.initDefaultAdmin();

        AdminUser user = adminUserRepository.findByUsername("admin_auth_check").orElseThrow();
        assertFalse(passwordEncoder.matches("OldP@ssword1234", user.getPassword()));
        assertTrue(passwordEncoder.matches("BrandNewP@ss2026!", user.getPassword()));
    }

    @Test
    void test7_VerifyBcryptHashStoredInDatabase() {
        adminUserRepository.deleteAll();
        ReflectionTestUtils.setField(adminAuthService, "adminUsernameEnv", "admin_bcrypt");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordEnv", "SecureBcryptP@ss1!");
        ReflectionTestUtils.setField(adminAuthService, "adminPasswordResetEnv", false);

        adminAuthService.initDefaultAdmin();

        AdminUser user = adminUserRepository.findByUsername("admin_bcrypt").orElseThrow();
        String storedHash = user.getPassword();

        assertNotEquals("SecureBcryptP@ss1!", storedHash);
        assertTrue(storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$"));
    }
}
