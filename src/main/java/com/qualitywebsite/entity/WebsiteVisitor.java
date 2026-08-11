package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "website_visitor", indexes = {
    @Index(name = "idx_wv_visit_time", columnList = "visit_time"),
    @Index(name = "idx_wv_visitor_id", columnList = "visitor_id"),
    @Index(name = "idx_wv_page_url", columnList = "page_url")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebsiteVisitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_id", nullable = false)
    private String visitorId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "page_url", nullable = false)
    private String pageUrl;

    @Column(name = "page_title")
    private String pageTitle;

    @Column(name = "visit_time", nullable = false)
    private LocalDateTime visitTime;

    @Column(name = "last_activity_time")
    private LocalDateTime lastActivityTime;

    @Column(name = "session_start")
    private LocalDateTime sessionStart;

    @Column(name = "session_end")
    private LocalDateTime sessionEnd;

    @Column(name = "page_views")
    @Builder.Default
    private Integer pageViews = 1;

    @Column(name = "browser")
    private String browser;

    @Column(name = "operating_system")
    private String operatingSystem;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "referrer")
    private String referrer;

    @Column(name = "is_returning")
    @Builder.Default
    private Boolean isReturning = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (this.visitTime == null) {
            this.visitTime = LocalDateTime.now();
        }
        if (this.lastActivityTime == null) {
            this.lastActivityTime = this.visitTime;
        }
        if (this.sessionStart == null) {
            this.sessionStart = this.visitTime;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.pageViews == null) {
            this.pageViews = 1;
        }
    }
}
