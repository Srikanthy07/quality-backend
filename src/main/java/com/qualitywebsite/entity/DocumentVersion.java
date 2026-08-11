package com.qualitywebsite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_version", indexes = {
    @Index(name = "idx_dv_master_latest", columnList = "document_master_id, is_latest"),
    @Index(name = "idx_dv_checksum", columnList = "checksum"),
    @Index(name = "idx_dv_uploaded_date", columnList = "uploaded_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * JPA Optimistic Locking token.
     * Protects concurrent approval/rejection/archival of the same version row.
     * Automatically managed by Hibernate — never set manually.
     */
    @Version
    @Column(name = "entity_version", nullable = false)
    private Long entityVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_master_id", nullable = false)
    @JsonIgnore
    private DocumentMaster documentMaster;

    @Column(name = "major_version", nullable = false)
    private Integer majorVersion;

    @Column(name = "minor_version", nullable = false)
    private Integer minorVersion;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType; // PDF, DOCX, XLSX, PPTX, etc.

    @Column(name = "mime_type")
    private String mimeType; // e.g. application/pdf, application/vnd.openxmlformats-officedocument.wordprocessingml.document

    @Column(name = "file_size")
    private Long fileSize;

    @Lob
    @Column(name = "file_data", columnDefinition = "LONGBLOB", nullable = false)
    @JsonIgnore
    private byte[] fileData;

    @Column(nullable = false)
    private String checksum; // SHA-256

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "uploaded_date")
    private LocalDateTime uploadedDate;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @Column(name = "approval_status")
    private String approvalStatus; // UNDER_REVIEW, APPROVED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "is_latest", nullable = false)
    @Builder.Default
    private Boolean isLatest = true;

    // Dynamically generated display version (Task 1: Removed redundant persistent "version" column)
    public String getVersion() {
        int maj = (majorVersion != null) ? majorVersion : 1;
        int min = (minorVersion != null) ? minorVersion : 0;
        return maj + "." + min;
    }

    @PrePersist
    public void onCreate() {
        if (this.uploadedDate == null) {
            this.uploadedDate = LocalDateTime.now();
        }
        if (this.approvalStatus == null) {
            this.approvalStatus = "UNDER_REVIEW";
        }
        if (this.majorVersion == null) {
            this.majorVersion = 1;
        }
        if (this.minorVersion == null) {
            this.minorVersion = 0;
        }
    }
}
