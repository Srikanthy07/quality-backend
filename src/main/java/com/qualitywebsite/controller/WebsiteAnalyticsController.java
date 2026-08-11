package com.qualitywebsite.controller;

import com.qualitywebsite.dto.*;
import com.qualitywebsite.service.WebsiteAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class WebsiteAnalyticsController {

    private final WebsiteAnalyticsService websiteAnalyticsService;

    private String resolveFilterParam(String filter, String range) {
        if (filter != null && !filter.isBlank()) return filter;
        if (range != null && !range.isBlank()) return range;
        return "30days";
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDTO> getSummary(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getSummary(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/daily")
    public ResponseEntity<List<DailyVisitorDTO>> getDailyTrend(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getDailyTrend(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/pages")
    public ResponseEntity<List<PageStatDTO>> getTopPages(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getTopPages(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/downloads")
    public ResponseEntity<List<DocumentDownloadStatDTO>> getTopDownloads(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getTopDownloads(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/downloads/category")
    public ResponseEntity<Map<String, Long>> getDownloadsByCategory(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getDownloadsByCategory(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/searches")
    public ResponseEntity<List<SearchStatDTO>> getTopSearches(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getTopSearches(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<RecentActivityDTO>> getRecentActivity(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getRecentActivity(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/browser")
    public ResponseEntity<Map<String, Long>> getBrowserDistribution(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getBrowserDistribution(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/device")
    public ResponseEntity<Map<String, Long>> getDeviceDistribution(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(websiteAnalyticsService.getDeviceDistribution(resolveFilterParam(filter, range), startDate, endDate));
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        byte[] csvData = websiteAnalyticsService.generateCsvExport(resolveFilterParam(filter, range), startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"website_analytics_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        byte[] csvData = websiteAnalyticsService.generateCsvExport(resolveFilterParam(filter, range), startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"website_analytics_report.csv\"")
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel; charset=UTF-8"))
                .body(csvData);
    }
}
