package com.qualitywebsite.service;

import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;

import com.qualitywebsite.entity.DeletedDocument;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DeletedDocumentRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMasterRepository documentMasterRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DeletedDocumentRepository deletedDocumentRepository;
    private final ActivityLogService activityLogService;

    @Value("${app.upload.dir:./uploaded-documents}")
    private String uploadDir;

    public List<DocumentEntity> getAllDocuments() {
        return documentRepository.findAllByIsActiveTrue().stream()
                .filter(this::isMasterApproved)
                .map(this::syncVersionFromMaster)
                .toList();
    }

    // Search and filter documents by query and category
    public List<DocumentEntity> searchAndFilter(String query, String category) {
        return documentRepository.searchAndFilter(query, category).stream()
                .filter(this::isMasterApproved)
                .map(this::syncVersionFromMaster)
                .toList();
    }

    public Optional<DocumentEntity> getById(String id) {
        return documentRepository.findById(id)
                .filter(this::isMasterApproved)
                .map(this::syncVersionFromMaster);
    }

    public List<DocumentEntity> getByCategory(String category) {
        return documentRepository.findByCategoryIgnoreCaseAndIsActiveTrue(category).stream()
                .filter(this::isMasterApproved)
                .map(this::syncVersionFromMaster)
                .toList();
    }

    public DocumentEntity syncVersionFromMaster(DocumentEntity doc) {
        if (doc == null) return null;
        Optional<DocumentMaster> masterOpt = documentMasterRepository
                .findByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase(
                        doc.getProcess(), doc.getCategory(), doc.getDocumentName());
        if (masterOpt.isEmpty()) {
            masterOpt = documentMasterRepository.findAll().stream()
                    .filter(m -> m.getDocumentName() != null && m.getDocumentName().equalsIgnoreCase(doc.getDocumentName()))
                    .findFirst();
        }
        if (masterOpt.isPresent()) {
            DocumentMaster master = masterOpt.get();
            if (master.getCurrentVersion() != null && !master.getCurrentVersion().isBlank()) {
                doc.setVersion(master.getCurrentVersion());
            }
        }
        return doc;
    }

    public boolean isMasterApproved(DocumentEntity doc) {
        if (doc == null || !Boolean.TRUE.equals(doc.getIsActive())) return false;
        
        Optional<DocumentMaster> masterOpt = documentMasterRepository
                .findByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase(
                        doc.getProcess(), doc.getCategory(), doc.getDocumentName());
        if (masterOpt.isPresent()) {
            DocumentMaster master = masterOpt.get();
            return "APPROVED".equalsIgnoreCase(master.getStatus());
        }

        Optional<DocumentMaster> masterByNameOpt = documentMasterRepository.findAll().stream()
                .filter(m -> m.getDocumentName() != null && m.getDocumentName().equalsIgnoreCase(doc.getDocumentName()))
                .findFirst();
        if (masterByNameOpt.isPresent()) {
            return "APPROVED".equalsIgnoreCase(masterByNameOpt.get().getStatus());
        }

        // Phase 3 Hardening Rule: If DMS master cannot be found, public access MUST be denied (return false).
        return false;
    }

    public boolean isDuplicate(String id, String documentName, String category, String process) {
        // Check by custom ID first (exact primary-key lookup — O(1))
        if (id != null && documentRepository.existsById(id)) {
            return true;
        }
        // RC-6 fix: replaced full documentRepository.findAll() + Java-level loop (O(N) table scan)
        // with a single SELECT EXISTS(...) query executed entirely in the database.
        return documentRepository
                .existsByDocumentNameIgnoreCaseAndCategoryIgnoreCaseAndProcessIgnoreCase(
                        documentName, category, process);
    }

    public DocumentEntity saveDocument(DocumentEntity doc, MultipartFile file, String username) throws IOException {
        String fileExt = "";
        if (file != null && !file.isEmpty()) {
            String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
            fileExt = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".") + 1).toUpperCase() : "DOC";

            String storedFileName = UUID.randomUUID().toString() + "_" + originalName;
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path targetPath = uploadPath.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            doc.setFileName(originalName);
            doc.setFilePath("/uploaded-documents/" + storedFileName);
            doc.setFileType(fileExt);
            doc.setFileSize(file.getSize());
        }

        if (doc.getId() == null || doc.getId().trim().isEmpty()) {
            String generatedId = generateDocumentId(doc.getProcess(), doc.getCategory());
            doc.setId(generatedId);
        }

        if (doc.getFileType() == null || doc.getFileType().isEmpty()) {
            doc.setFileType("DOC");
        }

        DocumentEntity saved = documentRepository.save(doc);
        activityLogService.logActivity(username, "Uploaded Document", "Uploaded " + saved.getProcess() + " " + saved.getDocumentName() + " (v" + saved.getVersion() + ")");
        return saved;
    }

    public DocumentEntity updateMetadata(String id, DocumentEntity updateData, String username) {
        DocumentEntity existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        existing.setDocumentName(updateData.getDocumentName());
        existing.setVersion(updateData.getVersion());
        existing.setDescription(updateData.getDescription());
        existing.setProcess(updateData.getProcess());
        existing.setProcessGroup(updateData.getProcessGroup());
        existing.setCategory(updateData.getCategory());
        existing.setUpdatedAt(LocalDateTime.now());

        DocumentEntity saved = documentRepository.save(existing);
        activityLogService.logActivity(username, "Updated Document", "Updated details for " + saved.getProcess() + " " + saved.getDocumentName());
        return saved;
    }

    public DocumentEntity replaceFile(String id, MultipartFile file, String username) throws IOException {
        DocumentEntity existing = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Delete old file if present inside uploadDir
        deletePhysicalFile(existing.getFilePath());

        String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExt = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".") + 1).toUpperCase() : "DOC";
        String storedFileName = UUID.randomUUID().toString() + "_" + originalName;

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path targetPath = uploadPath.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        existing.setFileName(originalName);
        existing.setFilePath("/uploaded-documents/" + storedFileName);
        existing.setFileType(fileExt);
        existing.setFileSize(file.getSize());
        existing.setUpdatedAt(LocalDateTime.now());

        DocumentEntity saved = documentRepository.save(existing);
        activityLogService.logActivity(username, "Replaced File", "Replaced file for " + saved.getProcess() + " " + saved.getDocumentName());
        return saved;
    }

    public boolean deleteDocument(String id, String username) {
        Optional<DocumentEntity> opt = documentRepository.findById(id);
        if (opt.isPresent()) {
            DocumentEntity doc = opt.get();
            // Soft delete: mark as inactive instead of removing the row
            doc.setIsActive(false);
            documentRepository.save(doc);

            // Synchronize with DMS DocumentMaster and DocumentVersion if present
            Optional<DocumentMaster> masterOpt = documentMasterRepository.findByDocumentCode(id);
            if (masterOpt.isEmpty()) {
                masterOpt = documentMasterRepository.findByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase(
                        doc.getProcess(), doc.getCategory(), doc.getDocumentName());
            }
            if (masterOpt.isEmpty()) {
                masterOpt = documentMasterRepository.findAll().stream()
                        .filter(m -> m.getDocumentName() != null && m.getDocumentName().equalsIgnoreCase(doc.getDocumentName()))
                        .findFirst();
            }
            if (masterOpt.isPresent()) {
                DocumentMaster master = masterOpt.get();
                master.setStatus("DELETED");
                master.setDeletedBy(username != null ? username : "admin");
                master.setDeletedDate(LocalDateTime.now());
                master.setUpdatedDate(LocalDateTime.now());
                documentMasterRepository.save(master);

                Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(master.getId());
                if (latestOpt.isPresent()) {
                    DocumentVersion latest = latestOpt.get();
                    latest.setApprovalStatus("DELETED");
                    documentVersionRepository.save(latest);
                }

                try {
                    DocumentVersion latest = latestOpt.orElse(null);
                    DeletedDocument delDoc = deletedDocumentRepository.findByOriginalMasterId(master.getId())
                            .orElseGet(() -> DeletedDocument.builder().originalMasterId(master.getId()).build());
                    delDoc.setDocumentCode(master.getDocumentCode());
                    delDoc.setProcessId(master.getProcessId());
                    delDoc.setProcessName(master.getProcessName());
                    delDoc.setProcessGroup(master.getProcessGroup());
                    delDoc.setCategory(master.getCategory());
                    delDoc.setDocumentName(master.getDocumentName());
                    delDoc.setDescription(master.getDescription());
                    delDoc.setCurrentVersion(master.getCurrentVersion());
                    delDoc.setFileName(latest != null ? latest.getFileName() : doc.getFileName());
                    delDoc.setFileType(latest != null ? latest.getFileType() : doc.getFileType());
                    delDoc.setMimeType(latest != null ? latest.getMimeType() : null);
                    delDoc.setFileSize(latest != null ? latest.getFileSize() : doc.getFileSize());
                    delDoc.setFileData(latest != null ? latest.getFileData() : null);
                    delDoc.setChecksum(latest != null ? latest.getChecksum() : null);
                    delDoc.setCreatedBy(master.getCreatedBy());
                    delDoc.setCreatedDate(master.getCreatedDate());
                    delDoc.setDeletedBy(username != null ? username : "admin");
                    delDoc.setDeletedDate(LocalDateTime.now());
                    deletedDocumentRepository.save(delDoc);
                } catch (Exception ignored) {}
            }

            // Optionally keep the physical file for archival purposes; delete if desired
            deletePhysicalFile(doc.getFilePath());
            activityLogService.logActivity(username, "Deleted Document", "Soft‑deleted " + doc.getProcess() + " " + doc.getDocumentName());
            return true;
        }
        return false;
    }

    private void deletePhysicalFile(String filePath) {
        if (filePath != null && filePath.startsWith("/uploaded-documents/")) {
            String fileName = filePath.substring("/uploaded-documents/".length());
            try {
                Path fileToDel = Paths.get(uploadDir).resolve(fileName);
                Files.deleteIfExists(fileToDel);
            } catch (Exception ignored) {
            }
        }
    }

    private String generateDocumentId(String process, String category) {
        String prefix = (process != null && !process.trim().isEmpty()) ? process.replace(".", "").toUpperCase() : "DOC";
        long count = documentRepository.count() + 1;
        String id = prefix + "-" + String.format("%03d", count);
        while (documentRepository.existsById(id)) {
            count++;
            id = prefix + "-" + String.format("%03d", count);
        }
        return id;
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        // RC-5 fix: use active-only count methods so soft-deleted documents are excluded.
        // Previously, documentRepository.count() and countByCategoryIgnoreCase() included
        // rows with isActive=false, inflating the admin dashboard totals.
        long total     = documentRepository.countByIsActiveTrue();
        long aspicePrm = documentRepository.countByCategoryIgnoreCaseAndIsActiveTrue("ASPICE PRM");
        long generic   = documentRepository.countByCategoryIgnoreCaseAndIsActiveTrue("Generic Templates");
        long lessons   = documentRepository.countByCategoryIgnoreCaseAndIsActiveTrue("Lessons Learned");
        long checklist = documentRepository.countByCategoryIgnoreCaseAndIsActiveTrue("Assessment Checklist");
        LocalDateTime latest = documentRepository.findLatestUploadDate();

        stats.put("totalDocuments", total);
        stats.put("aspicePrmDocuments", aspicePrm);
        stats.put("genericTemplates", generic);
        stats.put("lessonsLearned", lessons);
        stats.put("assessmentChecklists", checklist);
        stats.put("latestUploadDate", latest != null ? latest.toString() : "N/A");
        return stats;
    }
}