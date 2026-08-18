package com.qualitywebsite.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMasterDTO {

    private Long id;

    /**
     * JPA optimistic lock token — returned by every GET, must be sent back
     * in every PUT so the server can detect stale (concurrent) edits via
     * ObjectOptimisticLockingFailureException at flush time.
     */
    private Long entityVersion;

    private String documentCode;
    private String processId;
    private String processName;
    private String processGroup;
    private String category;
    private String documentName;
    private String description;
    private String currentVersion;
    private String status;
    private String createdBy;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    // Latest version details
    private Long latestVersionId;
    private String fileName;
    private String fileType;
    private String mimeType;
    private Long fileSize;
    private String checksum;
    private String downloadUrl;
}
