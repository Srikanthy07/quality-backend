package com.qualitywebsite.controller;

import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.MasterListItem;
import com.qualitywebsite.service.DocumentService;
import com.qualitywebsite.service.MasterListService;
import com.qualitywebsite.service.WebsiteAnalyticsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PublicDataController {

    private final DocumentService documentService;
    private final MasterListService masterListService;
    private final WebsiteAnalyticsService websiteAnalyticsService;

    @GetMapping(value = {"/data/documents.json", "/api/public/documents"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DocumentEntity>> getDocumentsJson() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @GetMapping(value = {"/data/generic-templates.json", "/api/public/generic-templates"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DocumentEntity>> getGenericTemplatesJson() {
        return ResponseEntity.ok(documentService.getByCategory("Generic Templates"));
    }

    @GetMapping(value = {"/data/lessons-learned.json", "/api/public/lessons-learned"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DocumentEntity>> getLessonsLearnedJson() {
        return ResponseEntity.ok(documentService.getByCategory("Lessons Learned"));
    }

    @GetMapping(value = {"/data/prm-documents.csv", "/api/public/master-list"}, produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> getMasterListCsv() {
        List<MasterListItem> items = masterListService.getAllItems();
        StringBuilder csv = new StringBuilder("S.No,Process ID,Process Name,Template Name,Version\n");
        int idx = 1;
        for (MasterListItem item : items) {
            csv.append(idx++).append(",")
               .append(escapeCsv(item.getProcessId())).append(",")
               .append(escapeCsv(item.getProcessName())).append(",")
               .append(escapeCsv(item.getTemplateName())).append(",")
               .append(escapeCsv(item.getVersion())).append("\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv.toString());
    }

    @GetMapping(value = {"/api/public/master-list/json"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<MasterListItem>> getMasterListJson() {
        return ResponseEntity.ok(masterListService.getAllItems());
    }

    // Global Search: searches active documents and master list
    @GetMapping(value = {"/api/public/search"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<com.qualitywebsite.dto.MasterListSearchResultDTO>> globalSearch(
            @RequestParam(required = false) String query) {

        List<com.qualitywebsite.dto.MasterListSearchResultDTO> results = masterListService.searchMasterList(query);
        return ResponseEntity.ok(results);
    }

    private String getVisitorId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "vid".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}