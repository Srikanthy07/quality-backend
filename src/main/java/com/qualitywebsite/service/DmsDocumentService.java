package com.qualitywebsite.service;

import com.qualitywebsite.dto.DocumentMasterDTO;
import com.qualitywebsite.dto.UploadResponseDTO;
import com.qualitywebsite.dto.VersionHistoryDTO;
import com.qualitywebsite.entity.DmsMigrationLog;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.exception.DocumentConflictException;
import com.qualitywebsite.repository.DmsMigrationLogRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DmsDocumentService {

    private final DocumentMasterRepository documentMasterRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DmsMigrationLogRepository dmsMigrationLogRepository;
    private final DocumentRepository documentRepository;
    private final ActivityLogService activityLogService;
    private final Tika tika = new Tika();

    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            "PDF", "application/pdf",
            "DOC", "application/msword",
            "DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "XLS", "application/vnd.ms-excel",
            "XLSX", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "PPT", "application/vnd.ms-powerpoint",
            "PPTX", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(EXTENSION_TO_MIME.values());

    @Transactional
    public UploadResponseDTO uploadDocument(
            MultipartFile file,
            String category,
            String processGroup,
            String processId,
            String processName,
            String documentName,
            String remarks,
            String username,
            boolean confirmNewVersion) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty.");
        }

        String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExt = getFileExtension(originalName).toUpperCase(Locale.ROOT);
        String resolvedMimeType = resolveAndValidateMimeType(file, fileExt);

        byte[] fileBytes = file.getBytes();
        String checksum = calculateChecksum(fileBytes);

        String cleanProcId = (processId != null && !processId.trim().isEmpty()) ? processId.trim() : "GLOBAL";
        String cleanCat = (category != null && !category.trim().isEmpty()) ? category.trim() : "ASPICE PRM";
        String cleanDocName = (documentName != null && !documentName.trim().isEmpty()) ? documentName.trim() : removeExtension(originalName);

        // Duplicate detection: check by processId + category + documentName
        Optional<DocumentMaster> existingOpt = documentMasterRepository
                .findByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase(cleanProcId, cleanCat, cleanDocName);

        if (existingOpt.isPresent()) {
            DocumentMaster existing = existingOpt.get();
            Optional<DocumentVersion> latestVersionOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(existing.getId());

            if (latestVersionOpt.isPresent() && latestVersionOpt.get().getChecksum().equalsIgnoreCase(checksum)) {
                return UploadResponseDTO.builder()
                        .success(false)
                        .message("Duplicate document already exists with identical file content.")
                        .documentMasterId(existing.getId())
                        .documentCode(existing.getDocumentCode())
                        .version(existing.getCurrentVersion())
                        .action("REJECTED")
                        .isDuplicateChecksum(true)
                        .existingDocument(toDTO(existing))
                        .build();
            }

            if (!confirmNewVersion) {
                return UploadResponseDTO.builder()
                        .success(false)
                        .message("Existing document found. Upload as a new version?")
                        .documentMasterId(existing.getId())
                        .documentCode(existing.getDocumentCode())
                        .version(existing.getCurrentVersion())
                        .action("DUPLICATE_PROMPT")
                        .isDuplicateChecksum(false)
                        .existingDocument(toDTO(existing))
                        .build();
            }

            return uploadNewVersion(existing.getId(), fileBytes, originalName, fileExt, resolvedMimeType, remarks, username);
        }

        // New Document Creation - Initial status = UNDER_REVIEW (Task 2)
        String code = generateDocumentCode(cleanProcId, cleanCat, cleanDocName);

        DocumentMaster master = DocumentMaster.builder()
                .documentCode(code)
                .processId(cleanProcId)
                .processName(processName != null && !processName.trim().isEmpty() ? processName.trim() : cleanProcId)
                .processGroup(processGroup != null && !processGroup.trim().isEmpty() ? processGroup.trim() : "General")
                .category(cleanCat)
                .documentName(cleanDocName)
                .currentVersion("1.0")
                .status("UNDER_REVIEW")
                .createdBy(username != null ? username : "admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();

        master = documentMasterRepository.save(master);

        // Store major_version=1, minor_version=0 (Task 1: Removed redundant persistent "version" column)
        DocumentVersion version = DocumentVersion.builder()
                .documentMaster(master)
                .majorVersion(1)
                .minorVersion(0)
                .fileName(originalName)
                .fileType(fileExt)
                .mimeType(resolvedMimeType)
                .fileSize((long) fileBytes.length)
                .fileData(fileBytes)
                .checksum(checksum)
                .uploadedBy(username != null ? username : "admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("UNDER_REVIEW")
                .remarks(remarks != null ? remarks : "Initial document upload - pending review")
                .isLatest(true)
                .build();

        version = documentVersionRepository.save(version);

        logActivity(master.getId(), version.getVersion(), "UPLOAD", username, "Uploaded document " + master.getDocumentName() + " (v1.0) - Pending Review");

        return UploadResponseDTO.builder()
                .success(true)
                .message("Document uploaded successfully and is now UNDER_REVIEW.")
                .documentMasterId(master.getId())
                .documentCode(master.getDocumentCode())
                .version("1.0")
                .action("CREATED")
                .isDuplicateChecksum(false)
                .existingDocument(toDTO(master))
                .build();
    }

    @Transactional
    public UploadResponseDTO uploadNewVersion(
            Long masterId,
            byte[] fileBytes,
            String originalName,
            String fileExt,
            String mimeType,
            String remarks,
            String username) {

        try {
            validateFileContent(fileBytes, originalName, fileExt);
            DocumentMaster master = documentMasterRepository.findById(masterId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + masterId));

            String checksum = calculateChecksum(fileBytes);

            // Check if identical checksum already exists in version history
            Optional<DocumentVersion> sameChecksumOpt = documentVersionRepository.findByMasterIdAndChecksum(masterId, checksum);
            if (sameChecksumOpt.isPresent()) {
                return UploadResponseDTO.builder()
                        .success(false)
                        .message("Duplicate file already exists in version history (v" + sameChecksumOpt.get().getVersion() + ").")
                        .documentMasterId(master.getId())
                        .documentCode(master.getDocumentCode())
                        .version(master.getCurrentVersion())
                        .action("REJECTED")
                        .isDuplicateChecksum(true)
                        .existingDocument(toDTO(master))
                        .build();
            }

            // Set previous versions isLatest = false
            List<DocumentVersion> allVersions = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId);
            int latestMajor = 1;
            int latestMinor = 0;
            if (!allVersions.isEmpty()) {
                DocumentVersion top = allVersions.get(0);
                latestMajor = top.getMajorVersion() != null ? top.getMajorVersion() : 1;
                latestMinor = top.getMinorVersion() != null ? top.getMinorVersion() : 0;
            }

            for (DocumentVersion v : allVersions) {
                v.setIsLatest(false);
            }
            documentVersionRepository.saveAll(allVersions);

            // Increment minor version (e.g. 1.0 -> 1.1)
            int newMinor = latestMinor + 1;
            String newVersionStr = latestMajor + "." + newMinor;

            DocumentVersion newVersion = DocumentVersion.builder()
                    .documentMaster(master)
                    .majorVersion(latestMajor)
                    .minorVersion(newMinor)
                    .fileName(originalName)
                    .fileType(fileExt)
                    .mimeType(mimeType)
                    .fileSize((long) fileBytes.length)
                    .fileData(fileBytes)
                    .checksum(checksum)
                    .uploadedBy(username != null ? username : "admin")
                    .uploadedDate(LocalDateTime.now())
                    .approvalStatus("UNDER_REVIEW")
                    .remarks(remarks != null && !remarks.trim().isEmpty() ? remarks.trim() : "Uploaded new version v" + newVersionStr)
                    .isLatest(true)
                    .build();

            newVersion = documentVersionRepository.save(newVersion);

            master.setCurrentVersion(newVersionStr);
            master.setStatus("UNDER_REVIEW");
            master.setUpdatedDate(LocalDateTime.now());
            master = documentMasterRepository.save(master);

            // Only log on success — failed lock attempts must not create audit records
            logActivity(master.getId(), newVersionStr, "UPDATE", username,
                    "Uploaded new version v" + newVersionStr + " for " + master.getDocumentName() + " (UNDER_REVIEW)");

            return UploadResponseDTO.builder()
                    .success(true)
                    .message("New version v" + newVersionStr + " uploaded successfully and submitted for review.")
                    .documentMasterId(master.getId())
                    .documentCode(master.getDocumentCode())
                    .version(newVersionStr)
                    .action("VERSIONED")
                    .isDuplicateChecksum(false)
                    .existingDocument(toDTO(master))
                    .build();

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DocumentConflictException(
                    "This document version is being modified by another administrator. " +
                    "Please refresh and try again.", masterId);
        }
    }


    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> streamPublicVersion(Long versionId) {
        DocumentVersion version = documentVersionRepository.findById(versionId).orElse(null);
        if (version == null) {
            log.warn("[Public DMS Download] Version ID {} not found", versionId);
            return ResponseEntity.notFound().build();
        }

        DocumentMaster master = version.getDocumentMaster();
        if (master == null
                || !"APPROVED".equalsIgnoreCase(master.getStatus())
                || !"APPROVED".equalsIgnoreCase(version.getApprovalStatus())) {
            log.warn("[Public DMS Download] Denied access to unapproved document/version (masterId={}, versionId={})",
                    master != null ? master.getId() : null, versionId);
            return ResponseEntity.notFound().build();
        }

        return buildStreamResponse(version);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> streamPublicLatest(Long masterId) {
        DocumentMaster master = documentMasterRepository.findById(masterId).orElse(null);
        if (master == null || !"APPROVED".equalsIgnoreCase(master.getStatus())) {
            log.warn("[Public DMS Download] Master document ID {} not found or not approved", masterId);
            return ResponseEntity.notFound().build();
        }

        DocumentVersion latest = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(masterId).orElse(null);
        if (latest == null || !"APPROVED".equalsIgnoreCase(latest.getApprovalStatus())) {
            log.warn("[Public DMS Download] Latest version for master ID {} is not approved or active", masterId);
            return ResponseEntity.notFound().build();
        }

        return buildStreamResponse(latest);
    }

    private ResponseEntity<byte[]> buildStreamResponse(DocumentVersion version) {
        byte[] data = version.getFileData();
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }

        // Task 3: Use stored MIME type for streaming
        String mimeType = version.getMimeType();
        if (mimeType == null || mimeType.trim().isEmpty()) {
            mimeType = EXTENSION_TO_MIME.getOrDefault(version.getFileType().toUpperCase(Locale.ROOT), "application/octet-stream");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentLength(data.length);

        if ("application/pdf".equalsIgnoreCase(mimeType) || "PDF".equalsIgnoreCase(version.getFileType())) {
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + version.getFileName() + "\"");
        } else {
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + version.getFileName() + "\"");
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }

    @Transactional(readOnly = true)
    public List<DocumentMasterDTO> getAllAdminDocuments(String query, String category) {
        return getAllAdminDocuments(query, category, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<DocumentMasterDTO> getAllAdminDocuments(String query, String category, String status) {
        String cleanStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : "ACTIVE";
        return documentMasterRepository.searchAndFilter(query, category, cleanStatus).stream()
                .map(this::toDTO)
                .toList();
    }

    // Task 4: Public website retrieves ONLY documents where status = APPROVED and latest version approvalStatus = APPROVED
    @Transactional(readOnly = true)
    public List<DocumentMasterDTO> getPublicApprovedDocuments(String category) {
        List<DocumentMaster> masters;
        if (category != null && !category.trim().isEmpty()) {
            masters = documentMasterRepository.findByCategoryIgnoreCaseAndStatus(category, "APPROVED");
        } else {
            masters = documentMasterRepository.findByStatus("APPROVED");
        }

        return masters.stream()
                .map(this::toDTO)
                .filter(dto -> dto.getLatestVersionId() != null && "APPROVED".equalsIgnoreCase(dto.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<DocumentMasterDTO> getDocumentById(Long id) {
        return documentMasterRepository.findById(id).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<VersionHistoryDTO> getVersionHistory(Long masterId) {
        return documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId).stream()
                .map(this::toVersionDTO)
                .toList();
    }

    // =========================================================
    // Approval Workflow — all methods guarded by Optimistic Locking
    // =========================================================

    @Transactional
    public boolean approveDocument(Long masterId, String username) {
        try {
            Optional<DocumentMaster> opt = documentMasterRepository.findById(masterId);
            if (opt.isEmpty()) return false;

            DocumentMaster master = opt.get();
            master.setStatus("APPROVED");
            master.setUpdatedDate(LocalDateTime.now());
            documentMasterRepository.save(master);

            Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(masterId);
            if (latestOpt.isPresent()) {
                DocumentVersion latest = latestOpt.get();
                latest.setApprovalStatus("APPROVED");
                latest.setApprovedBy(username != null ? username : "admin");
                latest.setApprovedDate(LocalDateTime.now());
                documentVersionRepository.save(latest);
            }

            // Sync legacy DocumentEntity if present so public endpoints immediately make it active
            try {
                documentRepository.findByDocumentNameIgnoreCase(master.getDocumentName())
                        .forEach(legacy -> {
                            legacy.setIsActive(true);
                            documentRepository.save(legacy);
                        });
            } catch (Exception ignored) {}

            // Only log on success
            logActivity(masterId, master.getCurrentVersion(), "APPROVE", username,
                    "Approved document: " + master.getDocumentName());
            return true;

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DocumentConflictException(
                    "This document was modified by another administrator while approving. " +
                    "Please refresh and try again.", masterId);
        }
    }

    @Transactional
    public boolean rejectDocument(Long masterId, String remarks, String username) {
        try {
            Optional<DocumentMaster> opt = documentMasterRepository.findById(masterId);
            if (opt.isEmpty()) return false;

            DocumentMaster master = opt.get();
            master.setStatus("REJECTED");
            master.setUpdatedDate(LocalDateTime.now());
            documentMasterRepository.save(master);

            Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(masterId);
            if (latestOpt.isPresent()) {
                DocumentVersion latest = latestOpt.get();
                latest.setApprovalStatus("REJECTED");
                if (remarks != null && !remarks.trim().isEmpty()) {
                    latest.setRemarks(remarks.trim());
                }
                documentVersionRepository.save(latest);
            }

            // Sync legacy DocumentEntity if present so public endpoints immediately exclude it
            try {
                documentRepository.findByDocumentNameIgnoreCase(master.getDocumentName())
                        .forEach(legacy -> {
                            legacy.setIsActive(false);
                            documentRepository.save(legacy);
                        });
            } catch (Exception ignored) {}

            // Only log on success
            logActivity(masterId, master.getCurrentVersion(), "REJECT", username,
                    "Rejected document: " + master.getDocumentName() + " (Remarks: " + remarks + ")");
            return true;

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DocumentConflictException(
                    "This document was modified by another administrator while rejecting. " +
                    "Please refresh and try again.", masterId);
        }
    }

    @Transactional
    public boolean archiveDocument(Long masterId, String username) {
        try {
            Optional<DocumentMaster> opt = documentMasterRepository.findById(masterId);
            if (opt.isEmpty()) return false;

            DocumentMaster master = opt.get();
            master.setStatus("ARCHIVED");
            master.setUpdatedDate(LocalDateTime.now());
            documentMasterRepository.save(master);

            Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(masterId);
            if (latestOpt.isPresent()) {
                DocumentVersion latest = latestOpt.get();
                latest.setApprovalStatus("ARCHIVED");
                documentVersionRepository.save(latest);
            }

            // Sync legacy DocumentEntity if present so public endpoints immediately exclude it
            try {
                documentRepository.findByDocumentNameIgnoreCase(master.getDocumentName())
                        .forEach(legacy -> {
                            legacy.setIsActive(false);
                            documentRepository.save(legacy);
                        });
            } catch (Exception ignored) {}

            // Only log on success
            logActivity(masterId, master.getCurrentVersion(), "ARCHIVE", username,
                    "Archived document: " + master.getDocumentName());
            return true;

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DocumentConflictException(
                    "This document was modified by another administrator while archiving. " +
                    "Please refresh and try again.", masterId);
        }
    }

    @Transactional
    public boolean restoreDocument(Long masterId, String username) {
        try {
            Optional<DocumentMaster> opt = documentMasterRepository.findById(masterId);
            if (opt.isEmpty()) return false;

            DocumentMaster master = opt.get();
            master.setStatus("APPROVED");
            master.setUpdatedDate(LocalDateTime.now());
            documentMasterRepository.save(master);

            Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(masterId);
            if (latestOpt.isPresent()) {
                DocumentVersion latest = latestOpt.get();
                latest.setApprovalStatus("APPROVED");
                documentVersionRepository.save(latest);
            }

            // Sync legacy DocumentEntity if present so public endpoints immediately recognize the restored document as active
            try {
                documentRepository.findByDocumentNameIgnoreCase(master.getDocumentName())
                        .forEach(legacy -> {
                            legacy.setIsActive(true);
                            documentRepository.save(legacy);
                        });
            } catch (Exception ignored) {}

            logActivity(masterId, master.getCurrentVersion(), "RESTORE", username,
                    "Restored document: " + master.getDocumentName() + " (Restored to APPROVED status)");
            return true;

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DocumentConflictException(
                    "This document was modified by another administrator while restoring. " +
                    "Please refresh and try again.", masterId);
        }
    }

    @Transactional
    public boolean deletePermanently(Long masterId, String username) {
        try {
            Optional<DocumentMaster> opt = documentMasterRepository.findById(masterId);
            if (opt.isEmpty()) return false;

            DocumentMaster master = opt.get();
            master.setStatus("DELETED");
            master.setDeletedBy(username != null ? username : "admin");
            master.setDeletedDate(LocalDateTime.now());
            master.setUpdatedDate(LocalDateTime.now());
            documentMasterRepository.save(master);

            Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(masterId);
            if (latestOpt.isPresent()) {
                DocumentVersion latest = latestOpt.get();
                latest.setApprovalStatus("DELETED");
                documentVersionRepository.save(latest);
            }

            // Sync legacy DocumentEntity if present so public endpoints immediately exclude it
            try {
                documentRepository.findByDocumentNameIgnoreCase(master.getDocumentName())
                        .forEach(legacy -> {
                            legacy.setIsActive(false);
                            documentRepository.save(legacy);
                        });
            } catch (Exception ignored) {}

            logActivity(masterId, master.getCurrentVersion(), "PERMANENT_DELETE", username,
                    "Permanently deleted document: " + master.getDocumentName() + " (Moved to Deleted Documents audit log)");
            return true;

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DocumentConflictException(
                    "This document was modified by another administrator while deleting permanently. " +
                    "Please refresh and try again.", masterId);
        }
    }

    @Transactional
    public DocumentMasterDTO updateMetadata(Long masterId, DocumentMasterDTO updateData, String username) {
        try {
            DocumentMaster master = documentMasterRepository.findById(masterId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + masterId));

            // Apply the client's lock token to the managed entity.
            // Hibernate then uses this value in: UPDATE ... WHERE entity_version = ?
            // If another admin saved first (incrementing the version), JPA detects the
            // mismatch at flush time and throws ObjectOptimisticLockingFailureException.
            if (updateData.getEntityVersion() != null) {
                master.setEntityVersion(updateData.getEntityVersion());
            }

            if (updateData.getDocumentName() != null && !updateData.getDocumentName().trim().isEmpty()) {
                master.setDocumentName(updateData.getDocumentName().trim());
            }
            if (updateData.getCategory() != null && !updateData.getCategory().trim().isEmpty()) {
                master.setCategory(updateData.getCategory().trim());
            }
            if (updateData.getProcessId() != null && !updateData.getProcessId().trim().isEmpty()) {
                master.setProcessId(updateData.getProcessId().trim());
            }
            if (updateData.getProcessGroup() != null && !updateData.getProcessGroup().trim().isEmpty()) {
                master.setProcessGroup(updateData.getProcessGroup().trim());
            }
            if (updateData.getDescription() != null) {
                master.setDescription(updateData.getDescription().trim());
            }

            master.setUpdatedDate(LocalDateTime.now());
            master = documentMasterRepository.save(master);

            // Only log on success — failed lock attempts must not create audit records
            logActivity(masterId, master.getCurrentVersion(), "UPDATE", username,
                    "Updated metadata for " + master.getDocumentName());

            return toDTO(master);

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DocumentConflictException(
                    "This document has been modified by another administrator. " +
                    "Please refresh the page and try again.", masterId);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        long totalApproved = documentMasterRepository.countByStatus("APPROVED");
        long pendingReview = documentMasterRepository.countByStatus("UNDER_REVIEW");
        long aspicePrm = documentMasterRepository.countByCategoryIgnoreCaseAndStatus("ASPICE PRM", "APPROVED");
        long generic = documentMasterRepository.countByCategoryIgnoreCaseAndStatus("Generic Templates", "APPROVED");
        long lessons = documentMasterRepository.countByCategoryIgnoreCaseAndStatus("Lessons Learned", "APPROVED");
        long checklist = documentMasterRepository.countByCategoryIgnoreCaseAndStatus("Assessment Checklist", "APPROVED");
        LocalDateTime latest = documentMasterRepository.findLatestUploadDate();

        stats.put("totalDocuments", totalApproved);
        stats.put("pendingReview", pendingReview);
        stats.put("aspicePrmDocuments", aspicePrm);
        stats.put("genericTemplates", generic);
        stats.put("lessonsLearned", lessons);
        stats.put("assessmentChecklists", checklist);
        stats.put("latestUploadDate", latest != null ? latest.toString() : "N/A");
        return stats;
    }

    public DocumentMasterDTO toDTO(DocumentMaster master) {
        Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(master.getId());

        DocumentMasterDTO.DocumentMasterDTOBuilder builder = DocumentMasterDTO.builder()
                .id(master.getId())
                .entityVersion(master.getEntityVersion())   // Optimistic lock token
                .documentCode(master.getDocumentCode())
                .processId(master.getProcessId())
                .processName(master.getProcessName())
                .processGroup(master.getProcessGroup())
                .category(master.getCategory())
                .documentName(master.getDocumentName())
                .description(master.getDescription())
                .currentVersion(master.getCurrentVersion())
                .status(master.getStatus())
                .createdBy(master.getCreatedBy())
                .createdDate(master.getCreatedDate())
                .updatedDate(master.getUpdatedDate())
                .deletedBy(master.getDeletedBy())
                .deletedDate(master.getDeletedDate());

        if (latestOpt.isPresent()) {
            DocumentVersion latest = latestOpt.get();
            builder.latestVersionId(latest.getId())
                    .fileName(latest.getFileName())
                    .fileType(latest.getFileType())
                    .mimeType(latest.getMimeType())
                    .fileSize(latest.getFileSize())
                    .checksum(latest.getChecksum())
                    .downloadUrl("/api/public/dms/download/" + latest.getId());
        }

        return builder.build();
    }

    public VersionHistoryDTO toVersionDTO(DocumentVersion v) {
        return VersionHistoryDTO.builder()
                .versionId(v.getId())
                .documentMasterId(v.getDocumentMaster().getId())
                .version(v.getVersion()) // Generated dynamically from major.minor
                .majorVersion(v.getMajorVersion())
                .minorVersion(v.getMinorVersion())
                .fileName(v.getFileName())
                .fileType(v.getFileType())
                .mimeType(v.getMimeType())
                .fileSize(v.getFileSize())
                .checksum(v.getChecksum())
                .uploadedBy(v.getUploadedBy())
                .uploadedDate(v.getUploadedDate())
                .approvedBy(v.getApprovedBy())
                .approvedDate(v.getApprovedDate())
                .approvalStatus(v.getApprovalStatus())
                .remarks(v.getRemarks())
                .isLatest(v.getIsLatest())
                .downloadUrl("/api/public/dms/download/" + v.getId())
                .build();
    }

    public void logActivity(Long masterId, String version, String action, String username, String remarks) {
        String user = username != null ? username : "admin";
        DmsMigrationLog log = DmsMigrationLog.builder()
                .documentMasterId(masterId)
                .version(version)
                .action(action)
                .performedBy(user)
                .performedDate(LocalDateTime.now())
                .remarks(remarks)
                .build();
        dmsMigrationLogRepository.save(log);

        activityLogService.logActivity(user, "DMS " + action, remarks != null ? remarks : ("Document Master ID: " + masterId + " (v" + version + ")"));
    }

    public String calculateChecksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    // Task 3: MIME Type & Content Validation via Apache Tika Magic Bytes
    private String resolveAndValidateMimeType(MultipartFile file, String fileExt) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }
        String expectedMime = EXTENSION_TO_MIME.get(fileExt);

        if (expectedMime == null) {
            log.warn("[DMS Validation] Rejected upload with unsupported extension: .{}", fileExt);
            throw new IllegalArgumentException("Unsupported file format: ." + fileExt + ". Allowed formats: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX");
        }

        String detectedMime;
        try {
            detectedMime = tika.detect(file.getInputStream(), file.getOriginalFilename());
        } catch (Exception e) {
            log.warn("[DMS Validation] Tika detection failed for file {}, falling back to client header: {}", file.getOriginalFilename(), e.getMessage());
            detectedMime = file.getContentType();
        }

        if (detectedMime != null && !detectedMime.equalsIgnoreCase("application/octet-stream")) {
            boolean valid = detectedMime.equalsIgnoreCase(expectedMime)
                    || ALLOWED_MIME_TYPES.contains(detectedMime)
                    || (detectedMime.contains("zip") && (fileExt.equals("DOCX") || fileExt.equals("XLSX") || fileExt.equals("PPTX")))
                    || (detectedMime.contains("ole-storage") && (fileExt.equals("DOC") || fileExt.equals("XLS") || fileExt.equals("PPT")));
            if (!valid) {
                log.warn("[DMS Validation] Content mismatch for file {}: detected MIME {} vs expected extension .{}", file.getOriginalFilename(), detectedMime, fileExt);
                throw new IllegalArgumentException("Invalid file content: detected format (" + detectedMime + ") does not match extension ." + fileExt);
            }
            return detectedMime;
        }

        return expectedMime;
    }

    public void validateFileContent(byte[] bytes, String originalName, String fileExt) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File content cannot be empty.");
        }

        String ext = (fileExt != null) ? fileExt.toUpperCase(Locale.ROOT) : getFileExtension(originalName).toUpperCase(Locale.ROOT);
        String expectedMime = EXTENSION_TO_MIME.get(ext);
        if (expectedMime == null) {
            log.warn("[DMS Validation] Rejected version file with unsupported extension: .{}", ext);
            throw new IllegalArgumentException("Unsupported file format: ." + ext + ". Allowed formats: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX");
        }

        String detectedMime;
        try {
            detectedMime = tika.detect(bytes, originalName);
        } catch (Exception e) {
            detectedMime = expectedMime;
        }

        if (detectedMime != null && !detectedMime.equalsIgnoreCase("application/octet-stream")) {
            boolean valid = detectedMime.equalsIgnoreCase(expectedMime)
                    || ALLOWED_MIME_TYPES.contains(detectedMime)
                    || (detectedMime.contains("zip") && (ext.equals("DOCX") || ext.equals("XLSX") || ext.equals("PPTX")))
                    || (detectedMime.contains("ole-storage") && (ext.equals("DOC") || ext.equals("XLS") || ext.equals("PPT")));
            if (!valid) {
                log.warn("[DMS Validation] Content mismatch for version file {}: detected MIME {} vs expected extension .{}", originalName, detectedMime, ext);
                throw new IllegalArgumentException("Invalid file content: detected format (" + detectedMime + ") does not match extension ." + ext);
            }
        }
    }

    private String getFileExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx + 1) : "DOC";
    }

    private String removeExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(0, idx) : fileName;
    }

    /**
     * Generates a unique document code from processId, category, and documentName.
     * Format: <PROC>-<CAT_ABBREV>-<DOC_ABBREV>-<TIMESTAMP>
     */
    private String generateDocumentCode(String processId, String category, String documentName) {
        String proc = processId.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (proc.length() > 8) proc = proc.substring(0, 8);

        String cat = category.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (cat.length() > 4) cat = cat.substring(0, 4);

        String doc = documentName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (doc.length() > 6) doc = doc.substring(0, 6);

        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        String candidate = proc + "-" + cat + "-" + doc + "-" + timestamp;

        // Ensure uniqueness
        if (documentMasterRepository.findByDocumentCode(candidate).isPresent()) {
            candidate = candidate + "-" + (int)(Math.random() * 1000);
        }
        return candidate;
    }

    public List<DocumentMasterDTO> searchAndFilter(String query, String category) {
        return searchAndFilter(query, category, "ACTIVE");
    }

    public List<DocumentMasterDTO> searchAndFilter(String query, String category, String status) {
        return getAllAdminDocuments(query, category, status);
    }

    @Transactional(readOnly = true)
    public Page<DocumentMasterDTO> searchAndFilterPaged(String query, String category, Pageable pageable) {
        return searchAndFilterPaged(query, category, "ACTIVE", pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentMasterDTO> searchAndFilterPaged(String query, String category, String status, Pageable pageable) {
        String cleanQ = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        String cleanCat = (category != null && !category.trim().isEmpty()) ? category.trim() : null;
        String cleanStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : "ACTIVE";
        Page<DocumentMaster> pagedMasters = documentMasterRepository.searchAndFilterPaged(cleanQ, cleanCat, cleanStatus, pageable);
        return pagedMasters.map(this::toDTO);
    }
}
