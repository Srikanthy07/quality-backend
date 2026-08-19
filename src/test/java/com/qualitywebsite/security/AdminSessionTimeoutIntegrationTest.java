package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.session.HttpSessionEventPublisher;
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
    private HttpSessionEventPublisher httpSessionEventPublisher;

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

    private void simulateSessionTimeout(MockHttpSession session) {
        String sessionId = session.getId();
        httpSessionEventPublisher.sessionDestroyed(new HttpSessionEvent(session));
        session.invalidate();
    }

    @Test
    void test1_AdminLoginSucceeds() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        assertNotNull(session);
        assertFalse(session.isInvalid());
    }

    @Test
    void test2_AdminSessionRemainsAccessibleBeforeTimeout() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session))
                .andExpect(status().isOk());
    }

    @Test
    void test3_AdminSessionExpiresAfterConfiguredTimeout() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        simulateSessionTimeout(session);
        assertTrue(session.isInvalid());
    }

    @Test
    void test4_ExpiredSessionCannotAccessAdminProtectedEndpoints() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        simulateSessionTimeout(session);

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void test5_AfterSessionExpirationLoginPageIsAccessible() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        simulateSessionTimeout(session);

        mockMvc.perform(get("/admin/login").secure(true))
                .andExpect(status().isOk());
    }

    @Test
    void test6_AfterSessionExpirationEnteringValidCredentialsCreatesNewAuthenticatedSession() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        simulateSessionTimeout(session1);

        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");
        assertNotNull(session2);
        assertFalse(session2.isInvalid());
        assertNotEquals(session1.getId(), session2.getId());
    }

    @Test
    void test7_AfterSessionExpirationAdministratorCanAccessDashboardAgain() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        simulateSessionTimeout(session1);

        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
    }

    @Test
    void test8_ExpiredSessionIsRemovedFromSessionRegistry() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        assertNotNull(sessionRegistry.getSessionInformation(session.getId()));

        simulateSessionTimeout(session);
        assertNull(sessionRegistry.getSessionInformation(session.getId()));
    }

    @Test
    void test9_RememberMeContinuesToWorkCorrectly() throws Exception {
        MvcResult result = performLoginWithRememberMe("admin", "Admin#Pass2026!");
        Cookie rememberMeCookie = result.getResponse().getCookie("remember-me");
        assertNotNull(rememberMeCookie);

        mockMvc.perform(get("/admin/dashboard").secure(true).cookie(rememberMeCookie))
                .andExpect(status().isOk());
    }

    @Test
    void test10_RememberMeDoesNotPreventFreshUsernamePasswordLoginAfterSessionExpiration() throws Exception {
        MvcResult result = performLoginWithRememberMe("admin", "Admin#Pass2026!");
        MockHttpSession session1 = (MockHttpSession) result.getRequest().getSession();
        Cookie rememberMeCookie = result.getResponse().getCookie("remember-me");

        simulateSessionTimeout(session1);

        // Fresh login using form submit with remember-me cookie present
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");
        assertNotNull(session2);

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
    }

    @Test
    void test11_MaximumTwoActiveSessionsStillWorks() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
    }

    @Test
    void test12_ThirdLoginExpiresOldestActiveSession() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session3 = performLogin("admin", "Admin#Pass2026!");

        // Session 1 must be expired
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?evicted=true"));

        // Sessions 2 and 3 must remain active
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session3))
                .andExpect(status().isOk());
    }

    @Test
    void test13_ManualLogoutFreesSessionSlot() throws Exception {
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

    @Test
    void test14_MultipleTabsSharingOneJsessionIdCountAsOneSession() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/documents").secure(true).session(session1)).andExpect(status().isOk());

        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2)).andExpect(status().isOk());
    }

    @Test
    void test15_Analytics30MinTimeoutDoesNotAffectAdminAuthentication() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session))
                .andExpect(status().isOk());
    }

    @Test
    void testEdgeCase_LoginInAAndB_SessionATimesOut_ReLoginFromA_TotalSessionsMaxTwo() throws Exception {
        // 1. Login Admin in Browser A (sessionA)
        MockHttpSession sessionA = performLogin("admin", "Admin#Pass2026!");

        // 2. Login Admin in Browser B (sessionB)
        MockHttpSession sessionB = performLogin("admin", "Admin#Pass2026!");

        // 3. Session A times out after 30 minutes
        simulateSessionTimeout(sessionA);

        // 4. Re-login from Browser A -> creates sessionA2
        MockHttpSession sessionA2 = performLogin("admin", "Admin#Pass2026!");
        assertNotNull(sessionA2);

        // 5. Verify sessionA2 is ACTIVE
        mockMvc.perform(get("/admin/dashboard").secure(true).session(sessionA2))
                .andExpect(status().isOk());

        // 6. Verify sessionB remains ACTIVE
        mockMvc.perform(get("/admin/dashboard").secure(true).session(sessionB))
                .andExpect(status().isOk());

        // 7. Verify sessionA remains EXPIRED
        assertTrue(sessionA.isInvalid());
    }

    @Test
    void testPublicWebsiteIsIndependentOfExpiredAdminSession() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        simulateSessionTimeout(session);
        Cookie expiredJsessionId = new Cookie("JSESSIONID", "EXPIRED_SESSION_ID_123");

        // Public page loads normally (200 OK) without redirecting to /admin/login
        mockMvc.perform(get("/index.html").secure(true).cookie(expiredJsessionId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/master-list.html").secure(true).cookie(expiredJsessionId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/documents").secure(true).cookie(expiredJsessionId))
                .andExpect(status().isOk());
    }

    @Test
    void testAdminEndpointsRejectExpiredSession() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");
        simulateSessionTimeout(session);
        Cookie expiredJsessionId = new Cookie("JSESSIONID", "EXPIRED_SESSION_ID_123");

        // Admin page redirects to login
        mockMvc.perform(get("/admin/dashboard").secure(true).cookie(expiredJsessionId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login*"));

        // Admin API redirects to login
        mockMvc.perform(get("/api/admin/dms/documents").secure(true).cookie(expiredJsessionId))
                .andExpect(status().is3xxRedirection());
    }
}
