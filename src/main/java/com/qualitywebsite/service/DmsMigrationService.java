package com.qualitywebsite.service;

import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DmsMigrationService {

    private final DocumentRepository documentRepository;
    private final DocumentMasterRepository documentMasterRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DmsDocumentService dmsDocumentService;
    private final ResourceLoader resourceLoader;

    @Value("${app.upload.dir:./uploaded-documents}")
    private String uploadDir;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2) // Run after DataInitializationService
    @Transactional
    public void migrateToDatabaseStorage() {
        log.info("[DMS Migration] Synchronizing legacy document entities into MySQL LONGBLOB DMS storage...");

        List<DocumentEntity> existingEntities = documentRepository.findAll();
        int migratedCount = 0;
        int failedCount = 0;

        for (DocumentEntity legacy : existingEntities) {
            try {
                String code = legacy.getId() != null ? legacy.getId() : (legacy.getProcess() + "-" + legacy.getFileName());
                String targetFileName = legacy.getFileName() != null ? legacy.getFileName() : (legacy.getDocumentName() + "." + getExtension(legacy.getFileName()).toLowerCase());

                Optional<DocumentMaster> existingMasterOpt = documentMasterRepository.findByDocumentCode(code);
                if (existingMasterOpt.isPresent() || documentVersionRepository.findFirstByFileNameIgnoreCase(targetFileName).isPresent()) {
                    if (existingMasterOpt.isPresent()) {
                        DocumentMaster master = existingMasterOpt.get();
                        String legVer = legacy.getVersion() != null ? legacy.getVersion() : "1.0";
                        if (!legVer.equals(master.getCurrentVersion())) {
                            master.setCurrentVersion(legVer);
                            documentMasterRepository.save(master);

                            int[] parts = parseVersion(legVer);
                            Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(master.getId());
                            if (latestOpt.isPresent()) {
                                DocumentVersion dv = latestOpt.get();
                                dv.setMajorVersion(parts[0]);
                                dv.setMinorVersion(parts[1]);
                                documentVersionRepository.save(dv);
                            }
                        }
                    }
                    continue; // Already migrated/seeded
                }

                byte[] fileBytes = readPhysicalFileBytes(legacy.getFilePath());

                if (fileBytes == null || fileBytes.length == 0) {
                    log.warn("[DMS Migration] Physical file not found or empty for legacy doc: {} ({})", legacy.getId(), legacy.getFilePath());
                    fileBytes = ("Empty placeholder for legacy document " + legacy.getId()).getBytes();
                }

                String checksum = dmsDocumentService.calculateChecksum(fileBytes);
                String fileExt = legacy.getFileType() != null ? legacy.getFileType().toUpperCase() : getExtension(legacy.getFileName());

                String processId = (legacy.getProcess() != null && !legacy.getProcess().isEmpty()) ? legacy.getProcess() : "GLOBAL";
                String category = (legacy.getCategory() != null && !legacy.getCategory().isEmpty()) ? legacy.getCategory() : "ASPICE PRM";
                String docName = (legacy.getDocumentName() != null && !legacy.getDocumentName().isEmpty()) ? legacy.getDocumentName() : "Document";
                String legVer = legacy.getVersion() != null ? legacy.getVersion() : "1.0";
                int[] verParts = parseVersion(legVer);

                DocumentMaster master = DocumentMaster.builder()
                        .documentCode(code)
                        .processId(processId)
                        .processName(processId)
                        .processGroup(legacy.getProcessGroup() != null ? legacy.getProcessGroup() : "General")
                        .category(category)
                        .documentName(docName)
                        .description(legacy.getDescription())
                        .currentVersion(legVer)
                        .status("APPROVED")
                        .createdBy(legacy.getCreatedBy() != null ? legacy.getCreatedBy() : "system")
                        .createdDate(legacy.getCreatedAt() != null ? legacy.getCreatedAt() : LocalDateTime.now())
                        .updatedDate(legacy.getUpdatedAt() != null ? legacy.getUpdatedAt() : LocalDateTime.now())
                        .build();

                master = documentMasterRepository.save(master);

                DocumentVersion version = DocumentVersion.builder()
                        .documentMaster(master)
                        .version(legVer)
                        .majorVersion(verParts[0])
                        .minorVersion(verParts[1])
                        .fileName(legacy.getFileName() != null ? legacy.getFileName() : (docName + "." + fileExt.toLowerCase()))
                        .fileType(fileExt)
                        .fileSize((long) fileBytes.length)
                        .fileData(fileBytes)
                        .checksum(checksum)
                        .uploadedBy("migration")
                        .uploadedDate(legacy.getCreatedAt() != null ? legacy.getCreatedAt() : LocalDateTime.now())
                        .approvedBy("system")
                        .approvedDate(legacy.getCreatedAt() != null ? legacy.getCreatedAt() : LocalDateTime.now())
                        .approvalStatus("APPROVED")
                        .remarks("Migrated from filesystem storage")
                        .isLatest(true)
                        .build();

                documentVersionRepository.save(version);

                dmsDocumentService.logActivity(master.getId(), master.getCurrentVersion(), "MIGRATION", "system", "Migrated legacy document " + legacy.getId() + " to LONGBLOB storage");

                migratedCount++;
            } catch (Exception e) {
                failedCount++;
                log.error("[DMS Migration] Failed to migrate document {}: {}", legacy.getId(), e.getMessage(), e);
            }
        }

        log.info("===============================================================");
        log.info("[DMS Migration] COMPLETED. Migrated: {} documents into LONGBLOBs (Failures: {})", migratedCount, failedCount);
        log.info("===============================================================");
    }

    private byte[] readPhysicalFileBytes(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) return null;

        String cleanPath = filePath.replaceAll("^/+", "").replaceAll("\\\\", "/");

        // 1. Try resolving in uploaded-documents directory
        if (cleanPath.startsWith("uploaded-documents/")) {
            String subName = cleanPath.substring("uploaded-documents/".length());
            Path p = Paths.get(uploadDir).resolve(subName);
            if (Files.exists(p) && Files.isReadable(p)) {
                try {
                    return Files.readAllBytes(p);
                } catch (IOException ignored) {}
            }
        }

        // 2. Try resolving via Spring ResourceLoader
        try {
            Resource res = resourceLoader.getResource("classpath:static/" + cleanPath);
            if (res.exists() && res.isReadable()) {
                return res.getInputStream().readAllBytes();
            }
        } catch (Exception ignored) {}

        // 3. Try resolving via direct file path
        try {
            Path directPath = Paths.get("src/main/resources/static/" + cleanPath);
            if (Files.exists(directPath) && Files.isReadable(directPath)) {
                return Files.readAllBytes(directPath);
            }
        } catch (Exception ignored) {}

        // 4. Try absolute or current dir file path
        try {
            Path fileP = Paths.get(cleanPath);
            if (Files.exists(fileP) && Files.isReadable(fileP)) {
                return Files.readAllBytes(fileP);
            }
        } catch (Exception ignored) {}

        return null;
    }

    private int[] parseVersion(String versionStr) {
        if (versionStr == null || versionStr.trim().isEmpty()) {
            return new int[]{1, 0};
        }
        String trimmed = versionStr.trim();
        String[] parts = trimmed.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return new int[]{major, minor};
        } catch (NumberFormatException e) {
            return new int[]{1, 0};
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "DOC";
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx + 1).toUpperCase(Locale.ROOT) : "DOC";
    }
}
