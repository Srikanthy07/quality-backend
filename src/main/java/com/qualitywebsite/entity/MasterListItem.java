package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "master_list_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "s_no")
    private Integer sNo;

    @Column(name = "process_group")
    private String processGroup;

    @Column(name = "process_id")
    private String processId;

    @Column(name = "process_name")
    private String processName;

    @Column(name = "template_name")
    private String templateName;

    private String version;

    @Column(name = "doc_id")
    private String docId;

    @Transient
    private String description;

    @Transient
    private String fileName;

    @Transient
    private String downloadUrl;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
