package com.qualitywebsite.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentActivityDTO {
    private LocalDateTime time;
    private String pageUrl;
    private String pageName;
    private String browser;
    private String deviceType;
    private String visitorType; // "New" or "Returning"
    private String sessionId;
    private String visitorId;
}
