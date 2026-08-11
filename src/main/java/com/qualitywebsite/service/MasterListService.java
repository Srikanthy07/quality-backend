package com.qualitywebsite.service;

import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.MasterListItem;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.MasterListItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class MasterListService {

    private static final String ASPICE_PRM_CATEGORY = "ASPICE PRM";

    private final MasterListItemRepository masterListItemRepository;
    private final DocumentRepository documentRepository;
    private final ActivityLogService activityLogService;

    public List<MasterListItem> getAllItems() {
        AtomicInteger sNo = new AtomicInteger(1);
        return documentRepository.findByCategoryIgnoreCaseAndIsActiveTrue(ASPICE_PRM_CATEGORY).stream()
                .sorted(masterListDocumentComparator())
                .map(document -> toMasterListItem(document, sNo.getAndIncrement()))
                .toList();
    }

    public Optional<MasterListItem> getItemById(Long id) {
        return masterListItemRepository.findById(id);
    }

    /**
     * Global Search backing method.
     * Searches ONLY the Master List (Process ID, Process Name, Process Group,
     * Document Name) — case-insensitive, partial, multi-word. Every query
     * word must be found somewhere across those four fields for a Master
     * List row to be considered a match. No document descriptions, metadata,
     * file contents, or other website content is searched.
     */
    public List<com.qualitywebsite.dto.MasterListSearchResultDTO> searchMasterList(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String[] tokens = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<com.qualitywebsite.dto.MasterListSearchResultDTO> results = new java.util.ArrayList<>();

        // 1. Search Master List items (ASPICE PRM)
        for (MasterListItem item : getAllItems()) {
            if (matchesAllTokens(item, tokens)) {
                results.add(com.qualitywebsite.dto.MasterListSearchResultDTO.builder()
                        .processId(item.getProcessId())
                        .processName(item.getProcessName())
                        .processGroup(item.getProcessGroup())
                        .category("ASPICE PRM")
                        .documentName(item.getTemplateName())
                        .version(item.getVersion())
                        .pageUrl(buildPageUrl(item))
                        .build());
            }
        }

        // 2. Search Other Active Documents (Generic Templates, Lessons Learned, Assessment Checklist)
        for (DocumentEntity doc : documentRepository.findAllByIsActiveTrue()) {
            if (ASPICE_PRM_CATEGORY.equalsIgnoreCase(doc.getCategory())) continue;
            if (matchesDocTokens(doc, tokens)) {
                results.add(com.qualitywebsite.dto.MasterListSearchResultDTO.builder()
                        .processId(nullToEmpty(doc.getProcess()))
                        .processName(doc.getProcessGroup() != null ? doc.getProcessGroup() : doc.getCategory())
                        .processGroup(doc.getProcessGroup() != null ? doc.getProcessGroup() : doc.getCategory())
                        .category(doc.getCategory())
                        .documentName(doc.getDocumentName())
                        .version(doc.getVersion() != null ? doc.getVersion() : "1.0")
                        .pageUrl(deriveDocPageUrl(doc))
                        .build());
            }
        }

        return results;
    }

    private boolean matchesDocTokens(DocumentEntity doc, String[] tokens) {
        String haystack = (nullToEmpty(doc.getProcess()) + " "
                + nullToEmpty(doc.getProcessGroup()) + " "
                + nullToEmpty(doc.getCategory()) + " "
                + nullToEmpty(doc.getDocumentName()) + " "
                + nullToEmpty(doc.getDescription()) + " "
                + nullToEmpty(doc.getFileName()))
                .toLowerCase(Locale.ROOT);

        for (String token : tokens) {
            if (!token.isEmpty() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAllTokens(MasterListItem item, String[] tokens) {
        String haystack = (nullToEmpty(item.getProcessId()) + " "
                + nullToEmpty(item.getProcessName()) + " "
                + nullToEmpty(item.getProcessGroup()) + " "
                + nullToEmpty(item.getTemplateName()))
                .toLowerCase(Locale.ROOT);

        for (String token : tokens) {
            if (!token.isEmpty() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String deriveDocPageUrl(DocumentEntity doc) {
        if ("Generic Templates".equalsIgnoreCase(doc.getCategory())) return "/generic-template.html";
        if ("Lessons Learned".equalsIgnoreCase(doc.getCategory())) return "/lessons-learned.html";
        if ("Assessment Checklist".equalsIgnoreCase(doc.getCategory())) return "/quality-checks.html";
        return "/section-details.html?section=" + java.net.URLEncoder.encode(nullToEmpty(doc.getProcess()), java.nio.charset.StandardCharsets.UTF_8);
    }

    private com.qualitywebsite.dto.MasterListSearchResultDTO toSearchResultDTO(MasterListItem item) {
        return com.qualitywebsite.dto.MasterListSearchResultDTO.builder()
                .processId(item.getProcessId())
                .processName(item.getProcessName())
                .processGroup(item.getProcessGroup())
                .category("ASPICE PRM")
                .documentName(item.getTemplateName())
                .version(item.getVersion())
                .pageUrl(buildPageUrl(item))
                .build();
    }

    // Reuses the exact navigation pattern already used by master-list.html so
    // Global Search results open the existing process subsection/page —
    // never a new details page.
    private String buildPageUrl(MasterListItem item) {
        StringBuilder url = new StringBuilder("/section-details.html?section=")
                .append(java.net.URLEncoder.encode(nullToEmpty(item.getProcessId()), java.nio.charset.StandardCharsets.UTF_8));
        if (item.getDocId() != null && !item.getDocId().isBlank()) {
            url.append("&doc=").append(java.net.URLEncoder.encode(item.getDocId(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    public MasterListItem saveItem(MasterListItem item, String username) {
        boolean isNew = item.getId() == null;
        MasterListItem saved = masterListItemRepository.save(item);
        String action = isNew ? "Added Master List Item" : "Updated Master List Item";
        activityLogService.logActivity(username, action, "Process: " + item.getProcessId() + " - " + item.getTemplateName());
        return saved;
    }

    public boolean deleteItem(Long id, String username) {
        Optional<MasterListItem> opt = masterListItemRepository.findById(id);
        if (opt.isPresent()) {
            MasterListItem item = opt.get();
            masterListItemRepository.deleteById(id);
            activityLogService.logActivity(username, "Deleted Master List Item", "Process: " + item.getProcessId() + " - " + item.getTemplateName());
            return true;
        }
        return false;
    }

    private MasterListItem toMasterListItem(DocumentEntity document, int sNo) {
        return MasterListItem.builder()
                .id((long) sNo)
                .sNo(sNo)
                .processGroup(document.getProcessGroup())
                .processId(document.getProcess())
                .processName(getAspiceProcessName(document.getProcess()))
                .templateName(document.getDocumentName())
                .version(document.getVersion())
                .docId(document.getId())
                .description(document.getDescription())
                .fileName(document.getFileName())
                .downloadUrl(normalizeDownloadUrl(document.getFilePath()))
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private Comparator<DocumentEntity> masterListDocumentComparator() {
        return Comparator
                .comparingInt((DocumentEntity document) -> processGroupOrder(document.getProcess()))
                .thenComparingInt(document -> processNumber(document.getProcess()))
                .thenComparing(document -> safeUpper(document.getDocumentName()))
                .thenComparing(document -> safeUpper(document.getId()));
    }

    private int processGroupOrder(String processId) {
        String prefix = processPrefix(processId);
        return switch (prefix) {
            case "SYS" -> 1;
            case "SWE" -> 2;
            case "SUP" -> 3;
            case "MAN" -> 4;
            case "PIM" -> 5;
            case "SPL" -> 6;
            case "HWE" -> 7;
            case "MLE" -> 8;
            default -> 99;
        };
    }

    private int processNumber(String processId) {
        if (processId == null) {
            return Integer.MAX_VALUE;
        }
        String digits = processId.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private String processPrefix(String processId) {
        if (processId == null) {
            return "";
        }
        int dotIndex = processId.indexOf('.');
        String prefix = dotIndex >= 0 ? processId.substring(0, dotIndex) : processId;
        return prefix.trim().toUpperCase(Locale.ROOT);
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDownloadUrl(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        String normalized = filePath.trim().replace("\\", "/");
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String getAspiceProcessName(String processId) {
        if (processId == null) return "ASPICE Process";
        String pid = processId.toUpperCase(Locale.ROOT).trim();
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
}