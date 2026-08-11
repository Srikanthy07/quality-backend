package com.qualitywebsite.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchStatDTO {
    private String keyword;
    private long searchCount;
    private double avgResults;
    private LocalDateTime latestSearch;
}
