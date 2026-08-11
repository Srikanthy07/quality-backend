package com.qualitywebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_log", indexes = {
    @Index(name = "idx_sl_search_time", columnList = "search_time"),
    @Index(name = "idx_sl_search_keyword", columnList = "search_keyword")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_id")
    private String visitorId;

    @Column(name = "search_keyword", nullable = false)
    private String searchKeyword;

    @Column(name = "results_count")
    @Builder.Default
    private Integer resultsCount = 0;

    @Column(name = "search_time", nullable = false)
    private LocalDateTime searchTime;

    @PrePersist
    public void onCreate() {
        if (this.searchTime == null) {
            this.searchTime = LocalDateTime.now();
        }
    }
}
