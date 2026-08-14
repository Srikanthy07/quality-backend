package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
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
    "spring.datasource.url=jdbc:h2:mem:concurrent_session_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ConcurrentSessionControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private LoginRateLimiterService loginRateLimiterService;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
                .andReturn();
    }

    @Test
    void test1_FirstLogin_SucceedsAndAccessesProtectedPages() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        assertNotNull(session1);

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().isOk());
    }

    @Test
    void test2_SecondLogin_SucceedsBothSessionsActive() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");

        assertNotNull(session1);
        assertNotNull(session2);
        assertNotEquals(session1.getId(), session2.getId());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
    }

    @Test
    void test3_ThirdLogin_ExpiresOldestSessionAndKeepsNewestTwoActive() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session3 = performLogin("admin", "Admin#Pass2026!");

        // Session 1 must be expired and redirected to /admin/login?expired=true
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?evicted=true"));

        // Session 2 and Session 3 must remain active
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session3))
                .andExpect(status().isOk());
    }

    @Test
    void test4_FourthLogin_ExpiresSession2AndKeepsSessions3And4Active() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session3 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session4 = performLogin("admin", "Admin#Pass2026!");

        // Session 1 and Session 2 must be expired
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?evicted=true"));

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?evicted=true"));

        // Session 3 and Session 4 must remain active
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session3))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session4))
                .andExpect(status().isOk());
    }

    @Test
    void test5_ManualLogout_FreesSessionSlotForNewLogin() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");

        // Session 1 logs out manually
        mockMvc.perform(post("/admin/logout").secure(true).with(csrf()).session(session1))
                .andExpect(status().is3xxRedirection());

        // Login session 3
        MockHttpSession session3 = performLogin("admin", "Admin#Pass2026!");

        // Session 2 and Session 3 should both be active
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session3))
                .andExpect(status().isOk());
    }

    @Test
    void test6_IncorrectPassword_IsRejectedAndDoesNotCreateSession() throws Exception {
        mockMvc.perform(post("/admin/login")
                .secure(true)
                .with(csrf())
                .param("username", "admin")
                .param("password", "WrongPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error=true"));
    }

    @Test
    void test7_RememberMeAuthentication_WorksNormally() throws Exception {
        MvcResult loginResult = performLoginWithRememberMe("admin", "Admin#Pass2026!");
        Cookie rememberMeCookie = loginResult.getResponse().getCookie("remember-me");
        assertNotNull(rememberMeCookie, "Remember-me cookie should be generated");

        // Authenticate new request with ONLY the remember-me cookie (no JSESSIONID)
        mockMvc.perform(get("/admin/dashboard")
                .secure(true)
                .cookie(rememberMeCookie))
                .andExpect(status().isOk());
    }

    @Test
    void test8_RememberMeCannotBypassMaxTwoSessions() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");

        // Login Session 2 with Remember-Me
        MvcResult rmLoginResult = performLoginWithRememberMe("admin", "Admin#Pass2026!");
        MockHttpSession session2 = (MockHttpSession) rmLoginResult.getRequest().getSession();
        Cookie rememberMeCookie2 = rmLoginResult.getResponse().getCookie("remember-me");
        assertNotNull(rememberMeCookie2);

        // Authenticate 3rd session via Remember-Me cookie without JSESSIONID
        MvcResult rmAccessResult = mockMvc.perform(get("/admin/dashboard")
                .secure(true)
                .cookie(rememberMeCookie2))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session3 = (MockHttpSession) rmAccessResult.getRequest().getSession();
        assertNotNull(session3);
        assertNotEquals(session2.getId(), session3.getId());

        // Session 1 must be expired because Session 3 (Remember-Me) became the 3rd active session
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
    void test9_ExpiredSessionCannotRegainAccessThroughRememberMe() throws Exception {
        // Login session 1 with Remember-Me
        MvcResult loginResult1 = performLoginWithRememberMe("admin", "Admin#Pass2026!");
        MockHttpSession session1 = (MockHttpSession) loginResult1.getRequest().getSession();
        Cookie rememberMeCookie1 = loginResult1.getResponse().getCookie("remember-me");

        // Login session 2 & 3
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession session3 = performLogin("admin", "Admin#Pass2026!");

        // Session 1 is now expired
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?evicted=true"));

        // Using rememberMeCookie1 on a NEW session creates session 4, which in turn expires session 2 (enforcing max 2 limit)
        MvcResult rmAccessResult = mockMvc.perform(get("/admin/dashboard").secure(true).cookie(rememberMeCookie1))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session4 = (MockHttpSession) rmAccessResult.getRequest().getSession();

        // Session 2 is now expired because session 4 was authenticated via Remember-Me
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?evicted=true"));

        // Sessions 3 and 4 remain active
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session3))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session4))
                .andExpect(status().isOk());
    }

    @Test
    void test10_MultipleTabsSharingSameJsessionId_CountAsOneSession() throws Exception {
        MockHttpSession session1 = performLogin("admin", "Admin#Pass2026!");

        // Simulate Tab 1, Tab 2, Tab 3 using the SAME session1 (JSESSIONID)
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/documents").secure(true).session(session1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/settings").secure(true).session(session1))
                .andExpect(status().isOk());

        // Now login session 2
        MockHttpSession session2 = performLogin("admin", "Admin#Pass2026!");

        // Both session1 (all tabs) and session2 are ACTIVE (total = 2 sessions)
        mockMvc.perform(get("/admin/dashboard").secure(true).session(session1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(session2))
                .andExpect(status().isOk());
    }

    @Test
    void test11_NaturalSessionTimeout_DisplaysCorrectExpirationMessage_WithoutAnotherLocationMention() throws Exception {
        mockMvc.perform(get("/admin/login?expired=true").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your session has expired. Please log in again.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("another location"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("another device"))));
    }

    @Test
    void test12_ConcurrentSessionEviction_DisplaysAnotherLocationMessage() throws Exception {
        mockMvc.perform(get("/admin/login?evicted=true").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your session has expired because your account was logged in from another location. Please log in again.")));
    }

    @Test
    void test13_ManualLogout_DisplaysSuccessMessage_WithoutExpirationErrorMessage() throws Exception {
        mockMvc.perform(get("/admin/login?logout=true").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You have been logged out successfully.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Your session has expired"))));
    }

    @Test
    void test14_ReLoginAfterTimeout_SucceedsAndCreatesNewSession() throws Exception {
        MockHttpSession oldSession = performLogin("admin", "Admin#Pass2026!");

        mockMvc.perform(get("/admin/login?expired=true").secure(true))
                .andExpect(status().isOk());

        MockHttpSession newSession = performLogin("admin", "Admin#Pass2026!");
        assertNotNull(newSession);
        assertNotEquals(oldSession.getId(), newSession.getId());

        mockMvc.perform(get("/admin/dashboard").secure(true).session(newSession))
                .andExpect(status().isOk());
    }

    @Test
    void test15_MaximumConcurrentSessionsRemainsExactlyTwo() throws Exception {
        MockHttpSession s1 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession s2 = performLogin("admin", "Admin#Pass2026!");
        MockHttpSession s3 = performLogin("admin", "Admin#Pass2026!");

        mockMvc.perform(get("/admin/dashboard").secure(true).session(s1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?evicted=true"));

        mockMvc.perform(get("/admin/dashboard").secure(true).session(s2)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/dashboard").secure(true).session(s3)).andExpect(status().isOk());
    }
}
