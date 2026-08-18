package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "document_master", indexes = {
    @Index(name = "idx_dm_category_status", columnList = "category, status"),
    @Index(name = "idx_dm_status_updated", columnList = "status, updated_date"),
    @Index(name = "idx_dm_proc_cat_name", columnList = "process_id, category, document_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * JPA Optimistic Locking token.
     * Automatically incremented by Hibernate on every successful UPDATE.
     * Never set or compare this manually — JPA manages it via the
     * UPDATE ... WHERE entity_version = ? clause at flush time.
     */
    @Version
    @Column(name = "entity_version", nullable = false)
    private Long entityVersion;

    @Column(name = "document_code", nullable = false, unique = true)
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

    @Column(name = "current_version", nullable = false)
    private String currentVersion;

    @Column(nullable = false)
    private String status; // DRAFT, UNDER_REVIEW, APPROVED, REJECTED, OBSOLETE, ARCHIVED

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @OneToMany(mappedBy = "documentMaster", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DocumentVersion> versions = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        if (this.createdDate == null) {
            this.createdDate = LocalDateTime.now();
        }
        this.updatedDate = LocalDateTime.now();
        if (this.currentVersion == null) {
            this.currentVersion = "1.0";
        }
        if (this.status == null) {
            this.status = "UNDER_REVIEW"; // Task 2: New uploads require review & approval
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedDate = LocalDateTime.now();
    }
}
