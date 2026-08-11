package com.qualitywebsite.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageStatDTO {
    private String pageName;
    private String pageUrl;
    private long totalVisits;
    private double percentage;
    private LocalDateTime lastVisit;
}
