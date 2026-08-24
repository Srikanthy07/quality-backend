package com.qualitywebsite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.entity.MasterListItem;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import com.qualitywebsite.repository.MasterListItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializationService {

    private final DocumentRepository documentRepository;
    private final MasterListItemRepository masterListItemRepository;
    private final DocumentMasterRepository documentMasterRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final ActivityLogService activityLogService;

    private static final Set<String> SUPPORTED_DOC_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".url"
    );

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedInitialData() {
        try {
            int docsImported = seedDocuments();
            int masterItemsImported = seedMasterList();

            log.info("================ RECONCILIATION SUMMARY ================");
            log.info("Total Document Records Committed to MySQL : {}", docsImported);
            log.info("Total Master List Items Committed to MySQL: {}", masterItemsImported);
            log.info("Data Initialization & Reconciliation completed successfully.");
            log.info("========================================================");
        } catch (Exception e) {
            log.error("Failed to complete data initialization and reconciliation", e);
        }
    }

    public int seedDocuments() {
        log.info("Starting complete physical document reconciliation and database seeding...");

        Map<String, Map<String, Object>> metadataByPath = loadDocumentMetadata();
        List<Path> physicalFiles = findPhysicalDocumentFiles();

        // Load ALL existing DB records ONCE into a normalised-path → entity Map.
        // This replaces the previous per-file documentRepository.findAll() call (N+1 queries).
        // It is also used to skip files that already have a DB record so that:
        //   (a) soft-deleted (isActive=false) documents are NOT resurrected on restart, and
        //   (b) unchanged active records are not unnecessarily re-saved.
        Map<String, DocumentEntity> existingByPath = documentRepository.findAll().stream()
                .filter(d -> d.getFilePath() != null)
                .collect(Collectors.toMap(
                        d -> normalizeFilePath(d.getFilePath()),
                        d -> d,
                        (a, b) -> a  // keep first on duplicate normalised path
                ));

        List<DocumentEntity> documentsToSave = new ArrayList<>();
        List<String> skippedFiles = new ArrayList<>();
        Map<String, List<String>> duplicateReasons = new LinkedHashMap<>();

        int totalUnsupportedFiles = 0;
        int totalFailures = 0;
        int totalSkippedExisting = 0;
        Set<String> processedPaths = new HashSet<>();

        for (Path filePath : physicalFiles) {
            try {
                String relativeFilePath = getRelativeDocumentPath(filePath);
                String normalizedFilePath = normalizeFilePath(relativeFilePath);

                if (!processedPaths.add(normalizedFilePath)) {
                    duplicateReasons.computeIfAbsent(normalizedFilePath, k -> new ArrayList<>())
                            .add("Duplicate physical file path detected");
                    continue;
                }

                // If a DB record already exists for this file path, skip it entirely.
                // This preserves the isActive=false state of soft-deleted documents and
                // avoids overwriting metadata that was already correctly set.
                if (existingByPath.containsKey(normalizedFilePath)) {
                    totalSkippedExisting++;
                    DocumentEntity existingDoc = existingByPath.get(normalizedFilePath);
                    Map<String, Object> metadata = metadataByPath.get(normalizedFilePath);
                    if (metadata != null && metadata.get("version") != null) {
                        String metaVer = (String) metadata.get("version");
                        if (!metaVer.equals(existingDoc.getVersion())) {
                            log.info("[Version Reconciliation] Updating version for {} from {} to {}", existingDoc.getDocumentName(), existingDoc.getVersion(), metaVer);
                            existingDoc.setVersion(metaVer);
                            documentRepository.save(existingDoc);

                            Optional<DocumentMaster> masterOpt = documentMasterRepository
                                    .findByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase(
                                            existingDoc.getProcess(), existingDoc.getCategory(), existingDoc.getDocumentName()
                                    );
                            if (masterOpt.isPresent()) {
                                DocumentMaster master = masterOpt.get();
                                if (!"ARCHIVED".equalsIgnoreCase(master.getStatus()) && !"DELETED".equalsIgnoreCase(master.getStatus())) {
                                    master.setCurrentVersion(metaVer);
                                    documentMasterRepository.save(master);

                                    int[] parts = parseVersion(metaVer);
                                    Optional<DocumentVersion> latestOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(master.getId());
                                    if (latestOpt.isPresent()) {
                                        DocumentVersion dv = latestOpt.get();
                                        dv.setMajorVersion(parts[0]);
                                        dv.setMinorVersion(parts[1]);
                                        documentVersionRepository.save(dv);
                                    }
                                }
                            }
                        }
                    }
                    continue;
                }

                Map<String, Object> metadata = metadataByPath.get(normalizedFilePath);
                DocumentEntity entity = buildDocumentEntity(filePath, normalizedFilePath, metadata, null);
                documentsToSave.add(entity);
            } catch (Exception e) {
                totalFailures++;
                String message = String.format("Failed to process %s: %s", filePath, e.getMessage());
                log.warn(message, e);
                skippedFiles.add(message);
            }
        }

        try {
            totalUnsupportedFiles = countUnsupportedFiles();
        } catch (Exception e) {
            log.warn("Unable to compute unsupported document file count", e);
        }

        List<DocumentEntity> savedDocuments = documentRepository.saveAll(documentsToSave);
        log.info("Successfully saved {} new document entities into MySQL.", savedDocuments.size());

        log.info("================ DOCUMENT RECONCILIATION REPORT ================");
        log.info("Total physical document files       : {}", physicalFiles.size());
        log.info("Total existing DB records (skipped) : {}", totalSkippedExisting);
        log.info("Total new records imported          : {}", savedDocuments.size());
        log.info("Total duplicates skipped           : {}", duplicateReasons.values().stream().mapToInt(List::size).sum());
        log.info("Total unsupported files            : {}", totalUnsupportedFiles);
        log.info("Total failures                     : {}", totalFailures);

        if (!duplicateReasons.isEmpty()) {
            duplicateReasons.forEach((path, reasons) -> log.warn("Duplicate ignored for {}: {}", path, String.join("; ", reasons)));
        }
        if (!skippedFiles.isEmpty()) {
            skippedFiles.forEach(reason -> log.warn("Skipped: {}", reason));
        }
        log.info("===============================================================");

        try {
            activityLogService.logActivity("system", "Data Migration", "Reconciled static document registries — added " + savedDocuments.size() + " new records");
        } catch (Exception e) {
            log.warn("Could not log activity during data initialization", e);
        }

        return savedDocuments.size();
    }

    private int countUnsupportedFiles() {
        Path root = getDocumentsRoot();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return 0;
        }
        try {
            return (int) Files.walk(root)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> !SUPPORTED_DOC_EXTENSIONS.contains(getExtension(name)))
                    .count();
        } catch (IOException e) {
            log.warn("Unable to count unsupported document files", e);
            return 0;
        }
    }

    private Map<String, Map<String, Object>> loadDocumentMetadata() {
        Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();
        loadJsonMetadataFile("classpath:static/data/documents.json", metadata);
        loadJsonMetadataFile("classpath:static/data/generic-templates.json", metadata);
        loadJsonMetadataFile("classpath:static/data/lessons-learned.json", metadata);
        return metadata;
    }

    private void loadJsonMetadataFile(String resourcePath, Map<String, Map<String, Object>> metadata) {
        try {
            Resource res = resourceLoader.getResource(resourcePath);
            if (!res.exists()) {
                log.warn("Resource {} does not exist.", resourcePath);
                return;
            }
            List<Map<String, Object>> docs = objectMapper.readValue(res.getInputStream(), new TypeReference<>() {});
            log.info("Found {} JSON records in {}", docs.size(), resourcePath);
            for (Map<String, Object> doc : docs) {
                Object filePathObj = doc.get("filePath");
                if (filePathObj instanceof String filePath && !filePath.trim().isEmpty()) {
                    String normalized = normalizeFilePath(filePath);
                    if (metadata.containsKey(normalized)) {
                        log.warn("Duplicate metadata entry for path {} found in {}. Keeping first occurrence.", normalized, resourcePath);
                        continue;
                    }
                    metadata.put(normalized, doc);
                } else {
                    log.warn("Skipping JSON record with missing filePath in {}", resourcePath);
                }
            }
        } catch (Exception e) {
            log.error("Exception occurred while parsing {}", resourcePath, e);
        }
    }

    private List<Path> findPhysicalDocumentFiles() {
        try {
            Path root = getDocumentsRoot();
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                log.warn("Document root {} does not exist or is not a directory.", root);
                return Collections.emptyList();
            }

            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> SUPPORTED_DOC_EXTENSIONS.contains(path.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("^.*\\.", ".")))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Unable to scan physical document files", e);
            return Collections.emptyList();
        }
    }

    private Path getDocumentsRoot() {
        try {
            Resource res = resourceLoader.getResource("classpath:static/documents");
            if (res.exists()) {
                try {
                    Path path = Paths.get(res.getURI());
                    if (Files.isDirectory(path)) {
                        return path;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return Paths.get("src/main/resources/static/documents");
    }

    private DocumentEntity buildDocumentEntity(Path filePath, String normalizedFilePath, Map<String, Object> metadata, DocumentEntity existing) throws IOException {
        String fileName = filePath.getFileName().toString();
        String extension = getExtension(fileName).toUpperCase(Locale.ROOT);
        String category = determineCategory(normalizedFilePath, metadata);
        String process = determineProcess(normalizedFilePath, metadata, category);
        String processGroup = determineProcessGroup(category, process);
        String documentName = determineDocumentName(fileName, metadata, category);
        String version = determineVersion(fileName, metadata);
        String description = metadata != null ? (String) metadata.getOrDefault("description", null) : null;
        String id = determineDocumentId(existing, metadata, process, category, fileName);

        DocumentEntity.DocumentEntityBuilder builder = DocumentEntity.builder()
                .id(id)
                .documentName(documentName)
                .category(category)
                .processGroup(processGroup)
                .process(process)
                .version(version)
                .description(description)
                .filePath(normalizedFilePath)
                .fileName(fileName)
                .fileType(extension)
                .fileSize(Files.size(filePath))
                .isActive(true);

        if (existing != null) {
            builder.createdAt(existing.getCreatedAt())
                   .createdBy(existing.getCreatedBy())
                   .updatedAt(existing.getUpdatedAt())
                   .updatedBy(existing.getUpdatedBy());
        }

        return builder.build();
    }

    private String determineDocumentId(DocumentEntity existing, Map<String, Object> metadata, String process, String category, String fileName) {
        if (existing != null && existing.getId() != null && !existing.getId().trim().isEmpty()) {
            return existing.getId();
        }
        if (metadata != null) {
            Object idObj = metadata.get("id");
            if (idObj instanceof String id && !id.trim().isEmpty()) {
                return id;
            }
        }
        return generateDocumentId(process, category, fileName);
    }

    private String determineCategory(String normalizedFilePath, Map<String, Object> metadata) {
        if (metadata != null) {
            Object catObj = metadata.get("category");
            if (catObj instanceof String cat && !cat.trim().isEmpty()) {
                String normalizedCat = cat.trim().toLowerCase(Locale.ROOT);
                if ("aspice_assessment_checklist".equals(normalizedCat) || "supporting".equals(normalizedCat) || "aspice assessment checklist".equals(normalizedCat)) {
                    return "Assessment Checklist";
                }
                if ("generic templates".equals(normalizedCat) || "generic_template".equals(normalizedCat) || "generic template".equals(normalizedCat)) {
                    return "Generic Templates";
                }
                if ("lessons learned".equals(normalizedCat) || "lessons-learned".equals(normalizedCat) || "lessons_learned".equals(normalizedCat)) {
                    return "Lessons Learned";
                }
            }
        }

        String path = normalizedFilePath.toLowerCase(Locale.ROOT);
        if (path.startsWith("documents/generic-template")) {
            return "Generic Templates";
        }
        if (path.startsWith("documents/lessons-learned")) {
            return "Lessons Learned";
        }
        if (path.contains("/aspice_assessment_checklist/") || path.contains("/aspice_assessment_checklist") || path.contains("/assessment_checklist")) {
            return "Assessment Checklist";
        }
        if (path.startsWith("documents/aspice-prm")) {
            return "ASPICE PRM";
        }
        return "ASPICE PRM";
    }

    private String determineProcess(String normalizedFilePath, Map<String, Object> metadata, String category) {
        if (metadata != null) {
            Object procObj = metadata.get("process");
            if (procObj instanceof String proc && !proc.trim().isEmpty()) {
                return proc.trim();
            }
        }

        if ("Generic Templates".equals(category)) {
            return "GENERIC";
        }
        if ("Lessons Learned".equals(category)) {
            return "LL";
        }

        String[] tokens = normalizedFilePath.split("/");
        for (int i = 0; i < tokens.length; i++) {
            if ("aspice-prm".equals(tokens[i]) && i + 2 < tokens.length) {
                String group = tokens[i + 1].toUpperCase(Locale.ROOT);
                String processFolder = tokens[i + 2].toUpperCase(Locale.ROOT);
                String letters = processFolder.replaceAll("[^A-Z]", "");
                String digits = processFolder.replaceAll("[^0-9]", "");
                if (!letters.isEmpty() && !digits.isEmpty()) {
                    return letters + "." + digits;
                }
                if (!processFolder.isEmpty()) {
                    return processFolder;
                }
            }
        }
        return "GLOBAL";
    }

    private String determineProcessGroup(String category, String processId) {
        if ("Generic Templates".equals(category)) {
            return "Generic Templates";
        }
        if ("Lessons Learned".equals(category)) {
            return "Lessons Learned";
        }
        if ("Assessment Checklist".equals(category)) {
            return "Supporting Process Group";
        }
        return getProcessGroup(processId);
    }

    private String determineDocumentName(String fileName, Map<String, Object> metadata, String category) {
        if (metadata != null) {
            Object nameObj = metadata.get("documentName");
            if (nameObj instanceof String name && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        String name = removeExtension(fileName).replaceAll("[_]+", " ").replaceAll("\\s+", " ").trim();
        name = name.replaceAll("(?i)^\\d+[\\s-_.]*", "").trim();
        name = name.replaceAll("(?i)\\bReviewed\\b", "").trim();
        if (name.isEmpty()) {
            if ("Generic Templates".equals(category)) {
                return "Generic Template";
            }
            if ("Lessons Learned".equals(category)) {
                return "Lessons Learned";
            }
            if ("Assessment Checklist".equals(category)) {
                return "Assessment Checklist";
            }
            return "Document";
        }
        return name;
    }

    private String determineVersion(String fileName, Map<String, Object> metadata) {
        if (metadata != null) {
            Object versionObj = metadata.get("version");
            if (versionObj instanceof String version && !version.trim().isEmpty()) {
                return version.trim();
            }
        }
        String baseName = removeExtension(fileName);
        String versionFromName = null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:_v|_)?(\\d+\\.\\d+|\\d+)(?:_Reviewed)?$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(baseName);
        if (matcher.find()) {
            versionFromName = matcher.group(1);
        }
        return versionFromName != null ? versionFromName : "1.0";
    }

    private String generateDocumentId(String process, String category, String fileName) {
        String prefix;
        if (process != null && !process.trim().isEmpty() && !"GENERIC".equalsIgnoreCase(process) && !"LL".equalsIgnoreCase(process)) {
            prefix = process.replace(".", "").toUpperCase(Locale.ROOT);
        } else if ("Generic Templates".equals(category)) {
            prefix = "GEN";
        } else if ("Lessons Learned".equals(category)) {
            prefix = "LL";
        } else if ("Assessment Checklist".equals(category)) {
            prefix = "CHK";
        } else {
            prefix = "DOC";
        }
        String slug = removeExtension(fileName).replaceAll("[^A-Za-z0-9]+", "").toUpperCase(Locale.ROOT);
        if (slug.length() > 12) {
            slug = slug.substring(0, 12);
        }
        String candidate = prefix + "-" + slug;
        int suffix = 1;
        while (documentRepository.existsById(candidate)) {
            candidate = prefix + "-" + slug + "-" + suffix++;
        }
        return candidate;
    }

    private String normalizeFilePath(String path) {
        if (path == null) return "";
        return path.replaceAll("^/+", "")
                .replaceAll("\\\\", "/")
                .replaceAll("/+", "/")
                .toLowerCase(Locale.ROOT);
    }

    private String getRelativeDocumentPath(Path filePath) {
        Path root = getDocumentsRoot();
        Path relative = root.relativize(filePath);
        return Paths.get("documents").resolve(relative).toString().replaceAll("\\\\", "/");
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx).toLowerCase(Locale.ROOT) : "";
    }

    private String removeExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(0, idx) : fileName;
    }

    public int seedMasterList() {
        long existingCount = masterListItemRepository.count();
        if (existingCount > 0) {
            log.info("Master list repository already contains {} records. Skipping master list seeding.", existingCount);
            return (int) existingCount;
        }

        log.info("Seeding master list items from documents.json and prm-documents.csv...");
        List<MasterListItem> itemsToSave = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();
        int csvRowsDetected = 0;
        int rowsSkipped = 0;
        int snoCounter = 1;

        // 1. Load primary ASPICE PRM documents from documents.json (51 templates)
        try {
            Resource res = resourceLoader.getResource("classpath:static/data/documents.json");
            if (res.exists()) {
                List<Map<String, Object>> docs = objectMapper.readValue(res.getInputStream(), new TypeReference<>() {});
                for (Map<String, Object> map : docs) {
                    String cat = (String) map.get("category");
                    // Skip Assessment Checklists
                    if ("aspice_assessment_checklist".equalsIgnoreCase(cat) || "supporting".equalsIgnoreCase(cat) || "ASPICE_Assessment_Checklist".equalsIgnoreCase(cat)) {
                        continue;
                    }

                    String id = (String) map.get("id");
                    String documentName = (String) map.get("documentName");
                    String process = (String) map.get("process");
                    String version = (String) map.get("version");

                    String cleanTplName = documentName != null 
                        ? documentName.replaceAll("(?i)^(SYS|SWE|SUP|MAN|PIM|SPL|HWE|MLE)\\.\\d+\\s*", "").trim()
                        : "Document Template";

                    MasterListItem item = MasterListItem.builder()
                            .sNo(snoCounter++)
                            .processGroup(getProcessGroup(process))
                            .processId(process != null ? process : "GLOBAL")
                            .processName(getAspiceProcessName(process))
                            .templateName(cleanTplName)
                            .version(version != null ? version : "1.0")
                            .docId(id)
                            .build();

                    itemsToSave.add(item);
                    processedKeys.add((process + ":" + cleanTplName).toLowerCase());
                }
                log.info("Extracted {} Master List items from documents.json", itemsToSave.size());
            }
        } catch (Exception e) {
            log.error("Exception occurred while reading documents.json for master list", e);
        }

        // 2. Read prm-documents.csv to capture any additional CSV rows not in documents.json
        try {
            Resource res = resourceLoader.getResource("classpath:static/data/prm-documents.csv");
            if (res.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    boolean firstLine = true;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        csvRowsDetected++;
                        if (firstLine) {
                            firstLine = false;
                            if (line.toLowerCase().contains("s.no") || line.toLowerCase().contains("process")) {
                                continue;
                            }
                        }
                        String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                        if (parts.length >= 4) {
                            String procId = cleanCsvCell(parts[0]);
                            String procName = cleanCsvCell(parts[1]);
                            String tplName = cleanCsvCell(parts[2]);
                            String ver = cleanCsvCell(parts[3]);

                            String key = (procId + ":" + tplName).toLowerCase();
                            if (!processedKeys.contains(key)) {
                                MasterListItem item = MasterListItem.builder()
                                        .sNo(snoCounter++)
                                        .processGroup(getProcessGroup(procId))
                                        .processId(procId)
                                        .processName(procName != null && !procName.isEmpty() ? procName : getAspiceProcessName(procId))
                                        .templateName(tplName)
                                        .version(ver != null && !ver.isEmpty() ? ver : "1.0")
                                        .build();
                                itemsToSave.add(item);
                                processedKeys.add(key);
                            } else {
                                rowsSkipped++;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Exception occurred while parsing prm-documents.csv", e);
        }

        log.info("Total CSV rows detected     : {}", csvRowsDetected);
        log.info("Rows skipped (duplicates)   : {}", rowsSkipped);
        
        List<MasterListItem> savedItems = masterListItemRepository.saveAll(itemsToSave);
        log.info("Rows successfully imported into MySQL: {}", savedItems.size());
        log.info("Final database master_list_items count: {}", masterListItemRepository.count());
        return savedItems.size();
    }

    private String normalizeFilePath11(String path) {
        if (path == null) return "";
        return path.replaceAll("^/+", "").replaceAll("\\\\", "/").toLowerCase();
    }

    private String cleanCsvCell(String val) {
        if (val == null) return "";
        String trimmed = val.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.trim();
    }

    private String getProcessGroup(String processId) {
        if (processId == null) return "Other";
        String p = processId.toUpperCase().trim();
        if (p.startsWith("SYS")) return "System Engineering Process Group";
        if (p.startsWith("SWE")) return "Software Engineering Process Group";
        if (p.startsWith("HWE")) return "Hardware Engineering Process Group";
        if (p.startsWith("MLE")) return "ML Engineering Process Group";
        if (p.startsWith("SUP")) return "Supporting Process Group";
        if (p.startsWith("MAN")) return "Management Process Group";
        if (p.startsWith("PIM")) return "Process Improvement Group";
        if (p.startsWith("VAL")) return "Validation Process Group";
        if (p.startsWith("REU")) return "Reuse Process Group";
        if (p.startsWith("ACQ")) return "Acquisition Process Group";
        if (p.startsWith("SPL")) return "Supply Process Group";
        return "Supporting Process Group";
    }

    private String getAspiceProcessName(String processId) {
        if (processId == null) return "ASPICE Process";
        String pid = processId.toUpperCase().trim();
        return switch (pid) {
            case "SYS.1" -> "Requirements Elicitation";
            case "SYS.2" -> "System Requirements Analysis";
            case "SYS.3" -> "System Architectural Design";
            case "SYS.4" -> "System Integration & Verification";
            case "SYS.5" -> "System Verification";
            case "SWE.1" -> "SW Requirements Analysis";
            case "SWE.2" -> "SW Architectural Design";
            case "SWE.3" -> "SW Detailed Design & Unit Construction";
            case "SWE.4" -> "Software Unit Verification";
            case "SWE.5" -> "Software Integration & Verification";
            case "SWE.6" -> "Software Verification";
            case "SUP.1" -> "Quality Assurance";
            case "SUP.2" -> "Verification";
            case "SUP.4" -> "Joint Review";
            case "SUP.7" -> "Documentation";
            case "SUP.8" -> "Configuration Management";
            case "SUP.9" -> "Problem Resolution Management";
            case "SUP.10" -> "Change Request Management";
            case "MAN.3" -> "Project Management";
            case "MAN.5" -> "Risk Management";
            case "MAN.6" -> "Measurement";
            case "PIM.3" -> "Process Improvement";
            default -> processId;
        };
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
}
