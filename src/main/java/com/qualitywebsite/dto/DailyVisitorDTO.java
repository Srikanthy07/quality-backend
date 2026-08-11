package com.qualitywebsite.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyVisitorDTO {
    private String date;
    private long totalVisits;
    private long uniqueVisitors;
    private long pageViews;
}
