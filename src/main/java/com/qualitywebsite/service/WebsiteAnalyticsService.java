package com.qualitywebsite.service;

import com.qualitywebsite.dto.*;
import com.qualitywebsite.entity.DocumentDownloadLog;
import com.qualitywebsite.entity.SearchLog;
import com.qualitywebsite.entity.WebsiteVisitor;
import com.qualitywebsite.repository.DocumentDownloadLogRepository;
import com.qualitywebsite.repository.SearchLogRepository;
import com.qualitywebsite.repository.WebsiteVisitorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebsiteAnalyticsService {

    private final WebsiteVisitorRepository websiteVisitorRepository;
    private final DocumentDownloadLogRepository documentDownloadLogRepository;
    private final SearchLogRepository searchLogRepository;

    @Async
    @Transactional
    public void logVisit(String visitorId, String sessionId, String pageUrl, String pageTitle,
                         String browser, String os, String deviceType, String referrer,
                         boolean isNewSession, boolean isNewVisitorCookie) {
        try {
            boolean isReturning = websiteVisitorRepository.countOtherSessions(visitorId, sessionId) > 0;
            LocalDateTime now = LocalDateTime.now();

            Optional<WebsiteVisitor> existingOpt = websiteVisitorRepository
                    .findFirstByVisitorIdAndSessionIdOrderByVisitTimeDesc(visitorId, sessionId);

            if (existingOpt.isPresent() && !isNewSession) {
                WebsiteVisitor existing = existingOpt.get();
                if (existing.getPageUrl().equalsIgnoreCase(pageUrl)) {
                    existing.setPageViews((existing.getPageViews() != null ? existing.getPageViews() : 1) + 1);
                    existing.setLastActivityTime(now);
                    existing.setSessionEnd(now);
                    websiteVisitorRepository.save(existing);
                    return;
                }
            }

            WebsiteVisitor visit = WebsiteVisitor.builder()
                    .visitorId(visitorId)
                    .sessionId(sessionId)
                    .pageUrl(pageUrl)
                    .pageTitle(pageTitle)
                    .visitTime(now)
                    .lastActivityTime(now)
                    .sessionStart(now)
                    .sessionEnd(now)
                    .pageViews(1)
                    .browser(browser)
                    .operatingSystem(os)
                    .deviceType(deviceType)
                    .referrer(referrer)
                    .isReturning(isReturning)
                    .createdAt(now)
                    .build();

            websiteVisitorRepository.save(visit);

        } catch (Exception e) {
            log.error("[Analytics Service] Failed to log visit: {}", e.getMessage());
        }
    }

    @Async
    @Transactional
    public void logDownload(Long documentId, String documentName, String category, String visitorId) {
        try {
            DocumentDownloadLog logEntry = DocumentDownloadLog.builder()
                    .documentId(documentId)
                    .documentName(documentName != null ? documentName : "Document #" + documentId)
                    .category(category != null ? category : "General")
                    .visitorId(visitorId)
                    .downloadTime(LocalDateTime.now())
                    .build();

            documentDownloadLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("[Analytics Service] Failed to log download: {}", e.getMessage());
        }
    }

    @Async
    @Transactional
    public void logSearch(String searchKeyword, int resultsCount, String visitorId) {
        if (searchKeyword == null || searchKeyword.isBlank()) return;
        try {
            SearchLog searchEntry = SearchLog.builder()
                    .visitorId(visitorId)
                    .searchKeyword(searchKeyword.trim())
                    .resultsCount(resultsCount)
                    .searchTime(LocalDateTime.now())
                    .build();

            searchLogRepository.save(searchEntry);
        } catch (Exception e) {
            log.error("[Analytics Service] Failed to log search: {}", e.getMessage());
        }
    }

    /**
     * Centralized Date Range Resolution
     * Resolves filter string and custom dates into a single DateRange object.
     */
    public DateRange resolveDateRange(String filter, LocalDate startDate, LocalDate endDate) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(29);
        String label = "Last 30 Days";

        if ("today".equalsIgnoreCase(filter)) {
            start = end;
            label = "Today";
        } else if ("yesterday".equalsIgnoreCase(filter)) {
            start = end.minusDays(1);
            end = start;
            label = "Yesterday";
        } else if ("7days".equalsIgnoreCase(filter) || "last_7_days".equalsIgnoreCase(filter)) {
            start = end.minusDays(6);
            label = "Last 7 Days";
        } else if ("30days".equalsIgnoreCase(filter) || "last_30_days".equalsIgnoreCase(filter)) {
            start = end.minusDays(29);
            label = "Last 30 Days";
        } else if ("current_month".equalsIgnoreCase(filter)) {
            start = end.withDayOfMonth(1);
            label = "Current Month";
        } else if ("last_month".equalsIgnoreCase(filter)) {
            LocalDate firstOfLastMonth = end.minusMonths(1).withDayOfMonth(1);
            start = firstOfLastMonth;
            end = firstOfLastMonth.plusMonths(1).minusDays(1);
            label = "Last Month";
        } else if ("custom".equalsIgnoreCase(filter) && startDate != null && endDate != null) {
            start = startDate;
            end = endDate;
            label = startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + " – " + endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        }

        return DateRange.builder()
                .start(start.atStartOfDay())
                .end(end.atTime(LocalTime.MAX))
                .periodLabel(label)
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDTO getSummary(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        LocalDateTime start = dateRange.getStart();
        LocalDateTime end = dateRange.getEnd();

        // Filtered metrics
        long visitors = websiteVisitorRepository.countUniqueVisitorsBetween(start, end);
        long sessions = websiteVisitorRepository.countSessionsBetween(start, end);
        long pageViews = websiteVisitorRepository.sumPageViewsBetween(start, end);
        long downloads = documentDownloadLogRepository.countDownloadsBetween(start, end);

        // Reference metrics
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime allTimeStart = LocalDateTime.of(2000, 1, 1, 0, 0);

        long weeklyVisitors = websiteVisitorRepository.countUniqueVisitorsBetween(weekStart, todayEnd);
        long monthlyVisitors = websiteVisitorRepository.countUniqueVisitorsBetween(monthStart, todayEnd);
        long overallVisitors = websiteVisitorRepository.countOverallUniqueVisitors();
        long uniqueVisitors = websiteVisitorRepository.countOverallUniqueVisitors();
        long returningVisitors = websiteVisitorRepository.countReturningVisitors();
        long totalDownloads = documentDownloadLogRepository.countDownloadsBetween(allTimeStart, todayEnd);

        return AnalyticsSummaryDTO.builder()
                .visitors(visitors)
                .sessions(sessions)
                .pageViews(pageViews)
                .downloads(downloads)
                .weeklyVisitors(weeklyVisitors)
                .monthlyVisitors(monthlyVisitors)
                .overallVisitors(overallVisitors)
                .uniqueVisitors(uniqueVisitors)
                .returningVisitors(returningVisitors)
                .totalDownloads(totalDownloads)
                .periodLabel(dateRange.getPeriodLabel())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DailyVisitorDTO> getDailyTrend(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        LocalDateTime start = dateRange.getStart();
        LocalDateTime end = dateRange.getEnd();

        Map<String, DailyVisitorDTO> map = new LinkedHashMap<>();
        LocalDate cur = start.toLocalDate();
        LocalDate last = end.toLocalDate();

        while (!cur.isAfter(last)) {
            String dateStr = cur.format(DateTimeFormatter.ISO_LOCAL_DATE);
            map.put(dateStr, new DailyVisitorDTO(dateStr, 0, 0, 0));
            cur = cur.plusDays(1);
        }

        List<WebsiteVisitor> visits = websiteVisitorRepository.findRecentActivityBetween(start, end, PageRequest.of(0, 10000));
        for (WebsiteVisitor v : visits) {
            String dateStr = v.getVisitTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            DailyVisitorDTO dto = map.get(dateStr);
            if (dto != null) {
                dto.setTotalVisits(dto.getTotalVisits() + 1);
                dto.setPageViews(dto.getPageViews() + (v.getPageViews() != null ? v.getPageViews() : 1));
            }
        }

        for (String dStr : map.keySet()) {
            LocalDate d = LocalDate.parse(dStr);
            long u = websiteVisitorRepository.countUniqueVisitorsBetween(d.atStartOfDay(), d.atTime(LocalTime.MAX));
            DailyVisitorDTO dto = map.get(dStr);
            if (dto != null) {
                dto.setUniqueVisitors(u);
            }
        }

        return new ArrayList<>(map.values());
    }

    @Transactional(readOnly = true)
    public List<PageStatDTO> getTopPages(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        List<Object[]> rows = websiteVisitorRepository.findTopPagesBetween(dateRange.getStart(), dateRange.getEnd(), PageRequest.of(0, 10));

        long totalVisits = websiteVisitorRepository.countVisitsBetween(dateRange.getStart(), dateRange.getEnd());
        if (totalVisits == 0) totalVisits = 1;

        List<PageStatDTO> result = new ArrayList<>();
        for (Object[] r : rows) {
            String pageUrl = (String) r[0];
            long count = (Long) r[1];
            LocalDateTime lastVisit = (LocalDateTime) r[2];

            double percentage = Math.round((count * 100.0 / totalVisits) * 10.0) / 10.0;
            String pageName = derivePageName(pageUrl);

            result.add(PageStatDTO.builder()
                    .pageName(pageName)
                    .pageUrl(pageUrl)
                    .totalVisits(count)
                    .percentage(percentage)
                    .lastVisit(lastVisit)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<DocumentDownloadStatDTO> getTopDownloads(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        List<Object[]> rows = documentDownloadLogRepository.findTopDownloadedDocumentsBetween(dateRange.getStart(), dateRange.getEnd(), PageRequest.of(0, 10));

        List<DocumentDownloadStatDTO> result = new ArrayList<>();
        for (Object[] r : rows) {
            Long docId = (Long) r[0];
            String docName = (String) r[1];
            String category = (String) r[2];
            long count = (Long) r[3];
            LocalDateTime latestDownload = (LocalDateTime) r[4];

            result.add(DocumentDownloadStatDTO.builder()
                    .documentId(docId)
                    .documentName(docName)
                    .category(category)
                    .downloads(count)
                    .latestDownload(latestDownload)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getDownloadsByCategory(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        List<Object[]> rows = documentDownloadLogRepository.findDownloadsByCategoryBetween(dateRange.getStart(), dateRange.getEnd());

        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String cat = (String) r[0];
            long count = (Long) r[1];
            result.put(cat != null ? cat : "General", count);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SearchStatDTO> getTopSearches(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        List<Object[]> rows = searchLogRepository.findTopKeywordsBetween(dateRange.getStart(), dateRange.getEnd(), PageRequest.of(0, 10));

        List<SearchStatDTO> result = new ArrayList<>();
        for (Object[] r : rows) {
            String keyword = (String) r[0];
            long count = (Long) r[1];
            double avgResults = r[2] != null ? Math.round(((Number) r[2]).doubleValue() * 10.0) / 10.0 : 0.0;
            LocalDateTime latestSearch = (LocalDateTime) r[3];

            result.add(SearchStatDTO.builder()
                    .keyword(keyword)
                    .searchCount(count)
                    .avgResults(avgResults)
                    .latestSearch(latestSearch)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<RecentActivityDTO> getRecentActivity(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        List<WebsiteVisitor> list = websiteVisitorRepository.findRecentActivityBetween(dateRange.getStart(), dateRange.getEnd(), PageRequest.of(0, 20));

        List<RecentActivityDTO> result = new ArrayList<>();
        for (WebsiteVisitor v : list) {
            result.add(RecentActivityDTO.builder()
                    .time(v.getVisitTime())
                    .pageUrl(v.getPageUrl())
                    .pageName(derivePageName(v.getPageUrl()))
                    .browser(v.getBrowser())
                    .deviceType(v.getDeviceType())
                    .visitorType(Boolean.TRUE.equals(v.getIsReturning()) ? "Returning" : "New")
                    .sessionId(v.getSessionId() != null && v.getSessionId().length() > 8 ? v.getSessionId().substring(0, 8) + "..." : v.getSessionId())
                    .visitorId(v.getVisitorId() != null && v.getVisitorId().length() > 8 ? v.getVisitorId().substring(0, 8) + "..." : v.getVisitorId())
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getBrowserDistribution(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        List<Object[]> rows = websiteVisitorRepository.findBrowserDistributionBetween(dateRange.getStart(), dateRange.getEnd());
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String b = (String) r[0];
            long count = (Long) r[1];
            map.put(b != null ? b : "Unknown", count);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getDeviceDistribution(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        List<Object[]> rows = websiteVisitorRepository.findDeviceDistributionBetween(dateRange.getStart(), dateRange.getEnd());
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String d = (String) r[0];
            long count = (Long) r[1];
            map.put(d != null ? d : "Desktop", count);
        }
        return map;
    }

    public byte[] generateCsvExport(String filter, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(filter, startDate, endDate);
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // UTF-8 BOM

        csv.append("WEBSITE ANALYTICS REPORT\n");
        csv.append("Generated Date,").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        csv.append("Generated By,Administrator\n");
        csv.append("Analytics Period,").append(dateRange.getPeriodLabel()).append("\n\n");

        AnalyticsSummaryDTO summary = getSummary(filter, startDate, endDate);
        csv.append("FILTERED METRICS (").append(dateRange.getPeriodLabel()).append(")\n");
        csv.append("Metric,Value\n");
        csv.append("Visitors,").append(summary.getVisitors()).append("\n");
        csv.append("Sessions,").append(summary.getSessions()).append("\n");
        csv.append("Page Views,").append(summary.getPageViews()).append("\n");
        csv.append("Downloads,").append(summary.getDownloads()).append("\n\n");

        csv.append("REFERENCE STATISTICS\n");
        csv.append("Metric,Value\n");
        csv.append("This Week,").append(summary.getWeeklyVisitors()).append("\n");
        csv.append("This Month,").append(summary.getMonthlyVisitors()).append("\n");
        csv.append("Overall Visitors,").append(summary.getOverallVisitors()).append("\n");
        csv.append("Unique Visitors,").append(summary.getUniqueVisitors()).append("\n");
        csv.append("Returning Visitors,").append(summary.getReturningVisitors()).append("\n");
        csv.append("Total Downloads,").append(summary.getTotalDownloads()).append("\n\n");

        csv.append("TOP VISITED PAGES\n");
        csv.append("Page Name,Page URL,Total Visits,Percentage,Last Visit\n");
        for (PageStatDTO p : getTopPages(filter, startDate, endDate)) {
            csv.append(escapeCsv(p.getPageName())).append(",")
               .append(escapeCsv(p.getPageUrl())).append(",")
               .append(p.getTotalVisits()).append(",")
               .append(p.getPercentage()).append("%,")
               .append(p.getLastVisit() != null ? p.getLastVisit().toString() : "").append("\n");
        }
        csv.append("\n");

        csv.append("MOST DOWNLOADED DOCUMENTS\n");
        csv.append("Document Name,Category,Downloads,Latest Download\n");
        for (DocumentDownloadStatDTO d : getTopDownloads(filter, startDate, endDate)) {
            csv.append(escapeCsv(d.getDocumentName())).append(",")
               .append(escapeCsv(d.getCategory())).append(",")
               .append(d.getDownloads()).append(",")
               .append(d.getLatestDownload() != null ? d.getLatestDownload().toString() : "").append("\n");
        }
        csv.append("\n");

        csv.append("DOWNLOADS BY CATEGORY\n");
        csv.append("Category,Downloads\n");
        Map<String, Long> catDownloads = getDownloadsByCategory(filter, startDate, endDate);
        for (Map.Entry<String, Long> entry : catDownloads.entrySet()) {
            csv.append(escapeCsv(entry.getKey())).append(",").append(entry.getValue()).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String derivePageName(String uri) {
        if (uri == null || uri.equals("/") || uri.equals("/index.html")) return "Home Page";
        if (uri.contains("master-list")) return "Master List";
        if (uri.contains("generic-template")) return "Generic Templates";
        if (uri.contains("lessons-learned")) return "Lessons Learned";
        if (uri.contains("quality-checks")) return "Quality Checklists";
        if (uri.contains("section-details")) return "Process Details";
        if (uri.contains("search")) return "Global Search";
        return uri;
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
