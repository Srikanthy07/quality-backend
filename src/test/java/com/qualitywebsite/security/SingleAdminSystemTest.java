package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminPasswordResetToken;
import com.qualitywebsite.entity.AdminUser;
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
    "spring.datasource.url=jdbc:h2:mem:single_admin_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SingleAdminSystemTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminPasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        adminUserRepository.deleteAll();

        // Seed single administrator account
        AdminUser admin = AdminUser.builder()
                .username("admin")
                .email("admin@iast.com")
                .password(passwordEncoder.encode("Admin#Pass2026!"))
                .enabled(true)
                .build();
        adminUserRepository.save(admin);
    }

    @Test
    void test1_SingleAdminLogin_Succeeds() throws Exception {
        mockMvc.perform(post("/admin/login")
                .secure(true)
                .with(csrf())
                .param("username", "admin")
                .param("password", "Admin#Pass2026!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void test2_ForgotPassword_AndResetPasswordWorkflow() throws Exception {
        // Request reset
        mockMvc.perform(post("/api/public/admin/forgot-password")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@iast.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        AdminUser admin = adminUserRepository.findByUsername("admin").orElseThrow();

        // Create reset token manually
        AdminPasswordResetToken resetToken = AdminPasswordResetToken.builder()
                .adminUser(admin)
                .tokenHash(adminAuthService.hashTokenForTest("raw_test_reset_token"))
                .expiryDate(java.time.LocalDateTime.now().plusHours(1))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        // Execute reset password
        mockMvc.perform(post("/api/public/admin/reset-password")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw_test_reset_token\",\"newPassword\":\"NewStrongerPass2026!\",\"confirmPassword\":\"NewStrongerPass2026!\"}"))
                .andExpect(status().isOk());

        AdminUser updatedAdmin = adminUserRepository.findByUsername("admin").orElseThrow();
        assertTrue(passwordEncoder.matches("NewStrongerPass2026!", updatedAdmin.getPassword()));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void test3_ChangePassword_Succeeds() throws Exception {
        mockMvc.perform(post("/api/admin/change-password")
                .secure(true)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Admin#Pass2026!\",\"newPassword\":\"BrandNewPass2026!\",\"confirmPassword\":\"BrandNewPass2026!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireRelogin").value(true));

        AdminUser updatedAdmin = adminUserRepository.findByUsername("admin").orElseThrow();
        assertTrue(passwordEncoder.matches("BrandNewPass2026!", updatedAdmin.getPassword()));
    }

    @Test
    void test4_UnauthenticatedAccessToAdminRoutes_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard").secure(true))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/admin/dashboard").secure(true))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void test5_RemovedMultiAdminRoutes_Return404OrRedirect() throws Exception {
        mockMvc.perform(get("/admin/users").secure(true))
                .andExpect(status().is3xxRedirection()); // Secured path redirects to login
    }
}
