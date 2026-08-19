package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:admin_session_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminSessionTimeoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private LoginRateLimiterService loginRateLimiterService;

    @BeforeEach
    void setUp() {
        loginRateLimiterService.loginSucceeded("admin");
        adminUserRepository.deleteAll();

        AdminUser admin = AdminUser.builder()
                .username("admin")
                .email("admin@iast.com")
                .password(passwordEncoder.encode("Admin#Pass2026!"))
                .enabled(true)
                .build();
        adminUserRepository.save(admin);
    }

    private MockHttpSession performLogin(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/login")
                .secure(true)
                .with(csrf())
                .param("username", username)
                .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession();
    }

    private MvcResult performLoginWithRememberMe(String username, String password) throws Exception {
        return mockMvc.perform(post("/admin/login")
                .secure(true)
                .with(csrf())
                .param("username", username)
                .param("password", password)
                .param("remember-me", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andReturn();
    }

    @Test
    @DisplayName("1. Admin login succeeds with valid credentials")
    void test1_AdminLoginSucceeds() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        assertNotNull(session);
        assertFalse(session.isInvalid());
    }

    @Test
    @DisplayName("2. Unauthenticated users cannot access /admin/** endpoints")
    void test2_UnauthenticatedAccessToAdminRoutes_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard").secure(true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login*"));

        mockMvc.perform(get("/admin/documents").secure(true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login*"));
    }

    @Test
    @DisplayName("3. Authenticated administrators can continue using the Admin Portal")
    void test3_AdminSessionRemainsAccessible() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/documents").secure(true).session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("4. Public pages remain accessible without administrator authentication")
    void test4_PublicPagesAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/index.html").secure(true))
                .andExpect(status().isOk());

        mockMvc.perform(get("/master-list.html").secure(true))
                .andExpect(status().isOk());

        mockMvc.perform(get("/generic-template.html").secure(true))
                .andExpect(status().isOk());

        mockMvc.perform(get("/lessons-learned.html").secure(true))
                .andExpect(status().isOk());

        mockMvc.perform(get("/quality-checks.html").secure(true))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("5. Public document APIs remain accessible without authentication")
    void test5_PublicDocumentApisRemainAccessible() throws Exception {
        mockMvc.perform(get("/api/public/documents").secure(true))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/generic-templates").secure(true))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/lessons-learned").secure(true))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/master-list").secure(true))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/search?query=test").secure(true))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("6. Public users are never redirected to /admin/login because of removed timeout or invalid cookies")
    void test6_PublicUsersNeverRedirectedToAdminLogin() throws Exception {
        Cookie dummyCookie = new Cookie("JSESSIONID", "DUMMY_SESSION_ID_999");

        mockMvc.perform(get("/index.html").secure(true).cookie(dummyCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/master-list.html").secure(true).cookie(dummyCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/documents").secure(true).cookie(dummyCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("7. Existing Remember-Me functionality continues to work")
    void test7_RememberMeContinuesToWork() throws Exception {
        MvcResult result = performLoginWithRememberMe("admin", "Admin#Pass2026!");
        Cookie rememberMeCookie = result.getResponse().getCookie("remember-me");
        assertNotNull(rememberMeCookie);

        mockMvc.perform(get("/admin/dashboard").secure(true).cookie(rememberMeCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("8. Maximum two active sessions limit is enforced")
    void test8_MaximumTwoActiveSessionsLimit() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("9. Third login evicts oldest active session")
    void test9_ThirdLoginEvictsOldestSession() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session3 = performLogin("admin", "Admin#Pass2026!");

        // Session 1 must be evicted
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?evicted=true"));

        // Sessions 2 and 3 remain active
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session3))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("10. Manual logout frees session slot and invalidates session")
    void test10_ManualLogoutFreesSessionSlot() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");

        mockMvc.perform(post("/admin/logout").secure(true).with(csrf()).session(session1))
                .andExpect(status().is3xxRedirection());

        MockHttpSession session3 = performLogin("admin", "Admin#Pass2026!");
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session3))
                .andExpect(status().isOk());
    }
}
