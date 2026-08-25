package com.qualitywebsite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReconciliationItemDTO {
    private String documentCode;
    private String processGroup;
    private String processName;
    private String documentName;
    private String version;
    private Long masterId;
    private Long versionId;
    private String checksum;
    private String masterStatus;
    private String versionStatus;
    private String reconciliationResult; // UNCHANGED, STATUS_CORRECTED, VERSION_CORRECTED, CHECKSUM_CORRECTED, ARCHIVE_CREATED, SKIPPED, CONFLICT_REQUIRES_REVIEW
    private String details;
}
