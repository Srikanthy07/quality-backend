package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_download_log", indexes = {
    @Index(name = "idx_ddl_download_time", columnList = "download_time"),
    @Index(name = "idx_ddl_document_id", columnList = "document_id"),
    @Index(name = "idx_ddl_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "category")
    private String category;

    @Column(name = "visitor_id")
    private String visitorId;

    @Column(name = "download_time", nullable = false)
    private LocalDateTime downloadTime;

    @PrePersist
    public void onCreate() {
        if (this.downloadTime == null) {
            this.downloadTime = LocalDateTime.now();
        }
    }
}
