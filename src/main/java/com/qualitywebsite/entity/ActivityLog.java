package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs", indexes = {
    @Index(name = "idx_act_timestamp", columnList = "timestamp"),
    @Index(name = "idx_act_username", columnList = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String action; // Uploaded SYS.2, Updated MAN.3, Deleted Generic Template, etc.

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    public void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
