package com.qualitywebsite.controller;

import com.qualitywebsite.dto.DocumentMasterDTO;
import com.qualitywebsite.dto.UploadResponseDTO;
import com.qualitywebsite.dto.VersionHistoryDTO;
import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.MasterListItem;
import com.qualitywebsite.exception.DocumentConflictException;
import com.qualitywebsite.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminApiController {

    private final DmsDocumentService dmsDocumentService;
    private final DocumentService legacyDocumentService;
    private final MasterListService masterListService;
    private final SettingsService settingsService;
    private final ActivityLogService activityLogService;
    private final AdminAuthService adminAuthService;
    private final WebsiteAnalyticsService websiteAnalyticsService;

    // --- Dashboard ---
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        response.put("stats", dmsDocumentService.getDashboardStats());
        response.put("activities", activityLogService.getRecentActivities());
        return ResponseEntity.ok(response);
    }

    // --- DMS Endpoints ---

    @GetMapping("/dms/documents")
    public ResponseEntity<?> getDmsDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int pageNum = (page != null && page >= 0) ? page : 0;
            int pageSize = (size != null && size > 0) ? Math.min(size, 100) : 20;
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                    pageNum, pageSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedDate"));
            org.springframework.data.domain.Page<DocumentMasterDTO> pagedResult = dmsDocumentService.searchAndFilterPaged(query, category, pageable);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content", pagedResult.getContent());
            response.put("page", pagedResult.getNumber());
            response.put("size", pagedResult.getSize());
            response.put("totalElements", pagedResult.getTotalElements());
            response.put("totalPages", pagedResult.getTotalPages());
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(dmsDocumentService.searchAndFilter(query, category));
    }

    @GetMapping("/dms/documents/{id}")
    public ResponseEntity<DocumentMasterDTO> getDmsDocumentById(@PathVariable Long id) {
        return dmsDocumentService.getDocumentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/dms/upload")
    public ResponseEntity<?> uploadDmsDocument(
            @RequestParam(value = "category", required = false, defaultValue = "ASPICE PRM") String category,
            @RequestParam(value = "processGroup", required = false, defaultValue = "General") String processGroup,
            @RequestParam(value = "processId", required = false, defaultValue = "GLOBAL") String processId,
            @RequestParam(value = "processName", required = false) String processName,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(value = "remarks", required = false) String remarks,
            @RequestParam(value = "confirmNewVersion", required = false, defaultValue = "false") boolean confirmNewVersion,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        try {
            UploadResponseDTO response = dmsDocumentService.uploadDocument(
                    file, category, processGroup, processId, processName, documentName, remarks, getUsername(auth), confirmNewVersion
            );

            if ("REJECTED".equalsIgnoreCase(response.getAction())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            if ("DUPLICATE_PROMPT".equalsIgnoreCase(response.getAction())) {
                return ResponseEntity.status(HttpStatus.MULTIPLE_CHOICES).body(response);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/dms/documents/{id}/version")
    public ResponseEntity<?> uploadDmsNewVersion(
            @PathVariable Long id,
            @RequestParam(value = "remarks", required = false) String remarks,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        try {
            byte[] bytes = file.getBytes();
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".") + 1).toUpperCase() : "DOC";
            String mimeType = resolveMimeFromExt(ext, file.getContentType());

            UploadResponseDTO response = dmsDocumentService.uploadNewVersion(id, bytes, originalName, ext, mimeType, remarks, getUsername(auth));
            if (!response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (DocumentConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", 409, "error", "Conflict", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Version upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/dms/documents/{id}/versions")
    public ResponseEntity<List<VersionHistoryDTO>> getVersionHistory(@PathVariable Long id) {
        return ResponseEntity.ok(dmsDocumentService.getVersionHistory(id));
    }

    @PostMapping("/dms/documents/{id}/approve")
    public ResponseEntity<?> approveDocument(@PathVariable Long id, Authentication auth) {
        try {
            boolean ok = dmsDocumentService.approveDocument(id, getUsername(auth));
            if (ok) return ResponseEntity.ok(Map.of("message", "Document approved successfully"));
            return ResponseEntity.notFound().build();
        } catch (DocumentConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", 409, "error", "Conflict", "message", e.getMessage()));
        }
    }

    @PostMapping("/dms/documents/{id}/reject")
    public ResponseEntity<?> rejectDocument(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        try {
            String remarks = (body != null) ? body.getOrDefault("remarks", "") : "";
            boolean ok = dmsDocumentService.rejectDocument(id, remarks, getUsername(auth));
            if (ok) return ResponseEntity.ok(Map.of("message", "Document rejected successfully"));
            return ResponseEntity.notFound().build();
        } catch (DocumentConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", 409, "error", "Conflict", "message", e.getMessage()));
        }
    }

    @PostMapping("/dms/documents/{id}/archive")
    public ResponseEntity<?> archiveDocument(@PathVariable Long id, Authentication auth) {
        try {
            boolean ok = dmsDocumentService.archiveDocument(id, getUsername(auth));
            if (ok) return ResponseEntity.ok(Map.of("message", "Document archived successfully"));
            return ResponseEntity.notFound().build();
        } catch (DocumentConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", 409, "error", "Conflict", "message", e.getMessage()));
        }
    }

    @PutMapping("/dms/documents/{id}")
    public ResponseEntity<?> updateDmsMetadata(
            @PathVariable Long id,
            @RequestBody DocumentMasterDTO updateData,
            Authentication auth) {
        try {
            DocumentMasterDTO updated = dmsDocumentService.updateMetadata(id, updateData, getUsername(auth));
            return ResponseEntity.ok(updated);
        } catch (DocumentConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", 409, "error", "Conflict",
                                 "message", e.getMessage(),
                                 "documentMasterId", e.getDocumentMasterId() != null ? e.getDocumentMasterId() : id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/dms/documents/{id}")
    public ResponseEntity<?> deleteDmsDocument(@PathVariable Long id, Authentication auth) {
        boolean ok = dmsDocumentService.archiveDocument(id, getUsername(auth));
        if (ok) {
            return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
        }
        return ResponseEntity.notFound().build();
    }

    // --- Backward Compatible Document Endpoints ---
    @GetMapping("/documents")
    public ResponseEntity<List<DocumentMasterDTO>> getDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(dmsDocumentService.searchAndFilter(query, category));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable String id) {
        try {
            Long masterId = Long.parseLong(id);
            return dmsDocumentService.getDocumentById(masterId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (NumberFormatException e) {
            return legacyDocumentService.getById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
    }

    @PostMapping("/documents")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("category") String category,
            @RequestParam("processGroup") String processGroup,
            @RequestParam("process") String process,
            @RequestParam("documentName") String documentName,
            @RequestParam("version") String version,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "id", required = false) String customId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        try {
            UploadResponseDTO response = dmsDocumentService.uploadDocument(
                    file, category, processGroup, process, process, documentName, description, getUsername(auth), false
            );
            if ("REJECTED".equalsIgnoreCase(response.getAction())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", response.getMessage()));
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File upload failed: " + e.getMessage()));
        }
    }

    @PutMapping("/documents/{id}")
    public ResponseEntity<?> updateDocumentMetadata(
            @PathVariable String id,
            @RequestBody DocumentEntity updateData,
            Authentication auth) {
        try {
            Long masterId = Long.parseLong(id);
            DocumentMasterDTO dto = DocumentMasterDTO.builder()
                    .documentName(updateData.getDocumentName())
                    .category(updateData.getCategory())
                    .processId(updateData.getProcess())
                    .processGroup(updateData.getProcessGroup())
                    .build();
            DocumentMasterDTO updated = dmsDocumentService.updateMetadata(masterId, dto, getUsername(auth));
            return ResponseEntity.ok(updated);
        } catch (NumberFormatException e) {
            try {
                DocumentEntity updated = legacyDocumentService.updateMetadata(id, updateData, getUsername(auth));
                return ResponseEntity.ok(updated);
            } catch (Exception ex) {
                return ResponseEntity.notFound().build();
            }
        }
    }

    @PostMapping("/documents/{id}/replace")
    public ResponseEntity<?> replaceDocumentFile(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        try {
            Long masterId = Long.parseLong(id);
            byte[] bytes = file.getBytes();
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".") + 1).toUpperCase() : "DOC";
            String mimeType = resolveMimeFromExt(ext, file.getContentType());

            UploadResponseDTO response = dmsDocumentService.uploadNewVersion(masterId, bytes, originalName, ext, mimeType, "Replaced file", getUsername(auth));
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            try {
                DocumentEntity replaced = legacyDocumentService.replaceFile(id, file, getUsername(auth));
                return ResponseEntity.ok(replaced);
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "File replacement failed: " + ex.getMessage()));
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File replacement failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable String id, Authentication auth) {
        try {
            Long masterId = Long.parseLong(id);
            boolean ok = dmsDocumentService.archiveDocument(masterId, getUsername(auth));
            if (ok) return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
            return ResponseEntity.notFound().build();
        } catch (NumberFormatException e) {
            boolean deleted = legacyDocumentService.deleteDocument(id, getUsername(auth));
            if (deleted) return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
            return ResponseEntity.notFound().build();
        }
    }

    // --- Master List ---
    @GetMapping("/master-list")
    public ResponseEntity<List<MasterListItem>> getMasterList() {
        return ResponseEntity.ok(masterListService.getAllItems());
    }

    @PostMapping("/master-list")
    public ResponseEntity<MasterListItem> createMasterListItem(
            @RequestBody MasterListItem item, Authentication auth) {
        return ResponseEntity.ok(masterListService.saveItem(item, getUsername(auth)));
    }

    @PutMapping("/master-list/{id}")
    public ResponseEntity<MasterListItem> updateMasterListItem(
            @PathVariable Long id, @RequestBody MasterListItem item, Authentication auth) {
        item.setId(id);
        return ResponseEntity.ok(masterListService.saveItem(item, getUsername(auth)));
    }

    @DeleteMapping("/master-list/{id}")
    public ResponseEntity<?> deleteMasterListItem(@PathVariable Long id, Authentication auth) {
        boolean deleted = masterListService.deleteItem(id, getUsername(auth));
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Master list item deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    // --- Settings ---
    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getSettings() {
        return ResponseEntity.ok(settingsService.getAllSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(
            @RequestBody Map<String, String> settings, Authentication auth) {
        settingsService.updateSettings(settings, getUsername(auth));
        return ResponseEntity.ok(Map.of("message", "Settings updated successfully"));
    }

    // --- Admin Profile & Status ---
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication auth) {
        return ResponseEntity.ok(adminAuthService.getAdminProfile(getUsername(auth)));
    }

    // --- Change Password ---
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> payload, Authentication auth, jakarta.servlet.http.HttpServletRequest request) {
        String currentPassword = payload.get("currentPassword");
        String newPassword = payload.get("newPassword");
        String confirmPassword = payload.get("confirmPassword");

        if (currentPassword == null || newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password fields cannot be blank"));
        }

        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(Map.of("message", "New password and confirm password do not match"));
        }

        try {
            boolean success = adminAuthService.changePassword(getUsername(auth), currentPassword, newPassword);
            if (success) {
                // Requirement 14: Invalidate existing session after password change
                if (request != null && request.getSession(false) != null) {
                    request.getSession().invalidate();
                }
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
                return ResponseEntity.ok(Map.of(
                        "message", "Password updated successfully. Please log in again with your new password.",
                        "requireRelogin", true
                ));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Current password is incorrect"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/analytics/reset")
    public ResponseEntity<Map<String, String>> resetAnalytics(Authentication auth) {
        try {
            websiteAnalyticsService.resetAnalyticsData();
            activityLogService.logActivity(getUsername(auth), "Analytics Reset", "Cleared all website visitor, search, and download analytics data");
            return ResponseEntity.ok(Map.of("message", "Website analytics data cleared successfully."));
        } catch (Exception e) {
            log.error("Failed to reset analytics data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to clear analytics data: " + e.getMessage()));
        }
    }

    private String getUsername(Authentication auth) {
        return auth != null ? auth.getName() : "admin";
    }

    private String resolveMimeFromExt(String ext, String clientContentType) {
        if (clientContentType != null && !clientContentType.equalsIgnoreCase("application/octet-stream")) {
            return clientContentType;
        }
        return switch (ext.toUpperCase()) {
            case "PDF"  -> "application/pdf";
            case "DOC"  -> "application/msword";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "XLS"  -> "application/vnd.ms-excel";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "PPT"  -> "application/vnd.ms-powerpoint";
            case "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default     -> "application/octet-stream";
        };
    }
}
