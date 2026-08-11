package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dms_migration_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmsMigrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_master_id")
    private Long documentMasterId;

    private String version;

    @Column(nullable = false)
    private String action; // MIGRATION, UPLOAD, UPDATE, DELETE, DOWNLOAD, APPROVE, REJECT, ARCHIVE

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "performed_date", nullable = false)
    private LocalDateTime performedDate;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @PrePersist
    public void onCreate() {
        if (this.performedDate == null) {
            this.performedDate = LocalDateTime.now();
        }
    }
}
