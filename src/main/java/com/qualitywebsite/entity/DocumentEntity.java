package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "documents")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    @Id
    private String id; // e.g. SYS2-001 or generated UUID/slug

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(nullable = false)
    private String category; // ASPICE PRM, Generic Templates, Lessons Learned, Assessment Checklist

    @Column(name = "process_group")
    private String processGroup;

    @Column(nullable = false)
    private String process; // e.g. SYS.2, LL, GENERIC, etc.

    @Column(nullable = false)
    private String version;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType; // PDF, DOCX, XLSX, etc.

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
