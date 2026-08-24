package com.qualitywebsite.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResponseDTO {

    private boolean success;
    private String message;
    private String details;
    private Long documentMasterId;
    private String documentCode;
    private String version;
    private String action; // CREATED, VERSIONED, REJECTED, DUPLICATE_PROMPT
    private boolean isDuplicateChecksum;
    private DocumentMasterDTO existingDocument;
}
