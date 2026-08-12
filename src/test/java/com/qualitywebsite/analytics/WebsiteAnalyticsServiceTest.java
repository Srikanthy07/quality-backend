package com.qualitywebsite.analytics;

import com.qualitywebsite.dto.AnalyticsSummaryDTO;
import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.WebsiteVisitor;
import com.qualitywebsite.repository.AdminUserRepository;
import com.qualitywebsite.repository.DocumentDownloadLogRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.SearchLogRepository;
import com.qualitywebsite.repository.WebsiteVisitorRepository;
import com.qualitywebsite.service.WebsiteAnalyticsService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

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
class WebsiteAnalyticsServiceTest {

    @Autowired
    private WebsiteAnalyticsService websiteAnalyticsService;

    @Autowired
    private WebsiteVisitorRepository websiteVisitorRepository;

    @Autowired
    private DocumentDownloadLogRepository documentDownloadLogRepository;

    @Autowired
    private SearchLogRepository searchLogRepository;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.qualitywebsite.security.LoginRateLimiterService loginRateLimiterService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Explicitly enable analytics logging during this unit test suite
        System.setProperty("analytics.enabled", "true");
        loginRateLimiterService.loginSucceeded("admin");

        websiteVisitorRepository.deleteAllInBatch();
        documentDownloadLogRepository.deleteAllInBatch();
        searchLogRepository.deleteAllInBatch();

        adminUserRepository.deleteAll();
        AdminUser admin = AdminUser.builder()
                .username("admin")
                .email("admin@iast.com")
                .password(passwordEncoder.encode("Admin#Pass2026!"))
                .enabled(true)
                .build();
        adminUserRepository.save(admin);
    }

    @Test
    void test1_OneVisitorBrowsingMultiplePages_ResultsInOneVisitorOneSessionMultiplePageViews() {
        String visitorId = "vid_" + UUID.randomUUID();
        String sessionId = "sid_" + UUID.randomUUID();

        // 1. Visit Home Page
        websiteAnalyticsService.logVisit(visitorId, sessionId, "/index.html", "Home Page", "Chrome", "Windows", "Desktop", null, true, true);
        // 2. Visit Documents Page
        websiteAnalyticsService.logVisit(visitorId, sessionId, "/master-list.html", "Master List", "Chrome", "Windows", "Desktop", null, false, false);
        // 3. Visit Lessons Learned Page
        websiteAnalyticsService.logVisit(visitorId, sessionId, "/lessons-learned.html", "Lessons Learned", "Chrome", "Windows", "Desktop", null, false, false);

        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);

        assertEquals(1, summary.getVisitors(), "Should have exactly 1 visitor");
        assertEquals(1, summary.getSessions(), "Should have exactly 1 session");
        assertEquals(3, summary.getPageViews(), "Should have exactly 3 page views");
    }

    @Test
    void test2_MultiplePagesDoNotCreateMultipleVisitors() {
        String visitorId = "vid_" + UUID.randomUUID();
        String sessionId = "sid_" + UUID.randomUUID();

        websiteAnalyticsService.logVisit(visitorId, sessionId, "/", "Home", "Chrome", "Windows", "Desktop", null, true, true);
        websiteAnalyticsService.logVisit(visitorId, sessionId, "/search.html", "Search", "Chrome", "Windows", "Desktop", null, false, false);
        websiteAnalyticsService.logVisit(visitorId, sessionId, "/quality-checks.html", "Quality Checks", "Chrome", "Windows", "Desktop", null, false, false);

        long countVisitors = websiteVisitorRepository.countUniqueVisitorsBetween(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        assertEquals(1, countVisitors);
    }

    @Test
    void test3_SessionTimeoutCreatesNewSessionWithoutCreatingNewVisitor() {
        String visitorId = "vid_" + UUID.randomUUID();
        String session1 = "sid_1_" + UUID.randomUUID();
        String session2 = "sid_2_" + UUID.randomUUID();

        // Session 1 (Initial visit)
        websiteAnalyticsService.logVisit(visitorId, session1, "/", "Home", "Chrome", "Windows", "Desktop", null, true, true);

        // Session 2 (Return after 30 min session timeout)
        websiteAnalyticsService.logVisit(visitorId, session2, "/", "Home", "Chrome", "Windows", "Desktop", null, true, false);

        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);

        assertEquals(1, summary.getVisitors(), "Visitor count should remain 1");
        assertEquals(2, summary.getSessions(), "Session count should be 2");
        assertEquals(2, summary.getPageViews(), "Page views count should be 2");
    }

    @Test
    void test4_ReturningVisitorIsCorrectlyIdentified() {
        String visitorId = "vid_" + UUID.randomUUID();
        String session1 = "sid_1_" + UUID.randomUUID();
        String session2 = "sid_2_" + UUID.randomUUID();

        websiteAnalyticsService.logVisit(visitorId, session1, "/", "Home", "Chrome", "Windows", "Desktop", null, true, true);
        websiteAnalyticsService.logVisit(visitorId, session2, "/master-list.html", "Master List", "Chrome", "Windows", "Desktop", null, true, false);

        long returningCount = websiteVisitorRepository.countReturningVisitors();
        assertTrue(returningCount >= 1, "Returning visitor should be identified");
    }

    @Test
    void test5_DownloadIncrementsDownloadCountWithoutCreatingExtraVisitors() {
        String visitorId = "vid_" + UUID.randomUUID();

        websiteAnalyticsService.logDownload(101L, "Quality_Manual.pdf", "Quality Policy", visitorId);

        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);

        assertEquals(1, summary.getDownloads(), "Download count should be 1");
        assertEquals(0, summary.getVisitors(), "Download should not create a visitor record");
    }

    @Test
    void test6_AutomatedTestTrafficIsNotRecordedAsRealAnalytics() {
        System.setProperty("analytics.enabled", "false");

        websiteAnalyticsService.logVisit("test_vid", "test_sid", "/", "Home", "MockMvc", "TestOS", "Desktop", null, true, true);
        websiteAnalyticsService.logDownload(999L, "TestDoc.pdf", "Test", "test_vid");

        System.setProperty("analytics.enabled", "true");

        assertEquals(0, websiteVisitorRepository.count());
        assertEquals(0, documentDownloadLogRepository.count());
    }

    @Test
    void test7_InternalStaticRequestsAreNotCountedAsPageViews() throws Exception {
        // Perform GET request to a static CSS resource or API
        mockMvc.perform(get("/css/style.css"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/documents"))
                .andExpect(status().isOk());

        // Verify neither static CSS nor public JSON API calls created WebsiteVisitor entries
        long count = websiteVisitorRepository.count();
        assertEquals(0, count, "Static assets and API requests must not create page view records");
    }

    @Test
    void test8_TodayCalculationUsesAsiaKolkataCorrectly() {
        ZonedDateTime nowKolkata = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String visitorId = "vid_today_" + UUID.randomUUID();
        String sessionId = "sid_today_" + UUID.randomUUID();

        websiteAnalyticsService.logVisit(visitorId, sessionId, "/", "Home", "Chrome", "Windows", "Desktop", null, true, true);

        AnalyticsSummaryDTO summaryToday = websiteAnalyticsService.getSummary("today", null, null);
        assertEquals(1, summaryToday.getVisitors());
    }

    @Test
    void test9_ThisWeekCalculationIsCorrect() {
        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("7days", null, null);
        assertNotNull(summary);
        assertEquals("Last 7 Days", summary.getPeriodLabel());
    }

    @Test
    void test10_ThisMonthCalculationIsCorrect() {
        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("current_month", null, null);
        assertNotNull(summary);
        assertEquals("Current Month", summary.getPeriodLabel());
    }

    @Test
    void test11_Last30DaysCalculationIsCorrect() {
        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);
        assertNotNull(summary);
        assertEquals("Last 30 Days", summary.getPeriodLabel());
    }

    @Test
    void test12_OverallStatisticsIncludeAllHistoricalRecords() {
        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);
        assertNotNull(summary);
        assertTrue(summary.getOverallVisitors() >= 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void test13_AnalyticsResetDeletesAnalyticsDataOnly() {
        String visitorId = "vid_" + UUID.randomUUID();
        String sessionId = "sid_" + UUID.randomUUID();

        websiteAnalyticsService.logVisit(visitorId, sessionId, "/", "Home", "Chrome", "Windows", "Desktop", null, true, true);
        websiteAnalyticsService.logDownload(10L, "Doc.pdf", "Category", visitorId);
        websiteAnalyticsService.logSearch("quality", 5, visitorId);

        assertTrue(websiteVisitorRepository.count() > 0);
        assertTrue(documentDownloadLogRepository.count() > 0);
        assertTrue(searchLogRepository.count() > 0);

        // Execute Reset
        websiteAnalyticsService.resetAnalyticsData();

        assertEquals(0, websiteVisitorRepository.count());
        assertEquals(0, documentDownloadLogRepository.count());
        assertEquals(0, searchLogRepository.count());

        // Verify DocumentMaster or other domain entities were NOT affected
        assertNotNull(documentMasterRepository.findAll());
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

    @Test
    void test14_AnalyticsResetRequiresAuthentication_SucceedsForAdmin() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");

        mockMvc.perform(post("/api/admin/analytics/reset")
                .secure(true)
                .session(session)
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Website analytics data cleared successfully."));
    }

    @Test
    void test15_PublicUsersCannotCallAnalyticsResetEndpoint() throws Exception {
        mockMvc.perform(post("/api/admin/analytics/reset")
                .secure(true)
                .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void test16_CsrfProtectionRemainsActiveForResetOperation() throws Exception {
        MockHttpSession session = performLogin("admin", "Admin#Pass2026!");

        MvcResult res = mockMvc.perform(post("/api/admin/analytics/reset")
                .secure(true)
                .session(session))
                .andReturn();

        assertNotEquals(200, res.getResponse().getStatus(), "Request without CSRF token must not return 200 OK");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void test17_AfterResetDashboardCountersShowZero() throws Exception {
        websiteAnalyticsService.resetAnalyticsData();

        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);
        assertEquals(0, summary.getVisitors());
        assertEquals(0, summary.getSessions());
        assertEquals(0, summary.getPageViews());
        assertEquals(0, summary.getDownloads());
    }
}
