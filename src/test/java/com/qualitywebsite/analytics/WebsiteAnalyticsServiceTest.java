package com.qualitywebsite.analytics;

import com.qualitywebsite.dto.AnalyticsSummaryDTO;
import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.entity.WebsiteVisitor;
import com.qualitywebsite.repository.AdminUserRepository;
import com.qualitywebsite.repository.DocumentDownloadLogRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.SearchLogRepository;
import com.qualitywebsite.repository.WebsiteVisitorRepository;
import com.qualitywebsite.service.WebsiteAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "analytics.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:analytics_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.qualitywebsite.security.LoginRateLimiterService loginRateLimiterService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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

        websiteAnalyticsService.logVisit(visitorId, sessionId, "/index.html", "Home Page", "Chrome", "Windows", "Desktop", null, true, true);
        websiteAnalyticsService.logVisit(visitorId, sessionId, "/master-list.html", "Master List", "Chrome", "Windows", "Desktop", null, false, false);
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
    void test3_SessionTimeoutCreatesNewSessionAndIncrementsVisitorsCount() {
        String visitorId = "vid_" + UUID.randomUUID();
        String session1 = "sid_1_" + UUID.randomUUID();
        String session2 = "sid_2_" + UUID.randomUUID();

        websiteAnalyticsService.logVisit(visitorId, session1, "/", "Home", "Chrome", "Windows", "Desktop", null, true, true);
        websiteAnalyticsService.logVisit(visitorId, session2, "/", "Home", "Chrome", "Windows", "Desktop", null, true, false);

        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);

        assertEquals(2, summary.getVisitors(), "Visitors count should be 2 (total sessions)");
        assertEquals(2, summary.getSessions(), "Session count should be 2");
        assertEquals(1, summary.getUniqueVisitors(), "Unique visitors count should remain 1");
        assertEquals(2, summary.getPageViews(), "Page views count should be 2");
    }

    @Test
    void test4_ReturningVisitorIsCorrectlyIdentifiedAndCountsNewVisit() {
        String visitorId = "vid_" + UUID.randomUUID();
        String session1 = "sid_1_" + UUID.randomUUID();
        String session2 = "sid_2_" + UUID.randomUUID();

        websiteAnalyticsService.logVisit(visitorId, session1, "/", "Home", "Chrome", "Windows", "Desktop", null, true, true);
        websiteAnalyticsService.logVisit(visitorId, session2, "/master-list.html", "Master List", "Chrome", "Windows", "Desktop", null, true, false);

        long returningCount = websiteVisitorRepository.countReturningVisitors();
        assertTrue(returningCount >= 1, "Returning visitor should be identified");

        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);
        assertEquals(2, summary.getVisitors(), "Total visits should be 2");
        assertEquals(1, summary.getUniqueVisitors(), "Unique visitors should be 1");
    }

    @Test
    void test4b_PageRefreshAndNavigationDoNotIncrementVisitorsCount() {
        String visitorId = "vid_" + UUID.randomUUID();
        String sessionId = "sid_" + UUID.randomUUID();

        websiteAnalyticsService.logVisit(visitorId, sessionId, "/index.html", "Home", "Chrome", "Windows", "Desktop", null, true, true);
        websiteAnalyticsService.logVisit(visitorId, sessionId, "/index.html", "Home", "Chrome", "Windows", "Desktop", null, false, false);
        websiteAnalyticsService.logVisit(visitorId, sessionId, "/master-list.html", "Master List", "Chrome", "Windows", "Desktop", null, false, false);

        AnalyticsSummaryDTO summary = websiteAnalyticsService.getSummary("30days", null, null);
        assertEquals(1, summary.getVisitors(), "Visitors count should be 1 for same session");
        assertEquals(3, summary.getPageViews(), "Page views count should be 3");
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
        mockMvc.perform(get("/css/style.css").secure(true)).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/documents").secure(true)).andExpect(status().isOk());

        long count = websiteVisitorRepository.count();
        assertEquals(0, count, "Static assets and API requests must not create page view records");
    }

    @Test
    void test8_VisitorCreatedOnDay1RemainsAvailableOnDay2AndDay3() {
        ZoneId zoneKolkata = ZoneId.of("Asia/Kolkata");
        ZoneId zoneUtc = ZoneId.of("UTC");

        LocalDate todayKolkata = LocalDate.now(zoneKolkata);
        LocalDate day1Kolkata = todayKolkata.minusDays(2);
        LocalDate day2Kolkata = todayKolkata.minusDays(1);

        LocalDateTime day1Utc = day1Kolkata.atTime(10, 0).atZone(zoneKolkata).withZoneSameInstant(zoneUtc).toLocalDateTime();
        LocalDateTime day2Utc = day2Kolkata.atTime(14, 0).atZone(zoneKolkata).withZoneSameInstant(zoneUtc).toLocalDateTime();

        WebsiteVisitor v1 = WebsiteVisitor.builder()
                .visitorId("visitor_day1")
                .sessionId("session_day1")
                .pageUrl("/")
                .pageTitle("Home")
                .visitTime(day1Utc)
                .lastActivityTime(day1Utc)
                .sessionStart(day1Utc)
                .sessionEnd(day1Utc)
                .pageViews(2)
                .browser("Chrome")
                .operatingSystem("Windows")
                .deviceType("Desktop")
                .isReturning(false)
                .createdAt(day1Utc)
                .build();
        websiteVisitorRepository.save(v1);

        WebsiteVisitor v2 = WebsiteVisitor.builder()
                .visitorId("visitor_day2")
                .sessionId("session_day2")
                .pageUrl("/master-list.html")
                .pageTitle("Master List")
                .visitTime(day2Utc)
                .lastActivityTime(day2Utc)
                .sessionStart(day2Utc)
                .sessionEnd(day2Utc)
                .pageViews(1)
                .browser("Firefox")
                .operatingSystem("Linux")
                .deviceType("Desktop")
                .isReturning(false)
                .createdAt(day2Utc)
                .build();
        websiteVisitorRepository.save(v2);

        // Verify Yesterday filter returns Day 2 records
        AnalyticsSummaryDTO yesterdaySummary = websiteAnalyticsService.getSummary("yesterday", null, null);
        assertEquals(1, yesterdaySummary.getVisitors(), "Yesterday should return 1 visitor");

        // Verify Last 7 Days filter includes both Day 1 and Day 2 records
        AnalyticsSummaryDTO last7DaysSummary = websiteAnalyticsService.getSummary("7days", null, null);
        assertEquals(2, last7DaysSummary.getVisitors(), "Last 7 Days should include Day 1 and Day 2 visitors");

        // Verify Overall / All Time includes all historical records
        AnalyticsSummaryDTO overallSummary = websiteAnalyticsService.getSummary("overall", null, null);
        assertEquals(2, overallSummary.getOverallVisitors(), "Overall visitors should be 2");
    }

    @Test
    void test9_YesterdayFilterReturnsYesterdayRecordsInAsiaKolkataTimezone() {
        ZoneId zoneKolkata = ZoneId.of("Asia/Kolkata");
        ZoneId zoneUtc = ZoneId.of("UTC");

        LocalDate todayKolkata = LocalDate.now(zoneKolkata);
        LocalDate yesterdayKolkata = todayKolkata.minusDays(1);

        LocalDateTime yesterdayNoonUtc = yesterdayKolkata.atTime(12, 0).atZone(zoneKolkata).withZoneSameInstant(zoneUtc).toLocalDateTime();

        WebsiteVisitor v = WebsiteVisitor.builder()
                .visitorId("visitor_yesterday")
                .sessionId("session_yesterday")
                .pageUrl("/index.html")
                .pageTitle("Home Page")
                .visitTime(yesterdayNoonUtc)
                .lastActivityTime(yesterdayNoonUtc)
                .sessionStart(yesterdayNoonUtc)
                .sessionEnd(yesterdayNoonUtc)
                .pageViews(3)
                .browser("Edge")
                .operatingSystem("Windows")
                .deviceType("Desktop")
                .isReturning(false)
                .createdAt(yesterdayNoonUtc)
                .build();
        websiteVisitorRepository.save(v);

        AnalyticsSummaryDTO yesterdaySummary = websiteAnalyticsService.getSummary("yesterday", null, null);
        assertEquals(1, yesterdaySummary.getVisitors(), "Yesterday summary should return 1 visitor");
        assertEquals(3, yesterdaySummary.getPageViews(), "Yesterday summary should return 3 page views");
    }

    @Test
    void test10_ResetEndpointNoLongerExists_Returns404NotFound() throws Exception {
        mockMvc.perform(post("/api/admin/analytics/reset")
                .secure(true)
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void test11_ClearAnalyticsDataButtonIsRemovedFromHtmlTemplate() throws Exception {
        mockMvc.perform(get("/admin/website-analytics")
                .secure(true)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Clear Analytics Data"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("clearAnalyticsModal"))));
    }
}
