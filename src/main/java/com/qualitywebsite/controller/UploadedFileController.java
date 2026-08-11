package com.qualitywebsite.controller;

import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import com.qualitywebsite.service.DmsDocumentService;
import com.qualitywebsite.service.WebsiteAnalyticsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UploadedFileController {

    private final DmsDocumentService dmsDocumentService;
    private final WebsiteAnalyticsService websiteAnalyticsService;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;

    @Value("${app.upload.dir:./uploaded-documents}")
    private String uploadDir;

    @GetMapping("/api/public/dms/download/{versionId}")
    public ResponseEntity<byte[]> downloadDmsVersion(@PathVariable Long versionId, HttpServletRequest request) {
        trackDownloadByVersionId(versionId, request);
        return dmsDocumentService.streamPublicVersion(versionId);
    }

    @GetMapping("/api/public/dms/document/{masterId}/download")
    public ResponseEntity<byte[]> downloadDmsLatest(@PathVariable Long masterId, HttpServletRequest request) {
        Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(masterId);
        latestOpt.ifPresent(v -> trackDownload(v.getDocumentMaster().getId(), v.getDocumentMaster().getDocumentName(), v.getDocumentMaster().getCategory(), request));
        return dmsDocumentService.streamPublicLatest(masterId);
    }

    @GetMapping("/uploaded-documents/{fileName:.+}")
    public ResponseEntity<?> serveFile(@PathVariable String fileName, HttpServletRequest request) {
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\") || fileName.contains("\0")) {
            log.warn("[Security Alert] Path traversal attempt blocked for uploaded document: {}", fileName);
            return ResponseEntity.notFound().build();
        }

        // Authoritative DMS Lookup Rule: All document downloads MUST have a matching approved DocumentVersion in MySQL
        Optional<DocumentVersion> versionOpt = documentVersionRepository.findFirstByFileNameIgnoreCase(fileName);
        if (versionOpt.isEmpty()) {
            log.warn("[DMS Security] Denied access to unregistered document file: {}", fileName);
            return ResponseEntity.notFound().build();
        }

        DocumentVersion v = versionOpt.get();
        if (!"APPROVED".equalsIgnoreCase(v.getApprovalStatus()) || !"APPROVED".equalsIgnoreCase(v.getDocumentMaster().getStatus())) {
            log.warn("[DMS Security] Denied access to unapproved document file: {} (masterStatus={}, versionStatus={})",
                    fileName, v.getDocumentMaster().getStatus(), v.getApprovalStatus());
            return ResponseEntity.notFound().build();
        }

        if (v.getFileData() != null && v.getFileData().length > 0) {
            trackDownload(v.getDocumentMaster().getId(), v.getDocumentMaster().getDocumentName(), v.getDocumentMaster().getCategory(), request);
            return dmsDocumentService.streamPublicVersion(v.getId());
        }

        log.warn("[DMS Security] Document version {} is approved but file_data is empty for file: {}", v.getId(), fileName);
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/documents/**")
    public ResponseEntity<?> serveStaticDocument(HttpServletRequest request) {
        String pathWithinHandler = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (pathWithinHandler == null) {
            pathWithinHandler = request.getRequestURI();
        }

        String relativePath = pathWithinHandler.startsWith("/") ? pathWithinHandler.substring(1) : pathWithinHandler;
        if (relativePath.contains("..") || relativePath.contains("\0")) {
            log.warn("[Security Alert] Path traversal attempt blocked for static document: {}", relativePath);
            return ResponseEntity.notFound().build();
        }

        String fileName = Paths.get(relativePath).getFileName().toString();

        // Authoritative DMS Lookup Rule: All static document downloads MUST have a matching approved DocumentVersion in MySQL
        Optional<DocumentVersion> versionOpt = documentVersionRepository.findFirstByFileNameIgnoreCase(fileName);
        if (versionOpt.isEmpty()) {
            log.warn("[DMS Security] Denied direct static access to unregistered document: {}", relativePath);
            return ResponseEntity.notFound().build();
        }

        DocumentVersion v = versionOpt.get();
        if (!"APPROVED".equalsIgnoreCase(v.getApprovalStatus()) || !"APPROVED".equalsIgnoreCase(v.getDocumentMaster().getStatus())) {
            log.warn("[DMS Security] Denied direct static access to unapproved document: {} (masterStatus={}, versionStatus={})",
                    fileName, v.getDocumentMaster().getStatus(), v.getApprovalStatus());
            return ResponseEntity.notFound().build();
        }

        if (v.getFileData() != null && v.getFileData().length > 0) {
            trackDownload(v.getDocumentMaster().getId(), v.getDocumentMaster().getDocumentName(), v.getDocumentMaster().getCategory(), request);
            return dmsDocumentService.streamPublicVersion(v.getId());
        }

        log.warn("[DMS Security] Document version {} is approved but file_data is empty for static file: {}", v.getId(), fileName);
        return ResponseEntity.notFound().build();
    }

    private String sanitizeFilenameHeader(String filename) {
        if (filename == null) return "document";
        return filename.replaceAll("[\\r\\n\"\\\\/]", "_");
    }

    private void trackDownloadByVersionId(Long versionId, HttpServletRequest request) {
        try {
            Optional<DocumentVersion> versionOpt = documentVersionRepository.findById(versionId);
            if (versionOpt.isPresent()) {
                DocumentVersion v = versionOpt.get();
                trackDownload(v.getDocumentMaster().getId(), v.getDocumentMaster().getDocumentName(), v.getDocumentMaster().getCategory(), request);
            }
        } catch (Exception ignored) {}
    }

    private void trackDownload(Long docId, String docName, String category, HttpServletRequest request) {
        String visitorId = getVisitorId(request);
        websiteAnalyticsService.logDownload(docId, docName, category, visitorId);
    }

    private String getVisitorId(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "vid".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String deriveCategoryFromPath(String path) {
        if (path.contains("Generic Templates")) return "Generic Templates";
        if (path.contains("Lessons Learned")) return "Lessons Learned";
        if (path.contains("ASPICE PRM")) return "ASPICE PRM";
        if (path.contains("Assessment Checklist")) return "Assessment Checklist";
        return "General";
    }

    private String deriveDocumentNameFromFilename(String filename) {
        if (filename == null) return "Document";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private String determineMimeType(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".doc")) return "application/msword";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".xls")) return "application/vnd.ms-excel";
        if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (name.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "application/octet-stream";
    }
}
