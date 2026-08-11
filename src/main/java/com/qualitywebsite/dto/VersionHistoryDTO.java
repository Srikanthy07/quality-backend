package com.qualitywebsite.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VersionHistoryDTO {

    private Long versionId;
    private Long documentMasterId;
    private String version; // Dynamically generated major.minor
    private Integer majorVersion;
    private Integer minorVersion;
    private String fileName;
    private String fileType;
    private String mimeType;
    private Long fileSize;
    private String checksum;
    private String uploadedBy;
    private LocalDateTime uploadedDate;
    private String approvedBy;
    private LocalDateTime approvedDate;
    private String approvalStatus;
    private String remarks;
    private Boolean isLatest;
    private String downloadUrl;
}
