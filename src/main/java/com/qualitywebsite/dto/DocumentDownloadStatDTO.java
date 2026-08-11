package com.qualitywebsite.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDownloadStatDTO {
    private Long documentId;
    private String documentName;
    private String category;
    private long downloads;
    private LocalDateTime latestDownload;
}
