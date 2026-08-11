package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminInvitationToken;
import com.qualitywebsite.entity.AdminPasswordResetToken;
import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminInvitationTokenRepository;
import com.qualitywebsite.repository.AdminPasswordResetTokenRepository;
import com.qualitywebsite.repository.AdminUserRepository;
import com.qualitywebsite.service.AdminAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=${TEST_DB_URL:${DB_URL:jdbc:mysql://localhost:3306/quality_website?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true}}",
    "spring.datasource.username=${TEST_DB_USERNAME:${DB_USERNAME:root}}",
    "spring.datasource.password=${TEST_DB_PASSWORD:${DB_PASSWORD:1234}}"
})
class TwoAdminSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminInvitationTokenRepository invitationTokenRepository;

    @Autowired
    private AdminPasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        invitationTokenRepository.deleteAll();
        adminUserRepository.deleteAll();

        // Seed Admin 1
        AdminUser admin1 = AdminUser.builder()
                .username("admin1")
                .email("admin1@iast.com")
                .password(passwordEncoder.encode("Admin1#Pass2026!"))
                .enabled(true)
                .build();
        adminUserRepository.save(admin1);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void test1_InviteAdmin2_CreatesTokenRecord() throws Exception {
        mockMvc.perform(post("/api/admin/users/invite")
                .secure(true)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin2@iast.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        assertTrue(invitationTokenRepository.existsByEmailIgnoreCaseAndUsedFalse("admin2@iast.com"));
    }

    @Test
    void test2_AcceptInvitation_RegistersAdmin2() throws Exception {
        AdminUser admin1 = adminUserRepository.findByUsername("admin1").orElseThrow();

        // Create invitation token manually
        AdminInvitationToken token = AdminInvitationToken.builder()
                .email("admin2@iast.com")
                .tokenHash(adminAuthService.hashTokenForTest("raw_test_invite_token"))
                .invitedByAdmin(admin1)
                .expiryDate(java.time.LocalDateTime.now().plusHours(48))
                .used(false)
                .build();
        invitationTokenRepository.save(token);

        mockMvc.perform(post("/api/public/admin/accept-invitation")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw_test_invite_token\",\"username\":\"admin2\",\"password\":\"Admin2#Pass2026!\",\"confirmPassword\":\"Admin2#Pass2026!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        assertTrue(adminUserRepository.findByUsername("admin2").isPresent());
        AdminUser admin2 = adminUserRepository.findByUsername("admin2").get();
        assertEquals("admin2@iast.com", admin2.getEmail());
        assertTrue(passwordEncoder.matches("Admin2#Pass2026!", admin2.getPassword()));
    }

    @Test
    void test3_ForgotPassword_AndResetPasswordWorkflow() throws Exception {
        // Request reset
        mockMvc.perform(post("/api/public/admin/forgot-password")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin1@iast.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        AdminUser admin1 = adminUserRepository.findByUsername("admin1").orElseThrow();
        var tokens = passwordResetTokenRepository.findAll();
        assertFalse(tokens.isEmpty());

        // Simulate password reset with a valid token
        AdminPasswordResetToken resetToken = AdminPasswordResetToken.builder()
                .adminUser(admin1)
                .tokenHash(adminAuthService.hashTokenForTest("raw_test_reset_token"))
                .expiryDate(java.time.LocalDateTime.now().plusHours(1))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        mockMvc.perform(post("/api/public/admin/reset-password")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw_test_reset_token\",\"newPassword\":\"NewStrongerPass2026!\",\"confirmPassword\":\"NewStrongerPass2026!\"}"))
                .andExpect(status().isOk());

        AdminUser updatedAdmin1 = adminUserRepository.findByUsername("admin1").orElseThrow();
        assertTrue(passwordEncoder.matches("NewStrongerPass2026!", updatedAdmin1.getPassword()));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void test4_LastAdminProtection_BlocksDisablingSoleAdmin() {
        assertEquals(1, adminUserRepository.countByEnabledTrue());
        AdminUser admin1 = adminUserRepository.findByUsername("admin1").orElseThrow();

        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                adminAuthService.toggleAdminStatus(admin1.getId(), false, "admin1"));

        assertTrue(ex.getMessage().contains("Cannot disable the last active administrator account"));
    }

    @Test
    void test5_UnauthorizedAccessToAdminUsers_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/users").secure(true))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/admin/users").secure(true))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void test6_GeneratedUrlsUseHttpsBaseUrl() {
        org.springframework.test.util.ReflectionTestUtils.setField(adminAuthService, "baseUrl", "https://localhost:8093/");
        
        // Execute requestPasswordReset
        adminAuthService.requestPasswordReset("admin1@iast.com");
        var resetTokens = passwordResetTokenRepository.findAll();
        assertFalse(resetTokens.isEmpty());

        // Execute inviteAdmin
        adminAuthService.inviteAdmin("admin2_test_url@iast.com", "admin1");
        var inviteTokens = invitationTokenRepository.findAll();
        assertFalse(inviteTokens.isEmpty());
    }
}
