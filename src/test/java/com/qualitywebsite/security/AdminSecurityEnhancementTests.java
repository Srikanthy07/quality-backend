package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import com.qualitywebsite.service.AdminAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:admin_security_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminSecurityEnhancementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        AdminUser admin = adminUserRepository.findByUsername("security_test_admin").orElseGet(() ->
                AdminUser.builder()
                        .username("security_test_admin")
                        .password(passwordEncoder.encode("ValidAdminPass123!"))
                        .enabled(true)
                        .build()
        );
        admin.setEnabled(true);
        admin.setPassword(passwordEncoder.encode("ValidAdminPass123!"));
        adminUserRepository.save(admin);
    }

    @Test
    void testDisabledAdminAccountCannotLogin() throws Exception {
        AdminUser disabledAdmin = adminUserRepository.findByUsername("disabled_test_admin").orElseGet(() ->
                AdminUser.builder()
                        .username("disabled_test_admin")
                        .password(passwordEncoder.encode("ValidAdminPass123!"))
                        .enabled(false)
                        .build()
        );
        disabledAdmin.setEnabled(false);
        adminUserRepository.save(disabledAdmin);

        mockMvc.perform(post("/admin/login").secure(true)
                        .with(csrf())
                        .param("username", "disabled_test_admin")
                        .param("password", "ValidAdminPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error=true"));
    }

    @Test
    void testSuccessfulLoginUpdatesLastLoginTimestamp() throws Exception {
        LocalDateTime beforeLogin = LocalDateTime.now().minusSeconds(2);

        mockMvc.perform(post("/admin/login").secure(true)
                        .with(csrf())
                        .param("username", "security_test_admin")
                        .param("password", "ValidAdminPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        AdminUser updated = adminUserRepository.findByUsername("security_test_admin").orElseThrow();
        assertNotNull(updated.getLastLoginAt(), "lastLoginAt must be updated after successful login");
        assertTrue(updated.getLastLoginAt().isAfter(beforeLogin), "lastLoginAt must be after beforeLogin timestamp");
    }

    @Test
    void testFailedLoginDoesNotUpdateLastLoginTimestamp() throws Exception {
        AdminUser admin = adminUserRepository.findByUsername("security_test_admin").orElseThrow();
        LocalDateTime originalLastLogin = admin.getLastLoginAt();

        mockMvc.perform(post("/admin/login").secure(true)
                        .with(csrf())
                        .param("username", "security_test_admin")
                        .param("password", "WrongPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error=true"));

        AdminUser afterFailedLogin = adminUserRepository.findByUsername("security_test_admin").orElseThrow();
        assertEquals(originalLastLogin, afterFailedLogin.getLastLoginAt(), "lastLoginAt must NOT change on failed login");
    }

    @Test
    @WithMockUser(username = "security_test_admin", roles = {"ADMIN"})
    void testPasswordChangeRejectsSamePassword() {
        assertThrows(IllegalArgumentException.class, () ->
                adminAuthService.changePassword("security_test_admin", "ValidAdminPass123!", "ValidAdminPass123!"));
    }

    @Test
    @WithMockUser(username = "security_test_admin", roles = {"ADMIN"})
    void testPasswordChangeRejectsShortPassword() {
        assertThrows(IllegalArgumentException.class, () ->
                adminAuthService.changePassword("security_test_admin", "ValidAdminPass123!", "Short123!"));
    }
}
