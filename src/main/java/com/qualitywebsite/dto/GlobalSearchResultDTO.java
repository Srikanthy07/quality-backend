package com.qualitywebsite.dto;

/**
 * Unified result item for Global Search.
 * Global Search covers exactly three sections: Master List, Generic
 * Templates, and Lessons Learned. Every result carries a `section` marker
 * so the frontend can group results correctly.
 *
 * - MASTER_LIST results populate processId/processName/processGroup/documentName.
 * - GENERIC_TEMPLATES and LESSONS_LEARNED results only populate documentName
 *   (processId/processName/processGroup are left null — those sections are
 *   not searched by process).
 *
 * pageUrl always points at the existing page/document the result should open;
 * Global Search never introduces new pages.
 */
public class GlobalSearchResultDTO {

    private String section; // "MASTER_LIST" | "GENERIC_TEMPLATES" | "LESSONS_LEARNED"
    private String processId;
    private String processName;
    private String processGroup;
    private String documentName;
    private String pageUrl;

    public GlobalSearchResultDTO() {
    }

    public GlobalSearchResultDTO(String section, String processId, String processName,
                                  String processGroup, String documentName, String pageUrl) {
        this.section = section;
        this.processId = processId;
        this.processName = processName;
        this.processGroup = processGroup;
        this.documentName = documentName;
        this.pageUrl = pageUrl;
    }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public String getProcessGroup() { return processGroup; }
    public void setProcessGroup(String processGroup) { this.processGroup = processGroup; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }
}