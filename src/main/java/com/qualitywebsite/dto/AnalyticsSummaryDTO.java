package com.qualitywebsite.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSummaryDTO {
    // Filtered metrics — these follow the selected date range
    private long visitors;
    private long sessions;
    private long pageViews;
    private long downloads;

    // Reference metrics — cumulative, independent of the selected filter
    private long weeklyVisitors;
    private long monthlyVisitors;
    private long overallVisitors;
    private long uniqueVisitors;
    private long returningVisitors;
    private long totalDownloads;

    // Human-readable label for the currently selected period
    private String periodLabel;
}
