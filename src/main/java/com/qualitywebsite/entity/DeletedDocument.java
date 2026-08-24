package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "deleted_documents", indexes = {
    @Index(name = "idx_del_doc_checksum", columnList = "checksum"),
    @Index(name = "idx_del_doc_cat", columnList = "category"),
    @Index(name = "idx_del_doc_orig_id", columnList = "original_master_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_master_id")
    private Long originalMasterId;

    @Column(name = "document_code")
    private String documentCode;

    @Column(name = "process_id")
    private String processId;

    @Column(name = "process_name")
    private String processName;

    @Column(name = "process_group")
    private String processGroup;

    @Column(nullable = false)
    private String category;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "current_version")
    private String currentVersion;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Lob
    @Column(name = "file_data", columnDefinition = "LONGBLOB")
    private byte[] fileData;

    private String checksum;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "deleted_date")
    private LocalDateTime deletedDate;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @PrePersist
    public void onCreate() {
        if (this.deletedDate == null) {
            this.deletedDate = LocalDateTime.now();
        }
    }
}
