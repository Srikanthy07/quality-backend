package com.qualitywebsite.service;

import com.qualitywebsite.dto.DocumentReconciliationItemDTO;
import com.qualitywebsite.entity.DeletedDocument;
import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DeletedDocumentRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentReconciliationService {

    private final DocumentMasterRepository documentMasterRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DeletedDocumentRepository deletedDocumentRepository;
    private final DocumentRepository documentRepository;
    private final ActivityLogService activityLogService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(3) // Run after DataInitializationService & DmsMigrationService
    @Transactional
    public List<DocumentReconciliationItemDTO> runOneTimeReconciliation() {
        log.info("Starting safe idempotent document reconciliation across all database records...");
        recoverIncorrectlyDeletedDocuments();
        return reconcileAllDocuments();
    }

    @Transactional
    public List<DocumentMaster> recoverIncorrectlyDeletedDocuments() {
        List<DocumentMaster> recovered = new ArrayList<>();
        List<DocumentMaster> deletedMasters = documentMasterRepository.findByStatusIgnoreCase("DELETED");

        for (DocumentMaster master : deletedMasters) {
            Long masterId = master.getId();
            Optional<DeletedDocument> delOpt = deletedDocumentRepository.findByOriginalMasterId(masterId);
            if (delOpt.isEmpty() && master.getDocumentCode() != null && !master.getDocumentCode().isBlank()) {
                delOpt = deletedDocumentRepository.findByDocumentCode(master.getDocumentCode());
            }

            // Evidence check:
            // 1. No explicit DeletedDocument record exists matching this masterId or documentCode.
            // 2. A DeletedDocument record exists with the exact same documentName under a DIFFERENT originalMasterId
            //    (proving this document was incorrectly auto-deleted by the old name-matching reconciliation bug).
            if (delOpt.isEmpty() && master.getDocumentName() != null && !master.getDocumentName().isBlank()) {
                Optional<DeletedDocument> nameMatchOpt = deletedDocumentRepository.findByDocumentNameIgnoreCase(master.getDocumentName());
                if (nameMatchOpt.isPresent() && !Objects.equals(nameMatchOpt.get().getOriginalMasterId(), masterId)) {
                    log.info("[DOCUMENT RECOVERY] Found evidence of incorrect auto-deletion for Master ID: {}, Name: '{}', Code: '{}'. Restoring status to APPROVED.",
                            masterId, master.getDocumentName(), master.getDocumentCode());

                    master.setStatus("APPROVED");
                    master.setDeletedBy(null);
                    master.setDeletedDate(null);
                    master.setUpdatedDate(LocalDateTime.now());
                    master = documentMasterRepository.save(master);

                    List<DocumentVersion> versions = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId);
                    for (DocumentVersion v : versions) {
                        if ("DELETED".equalsIgnoreCase(v.getApprovalStatus())) {
                            v.setApprovalStatus("APPROVED");
                            documentVersionRepository.save(v);
                        }
                    }
                    recovered.add(master);
                }
            }
        }
        if (!recovered.isEmpty()) {
            log.info("[DOCUMENT RECOVERY] Successfully recovered {} incorrectly auto-deleted master document(s).", recovered.size());
        }
        return recovered;
    }

    @Transactional
    public List<DocumentReconciliationItemDTO> reconcileAllDocuments() {
        List<DocumentReconciliationItemDTO> report = new ArrayList<>();
        List<DocumentMaster> masters = documentMasterRepository.findAll();

        int activeCount = 0;
        int deletedCount = 0;
        int underReviewCount = 0;
        int statusCorrectedCount = 0;
        int versionCorrectedCount = 0;
        int checksumCorrectedCount = 0;
        int archiveCreatedCount = 0;
        int conflictsCount = 0;

        for (DocumentMaster master : masters) {
            DocumentReconciliationItemDTO item = reconcileSingleMaster(master);
            report.add(item);

            String res = item.getReconciliationResult();
            if (res.contains("STATUS_CORRECTED")) statusCorrectedCount++;
            if (res.contains("VERSION_CORRECTED")) versionCorrectedCount++;
            if (res.contains("CHECKSUM_CORRECTED")) checksumCorrectedCount++;
            if (res.contains("ARCHIVE_CREATED")) archiveCreatedCount++;
            if (res.contains("CONFLICT_REQUIRES_REVIEW")) conflictsCount++;

            String finalStatus = item.getMasterStatus();
            if ("APPROVED".equalsIgnoreCase(finalStatus)) activeCount++;
            else if ("DELETED".equalsIgnoreCase(finalStatus) || "ARCHIVED".equalsIgnoreCase(finalStatus)) deletedCount++;
            else if ("UNDER_REVIEW".equalsIgnoreCase(finalStatus)) underReviewCount++;
        }

        log.info("================ DOCUMENT RECONCILIATION SUMMARY ================");
        log.info("Total Documents Scanned        : {}", masters.size());
        log.info("Active Approved Documents      : {}", activeCount);
        log.info("Deleted / Archived Documents   : {}", deletedCount);
        log.info("Under Review Documents         : {}", underReviewCount);
        log.info("Statuses Corrected             : {}", statusCorrectedCount);
        log.info("Versions Corrected             : {}", versionCorrectedCount);
        log.info("Checksums Corrected            : {}", checksumCorrectedCount);
        log.info("Archive Records Created        : {}", archiveCreatedCount);
        log.info("Conflicts Requiring Manual Rev : {}", conflictsCount);
        log.info("================================================================");

        return report;
    }

    @Transactional
    public DocumentReconciliationItemDTO reconcileSingleMaster(DocumentMaster master) {
        if (master == null) return null;

        Long masterId = master.getId();
        String origStatus = master.getStatus();
        String docName = master.getDocumentName() != null ? master.getDocumentName() : "Untitled Document";
        String cat = master.getCategory() != null ? master.getCategory() : "General";
        String proc = master.getProcessId() != null ? master.getProcessId() : "GLOBAL";
        String procGroup = master.getProcessGroup() != null ? master.getProcessGroup() : "General";
        String code = master.getDocumentCode() != null ? master.getDocumentCode() : ("DOC-" + masterId);

        Set<String> actions = new LinkedHashSet<>();

        // 1. Check if DeletedDocument archive record exists strictly by originalMasterId or exact documentCode
        // DO NOT match by documentName alone, as multiple distinct documents can share identical display names.
        Optional<DeletedDocument> delOpt = deletedDocumentRepository.findByOriginalMasterId(masterId);
        if (delOpt.isEmpty() && master.getDocumentCode() != null && !master.getDocumentCode().isBlank()) {
            delOpt = deletedDocumentRepository.findByDocumentCode(master.getDocumentCode());
        }

        // A master document is considered deleted ONLY if an explicit archive record exists matching its masterId or documentCode,
        // or if its status was explicitly set to 'DELETED' by a deletion operation.
        boolean shouldBeDeleted = delOpt.isPresent() || "DELETED".equalsIgnoreCase(origStatus);

        if (shouldBeDeleted && !"DELETED".equalsIgnoreCase(master.getStatus())) {
            log.info("[DOCUMENT RECONCILIATION] Document: '{}', Master ID: {}, Code: {}, Previous status: {}, Correct status: DELETED, Action: STATUS_CORRECTED (Matched DeletedDocument ID: {})",
                    docName, masterId, code, origStatus, delOpt.map(DeletedDocument::getId).orElse(null));
            master.setStatus("DELETED");
            if (master.getDeletedBy() == null) {
                master.setDeletedBy(delOpt.map(DeletedDocument::getDeletedBy).orElse("admin"));
            }
            if (master.getDeletedDate() == null) {
                master.setDeletedDate(delOpt.map(DeletedDocument::getDeletedDate).orElse(LocalDateTime.now()));
            }
            master.setUpdatedDate(LocalDateTime.now());
            master = documentMasterRepository.save(master);
            actions.add("STATUS_CORRECTED");
        }

        // 2. Ensure DeletedDocument archive record exists if status is DELETED / ARCHIVED
        if ("DELETED".equalsIgnoreCase(master.getStatus()) || "ARCHIVED".equalsIgnoreCase(master.getStatus())) {
            if (delOpt.isEmpty()) {
                ensureDeletedDocumentArchive(master, "admin");
                actions.add("ARCHIVE_CREATED");
            }
        }

        // 3. Version Reconciliation
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId);
        DocumentVersion latestVersion = null;
        if (!versions.isEmpty()) {
            latestVersion = versions.get(0);

            // Reconcile version statuses
            for (DocumentVersion v : versions) {
                if ("DELETED".equalsIgnoreCase(master.getStatus()) && !"DELETED".equalsIgnoreCase(v.getApprovalStatus())) {
                    v.setApprovalStatus("DELETED");
                    documentVersionRepository.save(v);
                    actions.add("VERSION_CORRECTED");
                }
                boolean expectedIsLatest = v.getId().equals(latestVersion.getId());
                if (!Objects.equals(v.getIsLatest(), expectedIsLatest)) {
                    v.setIsLatest(expectedIsLatest);
                    documentVersionRepository.save(v);
                    actions.add("VERSION_CORRECTED");
                }
            }

            String expectedVerStr = latestVersion.getVersion();
            if (expectedVerStr == null || expectedVerStr.isBlank()) {
                int maj = latestVersion.getMajorVersion() != null ? latestVersion.getMajorVersion() : 1;
                int min = latestVersion.getMinorVersion() != null ? latestVersion.getMinorVersion() : 0;
                expectedVerStr = maj + "." + min;
                latestVersion.setVersion(expectedVerStr);
                documentVersionRepository.save(latestVersion);
                actions.add("VERSION_CORRECTED");
            }

            if (!Objects.equals(master.getCurrentVersion(), expectedVerStr)) {
                master.setCurrentVersion(expectedVerStr);
                documentMasterRepository.save(master);
                actions.add("VERSION_CORRECTED");
            }
        }

        String actionResultStr = actions.isEmpty() ? "UNCHANGED" : String.join(", ", actions);
        if (actionResultStr.equals("UNCHANGED")) actionResultStr = "SKIPPED";

        return DocumentReconciliationItemDTO.builder()
                .documentCode(code)
                .processGroup(procGroup)
                .processName(proc)
                .documentName(docName)
                .version(master.getCurrentVersion() != null ? master.getCurrentVersion() : "1.0")
                .masterId(masterId)
                .versionId(latestVersion != null ? latestVersion.getId() : null)
                .checksum(latestVersion != null ? latestVersion.getChecksum() : null)
                .masterStatus(master.getStatus())
                .versionStatus(latestVersion != null ? latestVersion.getApprovalStatus() : master.getStatus())
                .reconciliationResult(actionResultStr)
                .details("Master ID " + masterId + " reconciled cleanly. Status: " + master.getStatus())
                .build();
    }

    private void ensureDeletedDocumentArchive(DocumentMaster master, String username) {
        if (master == null) return;

        Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(master.getId());
        if (latestOpt.isEmpty()) {
            List<DocumentVersion> versions = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(master.getId());
            if (!versions.isEmpty()) {
                latestOpt = Optional.of(versions.get(0));
            }
        }
        DocumentVersion latest = latestOpt.orElse(null);

        DeletedDocument delDoc = deletedDocumentRepository.findByOriginalMasterId(master.getId())
                .orElseGet(() -> DeletedDocument.builder().originalMasterId(master.getId()).build());

        delDoc.setDocumentCode(master.getDocumentCode());
        delDoc.setProcessId(master.getProcessId());
        delDoc.setProcessName(master.getProcessName());
        delDoc.setProcessGroup(master.getProcessGroup());
        delDoc.setCategory(master.getCategory() != null && !master.getCategory().isBlank() ? master.getCategory() : "General");
        delDoc.setDocumentName(master.getDocumentName() != null && !master.getDocumentName().isBlank() ? master.getDocumentName() : "Untitled Document");
        delDoc.setDescription(master.getDescription());
        delDoc.setCurrentVersion(master.getCurrentVersion());
        delDoc.setFileName(latest != null ? latest.getFileName() : null);
        delDoc.setFileType(latest != null ? latest.getFileType() : null);
        delDoc.setMimeType(latest != null ? latest.getMimeType() : null);
        delDoc.setFileSize(latest != null ? latest.getFileSize() : null);
        delDoc.setFileData(latest != null ? latest.getFileData() : null);
        delDoc.setChecksum(latest != null ? latest.getChecksum() : null);
        delDoc.setCreatedBy(master.getCreatedBy());
        delDoc.setCreatedDate(master.getCreatedDate());
        delDoc.setDeletedBy(master.getDeletedBy() != null ? master.getDeletedBy() : username);
        delDoc.setDeletedDate(master.getDeletedDate() != null ? master.getDeletedDate() : LocalDateTime.now());

        deletedDocumentRepository.save(delDoc);
        log.info("[DOCUMENT RECONCILIATION] Created missing DeletedDocument archive record for master ID {}", master.getId());
    }
}
