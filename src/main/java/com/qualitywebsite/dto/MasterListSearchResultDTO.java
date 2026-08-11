package com.qualitywebsite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterListSearchResultDTO {
    private String processId;
    private String processName;
    private String processGroup;
    private String category;
    private String documentName;
    private String version;
    private String pageUrl;

    public MasterListSearchResultDTO(String processId, String processName, String processGroup, String documentName, String pageUrl) {
        this.processId = processId;
        this.processName = processName;
        this.processGroup = processGroup;
        this.category = "ASPICE PRM";
        this.documentName = documentName;
        this.version = "1.0";
        this.pageUrl = pageUrl;
    }
}